package com.example.mobile_p2pfl.ai.testing.inicial

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.TRAINER_LOG_TAG
import com.example.mobile_p2pfl.common.getMappedModel
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.file.StandardOpenOption
import java.util.TreeMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.max
import kotlin.math.min


class Trainer(
    private val context: Context,
    private var numThreads: Int = 2,
    device: Device = Device.NNAPI// temporal, cambiar a cpu
) : TrainingInterface {

    //*****************VARIABLES*********************//
    private val samples: MutableList<TrainingSample> = mutableListOf()

    private var executor: ExecutorService? = null
    private var interpreter: Interpreter? = null

    private var targetWidth: Int = 0
    private var targetHeight: Int = 0

    // Lock for thread-safe operations.
    private val lock = Any()

    // Delegate for GPU acceleration.
    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }


    //*****************SETUP*********************//
    init {
        if (initModelInterpreter()) {
            targetWidth = interpreter!!.getInputTensor(0).shape()[2]
            targetHeight = interpreter!!.getInputTensor(0).shape()[1]
        } else {
            Log.e(TRAINER_LOG_TAG, "TFLite failed to init.")
        }
    }

    // Initialize the TFLite interpreter.
    private fun initModelInterpreter(): Boolean {
        val options = Interpreter.Options()
        options.numThreads = numThreads

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
        }*/

        //delegate?.let { options.delegates.add(it) }

        return try {
            //val modelFile = FileUtil.loadMappedFile(context, MODEL_FILE_NAME)


            val modelFile = getMappedModel(context)
            interpreter = Interpreter(modelFile, options)



            true
        } catch (e: IOException) {
            Log.e(TRAINER_LOG_TAG, "TFLite failed to load model with error: " + e.message)
            false
        }


    }


    //*************MAIN FUNCTIONS****************//

    // Add a training sample to the list.
    override fun addTrainingSample(image: Bitmap, number: Int) {
        synchronized(lock) {
            if (interpreter == null) {
                initModelInterpreter()
            }

            val imageFeatures = extractImageFeature(image)
            val label = FloatArray(10) { 0f }
            label[number] = 1f

            //samples.add(TrainingSample(imageFeatures, number)) // TODO es label, pero label tiene que ser el floatarray

        }
    }

    // Start the training process.
    override fun startTraining() {
        if (interpreter == null) {
            initModelInterpreter()
        }

        // New thread for training process.
        executor = Executors.newSingleThreadExecutor()

        val trainBatchSize = min(max(1, samples.size), EXPECTED_BATCH_SIZE)

        if (samples.size < trainBatchSize) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    trainBatchSize,
                    samples.size
                )
            )
        }

        executor?.execute {
            synchronized(lock) {
                var avgLoss: Float

                // Keep training until the helper pause or close.
                while (executor?.isShutdown == false) {
                    var totalLoss = 0f
                    var numBatchesProcessed = 0

                    // Shuffle training samples to reduce overfitting and variance.
                    samples.shuffle()

                    trainingBatchesIterator(trainBatchSize).forEach { trainingSamples ->
                        val trainingBatchBottlenecks =
                            MutableList(trainBatchSize) { FloatArray(VALUES_OUTPUTS_SIZE) }
                        val trainingBatchLabels =
                            MutableList(trainBatchSize) { FloatArray(VALUES_OUTPUTS_SIZE) }

                        // Copy a training sample list into two different input training lists.
                        trainingSamples.forEachIndexed { i, sample ->
                            trainingBatchBottlenecks[i] = sample.bottleneck
                            trainingBatchLabels[i] = sample.label
                        }
                        val loss: FloatArray =
                            training(trainingBatchBottlenecks, trainingBatchLabels)


                        for (i in loss.indices) {
                            totalLoss += loss[i]
                        }
                        numBatchesProcessed++
                    }

                    // Calculate the average loss after training all batches.
                    avgLoss = totalLoss / numBatchesProcessed
                    Log.d(TRAINER_LOG_TAG, "Average loss: $avgLoss")


                }
            }
        }
    }

    // Pause the training process.
    override fun pauseTraining() {
        executor?.shutdownNow()
    }

    // Close the TFLite interpreter.
    override fun closeTrainer() {
        executor?.shutdownNow()
        interpreter?.close()
        executor = null
        interpreter = null
        if (delegate is Closeable) {
            delegate.close()
        }
    }


    //*****************UTILS*******************//
    // Train the model.
    private fun training(
        bottlenecks: MutableList<FloatArray>,
        labels: MutableList<FloatArray>
    ): FloatArray {
        val inputShape = interpreter?.getInputTensor(0)?.shape()
        val expectedInputSize = inputShape?.fold(1, Int::times)?.times(4) // 4 bytes por float
        val inputBuffer = ByteBuffer.allocateDirect(expectedInputSize!!).apply {
            order(ByteOrder.nativeOrder())
        }

        // Copiar los bottlenecks y labels a los buffers
        for (i in bottlenecks.indices) {
            for (value in bottlenecks[i]) {
                inputBuffer.putFloat(value)
            }
            for (value in labels[i]) {
                inputBuffer.putFloat(value)
            }
        }

        // Rellenar el resto del buffer con ceros
        while (inputBuffer.position() < expectedInputSize) {
            inputBuffer.putFloat(0f)
        }
        inputBuffer.rewind()

        // Obtener las dimensiones de salida del modelo
        val outputShape = interpreter?.getOutputTensor(0)?.shape()
        val expectedOutputSize = outputShape!!.fold(1, Int::times) * 4 // 4 bytes por float

        // Crear ByteBuffer para la salida
        val outputBuffer = ByteBuffer.allocateDirect(expectedOutputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Ejecutar el modelo
        interpreter?.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()

        // Convertir el ByteBuffer de salida a FloatArray
        val outputArray = FloatArray(expectedOutputSize / 4)
        outputBuffer.asFloatBuffer().get(outputArray)

        return outputArray
    }

    fun getSamplesSize(): Int {
        return samples.size
    }

    fun saveModelWeights() {
        val tensorsSize = interpreter!!.outputTensorCount

        val outputFile = File(context.filesDir, "checkpoint.bin")
        val fos = FileOutputStream(outputFile)

        for (i in 0 until tensorsSize) {
            val tensor = interpreter!!.getOutputTensor(i)
            val buffer = tensor.asReadOnlyBuffer()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            fos.write(bytes)
        }

        // Cerramos el archivo
        fos.close()
    }

    private fun loadModelWeights() {
        val inputFil = File(context.filesDir, "checkpoint.bin")
        if (!inputFil.exists()) return

        val fileInputStream = FileInputStream(inputFil)
        val fileChannel = fileInputStream.channel
        val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())

        val weights = ByteArray(mappedByteBuffer.limit())
        mappedByteBuffer.get(weights)


        // Cargar los pesos en el interpreter
        interpreter!!.run {
            allocateTensors()

            var offset = 0
            for (i in 0 until outputTensorCount) {
                val tensor = getOutputTensor(i)
                val tensorBuffer = tensor.asReadOnlyBuffer()
                val bytes = ByteArray(tensorBuffer.remaining())
                tensorBuffer.get(bytes)


    // Extract the bottleneck features from the given image.
    private fun extractImageFeature(image: Bitmap): FloatArray {
        val inputBuffer = convertBitmapToByteBuffer(image)
        val outputBuffer = Array(1) { FloatArray(VALUES_OUTPUTS_SIZE) }

        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("TFLite", "Error running model", e)
        }

        return outputBuffer[0]
    }

    // Convert the given image to a ByteBuffer.
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val imagePixels = IntArray(targetHeight * targetWidth)
        val imageBuffer: ByteBuffer =
            ByteBuffer.allocateDirect(4 * targetHeight * targetWidth).apply {
                order(ByteOrder.nativeOrder())
            }

        bitmap.getPixels(imagePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in 0 until targetWidth * targetHeight) {
            val pixel: Int = imagePixels[i]
            imageBuffer.putFloat(convertPixel222(pixel))
        }

        imageBuffer.rewind()
        return imageBuffer
    }

    // Convert to grayscale
    private fun convertPixel222(color: Int): Float {
        return (255 - ((color shr 16 and 0xFF) * 0.299f
                + (color shr 8 and 0xFF) * 0.587f
                + (color and 0xFF) * 0.114f)) / 255.0f
    }

    // Iterator for training sample batches.
    private fun trainingBatchesIterator(trainBatchSize: Int): Iterator<List<TrainingSample>> {

        return object : Iterator<List<TrainingSample>> {

            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < samples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= samples.size) {
                    // To keep batch size consistent, last batch may include some elements from the
                    // next-to-last batch.
                    samples.subList(
                        samples.size - trainBatchSize,
                        samples.size
                    )
                } else {
                    samples.subList(fromIndex, toIndex)
                }
            }
        }
    }




    //*****************CONSTANTS*******************//
    companion object {
        private const val FLOAT_BYTES = 4
        private const val VALUES_OUTPUTS_SIZE = 10
        private const val EXPECTED_BATCH_SIZE = 20
    }
}