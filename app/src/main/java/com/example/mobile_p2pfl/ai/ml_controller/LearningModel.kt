package com.example.mobile_p2pfl.ai.ml_controller

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.mobile_p2pfl.ai.LearningModelController
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.TRAINER_LOG_TAG
import com.example.mobile_p2pfl.common.getMappedModel
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis


class LearningModel(
    private val context: Context,
    private var numThreads: Int = 4,
    device: Device = Device.CPU
) : LearningModelController {


    //*****************VARIABLES*********************//

    private var interpreter: Interpreter? = null

    private var executor: ExecutorService? = null

    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }


    private val lock = Any()
    private val trainingSamples = mutableListOf<TrainingSample>()


    //*****************SETUP*********************//
    init {
        if (!initModelInterpreter()) {
            Log.e(TRAINER_LOG_TAG, "TFLite failed to init.")
        }else
            restoreModel()
    }

    private fun initModelInterpreter(): Boolean {
        val options = Interpreter.Options()
        options.numThreads = numThreads

        delegate?.let { options.delegates.add(it) }

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

    /***************UTILS********************/
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 28, 28, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * 28 * 28).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(28 * 28)
        resizedBitmap.getPixels(pixels, 0, 28, 0, 0, 28, 28)

        for (pixelValue in pixels) {
            byteBuffer.putFloat(convertPixel(pixelValue))
        }
        return byteBuffer
    }

    private fun convertPixel(color: Int): Float {
        return (255 - ((color shr 16 and 0xFF) * 0.299f
                + (color shr 8 and 0xFF) * 0.587f
                + (color and 0xFF) * 0.114f)) / 255.0f
    }

    /***************OVERRIDE*****************/
    override fun classify(image: Bitmap): Recognition {


        try {
            val inputImageBuffer = preprocessImage(image)
            val outputProbabilityBuffer =
                TensorBuffer.createFixedSize(
                    intArrayOf(1, 10),
                    org.tensorflow.lite.DataType.FLOAT32
                )

            val timeCost = measureTimeMillis {
                val inputs = mapOf(
                    "x" to inputImageBuffer
                )
                val outputs = mutableMapOf<String, Any>(
                    "output" to outputProbabilityBuffer.buffer
                )
                interpreter!!.runSignature(inputs, outputs, "infer")
            }

            val outputArray = outputProbabilityBuffer.floatArray
            val maxIdx = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
            val confidence = outputArray[maxIdx]

            return Recognition(label = maxIdx, confidence = confidence, timeCost = timeCost)
        } finally {

        }
    }

    override fun addTrainingSample(image: Bitmap, number: Int) {
        val inputImageBuffer = preprocessImage(image)
        val trainingSample = TrainingSample(inputImageBuffer, number)
        trainingSamples.add(trainingSample)
    }

    override fun getSamplesSize(): Int {
        return trainingSamples.size
    }

    //TODO de uno en uno
    override fun startTraining() {
        if (interpreter == null) {
            initModelInterpreter()
        }

        if (trainingSamples.isEmpty()) {
            throw IllegalStateException("No training samples available.")
        }

        executor = Executors.newSingleThreadExecutor()

        val trainBatchSize = min(max(1, trainingSamples.size), BATCH_SIZE)

        if (trainingSamples.size < trainBatchSize) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    trainBatchSize,
                    trainingSamples.size
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

                    trainingSamples.shuffle()

                    trainingBatchesIterator(trainBatchSize).forEach { samples ->
                        samples.forEach { sample ->
                            // Crear buffers de entrada y salida para un solo ejemplo
                            val inputImageBuffer = sample.image
                            val labelBuffer = ByteBuffer.allocateDirect(4).apply {
                                order(ByteOrder.nativeOrder())
                                putInt(sample.label)
                            }
                            val outputLossBuffer = ByteBuffer.allocateDirect(4).apply {
                                order(ByteOrder.nativeOrder())
                            }

                            val inputs = mapOf("x" to inputImageBuffer, "y" to labelBuffer)
                            val outputs = mutableMapOf<String, Any>("loss" to outputLossBuffer)

                            val timeCost = measureTimeMillis {
                                interpreter!!.runSignature(inputs, outputs, "train")
                            }

                            outputLossBuffer.rewind()
                            val loss = outputLossBuffer.float
//                            Log.d(
//                                TRAINER_LOG_TAG,
//                                "Training Loss for sample: $loss, Time cost: $timeCost"
//                            )


                            totalLoss += loss
                            numBatchesProcessed++
                        }
                    }
                    avgLoss = totalLoss / numBatchesProcessed
                    Log.d(TRAINER_LOG_TAG, "Average loss: $avgLoss")

                }

            }
        }
    }

    //TODO de batch en batch
    fun startTraining2() {
        if (interpreter == null) {
            initModelInterpreter()
        }

        // New thread for training process.
        executor = Executors.newSingleThreadExecutor()

        val trainBatchSize = min(max(1, trainingSamples.size), BATCH_SIZE)

        if (trainingSamples.size < trainBatchSize) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    trainBatchSize,
                    trainingSamples.size
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
                    trainingSamples.shuffle()

                    trainingBatchesIterator(trainBatchSize).forEach { samples ->

                        val batchSize = samples.size
                        val inputImageBuffer =
                            ByteBuffer.allocateDirect(batchSize * 4 * IMG_SIZE * IMG_SIZE).apply {
                                order(ByteOrder.nativeOrder())
                            }
                        val labelBuffer = ByteBuffer.allocateDirect(batchSize * 4).apply {
                            order(ByteOrder.nativeOrder())
                        }
                        val outputLossBuffer = ByteBuffer.allocateDirect(4).apply {
                            order(ByteOrder.nativeOrder())
                        }


                        // Llenar los buffers con los datos del lote
                        samples.forEach { sample ->
                            inputImageBuffer.put(sample.image)
                            labelBuffer.putInt(sample.label)
                        }

                        // Rebobinar los buffers para que estén listos para ser leídos
                        inputImageBuffer.rewind()
                        labelBuffer.rewind()

                        val inputs = mapOf("x" to inputImageBuffer, "y" to labelBuffer)
                        val outputs = mutableMapOf<String, Any>("loss" to outputLossBuffer)

                        val timeCost = measureTimeMillis {
                            interpreter!!.runSignature(inputs, outputs, "train")
                        }

                        outputLossBuffer.rewind()
                        val loss = outputLossBuffer.float
                        Log.d(
                            TRAINER_LOG_TAG,
                            "Training Loss for batch: $loss, Time cost: $timeCost"
                        )

                        totalLoss += loss
                        numBatchesProcessed++
                    }

                    // Calculate the average loss after training all batches.
                    avgLoss = totalLoss / numBatchesProcessed
                    Log.d(TRAINER_LOG_TAG, "Average loss: $avgLoss")


                }
            }
        }
    }


    private fun saveModel() {
        val outputFile = File(context.filesDir, "checkpoint.ckpt")
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter!!.runSignature(inputs, outputs, "save")
        Log.d(TRAINER_LOG_TAG, "Model saved")

    }

    private fun restoreModel() {
        val outputFile = File(context.filesDir, "checkpoint.ckpt")
         if(outputFile.exists()){
             val inputs: MutableMap<String, Any> = HashMap()
             inputs["checkpoint_path"] = outputFile.absolutePath
             val outputs: Map<String, Any> = HashMap()
             interpreter!!.runSignature(inputs, outputs, "restore")
             Log.d(TRAINER_LOG_TAG, "Model loaded")
         }else{
             Log.e(TRAINER_LOG_TAG, "cant load model")
         }
    }

    override fun pauseTraining() {
        executor?.shutdownNow()
        saveModel()
    }

    override fun close() {
        executor?.shutdownNow()
        interpreter?.close()
        executor = null
        interpreter = null
        if (delegate is Closeable) {
            delegate.close()
        }
    }

    private fun trainingBatchesIterator(trainBatchSize: Int): Iterator<List<TrainingSample>> {

        return object : Iterator<List<TrainingSample>> {

            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < trainingSamples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= trainingSamples.size) {
                    // To keep batch size consistent, last batch may include some elements from the
                    // next-to-last batch.
                    trainingSamples.subList(
                        trainingSamples.size - trainBatchSize,
                        trainingSamples.size
                    )
                } else {
                    trainingSamples.subList(fromIndex, toIndex)
                }
            }
        }
    }


    companion object {
        const val IMG_SIZE = 28
        const val BATCH_SIZE = 20

    }
}