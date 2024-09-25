package com.example.mobile_p2pfl.ai.testing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.util.Size
import com.example.mobile_p2pfl.ai.LearningModelController
import com.example.mobile_p2pfl.ai.controller.LearningModel.Companion.CLASSES
import com.example.mobile_p2pfl.ai.controller.MnistLoader
import com.example.mobile_p2pfl.ai.testing.inicial.TrainingInterface
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import com.example.mobile_p2pfl.common.getMappedModel
import com.example.mobile_p2pfl.protocol.comms.StreamingClientGRPC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis


class ModelControllerWithSignatures(
    private val context: Context,
    private var numThreads: Int = 2,
    private val onProgressUpdate: (Float, Float, Int, Int) -> Unit = { _, _, _, _ -> },
    device: Device = Device.CPU// temporal, cambiar a cpu
) : LearningModelController {

    private val batchSize: Int = BATCH_SIZE
    private val learningRate: Float = 0.001f
    private val epochs: Int = 4
    private val validationSplit: Float = 0.2f

    private var trainingJob: Job? = null


    //*****************VARIABLES*********************//
    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()

    //    private var executor: ExecutorService? = null
    private var interpreter: Interpreter? = null

//    private var targetWidth: Int = 0
//    private var targetHeight: Int = 0
//
//    // Lock for thread-safe operations.
//    private val lock = Any()

    // Delegate for GPU acceleration.
    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }


    //*****************SETUP*********************//

    fun isModelInitialized(): Boolean {
        return interpreter != null
    }


    init {
        try {
            val options = Interpreter.Options().apply {
                numThreads = numThreads
                setUseXNNPACK(true)
//                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//                    addDelegate(GpuDelegate())
//                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                    val nnApiDelegate = NnApiDelegate()
//                    addDelegate(nnApiDelegate)
//                }
            }

            val modelFile = getMappedModel(context)
            interpreter = Interpreter(modelFile, options)

            restoreModel()
        } catch (e: IOException) {
            Log.e(
                MODEL_LOG_TAG,
                "TFLite failed to load model with error: " + e.stackTraceToString()
            )
        }
    }

    //*****************SAMPLE FUNCTIONS****************//
    override fun addTrainingSample(image: Bitmap, number: Int) {
        val byteBuffer = convertBitmapToByteBuffer(image)
        val sample = TrainingSample(byteBuffer.array(), number)
        trainingSamples.add(sample)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(IMG_SIZE * IMG_SIZE)
        resizedBitmap.getPixels(
            intValues,
            0,
            resizedBitmap.width,
            0,
            0,
            resizedBitmap.width,
            resizedBitmap.height
        )

        intValues.forEach { pixel ->
            val normalizedPixelValue = convertPixelToFloat(pixel)
            byteBuffer.putFloat(normalizedPixelValue)
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    private fun convertPixelToFloat(color: Int): Float {
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        val grayscale = (0.299f * r + 0.587f * g + 0.114f * b)
        return (255f - grayscale) / 255f
    }


    //*****************temp FUNCTIONS****************//
    fun mnistTraining() {// test

        val mnistLoader = MnistLoader()
        var samples = mnistLoader.loadTrainingSamples(context, "training_samples.dat")
        if (samples != null) {
            trainingSamples.addAll(samples.subList(0, 150))
        }

        for (i in 1..9) {
            samples = mnistLoader.loadTrainingSamples(context, "training_samples_$i.dat")
            Log.d(Values.MODEL_LOG_TAG, "Loaded ${samples!!.size} samples")
            trainingSamples.addAll(samples.subList(0, 150))
            Log.d(Values.MODEL_LOG_TAG, "Loaded ${trainingSamples.size} samples")
        }
        Log.d(Values.MODEL_LOG_TAG, "Loaded ${trainingSamples.size} total samples")
        //startTraining2()
    }


    fun savesamples(num: String) { // test

        val mnistLoader = MnistLoader()
        mnistLoader.saveTrainingSamples(context, "training_samples_$num.dat", trainingSamples)
    }

    fun loadsamples(num: String): List<TrainingSample>? { // test

        val mnistLoader = MnistLoader()
        return mnistLoader.loadTrainingSamples(context, "training_samples_$num.dat")
    }
    //***************CLASSIFY FUNCTIONS****************//

    override fun classify(image: Bitmap): Recognition {

        try {
            val inputImageBuffer = convertBitmapToByteBuffer(image)
            val outputProbabilityBuffer =
                TensorBuffer.createFixedSize(
                    intArrayOf(1, CLASSES),
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
            Log.d(MODEL_LOG_TAG, "Output Array: ${outputArray.contentToString()}")
            val maxIdx = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
            val confidence = outputArray[maxIdx]

            return Recognition(label = maxIdx, confidence = confidence, timeCost = timeCost)
        } finally {

        }

    }


    //*************TRAIN FUNCTIONS****************//

    private var isTraining: Boolean = false

    fun startTraining2(grpc: StreamingClientGRPC) {
        CoroutineScope(Dispatchers.IO).launch {
            for (i in 1..15) {
                restoreModel()
                startTraining()
                isTraining = true
                while (isTraining) {
                    delay(200) // Delay for 2 seconds before checking again
                }
                saveModel()

                grpc.sendWeights(context)
            }
        }
    }

    override fun startTraining() {
        require(interpreter != null) { "Interpreter not initialized. Call init() first." }
        require(trainingSamples.isNotEmpty()) { "No training samples available." }

        // trainingSamples contiene 15000 muestras de entrenamiento
        val (validationSet, trainSet) = trainingSamples.shuffled().let {
            (it.subList(
                0,
                (it.size * validationSplit).toInt()
            ) to it.subList((it.size * validationSplit).toInt(), it.size))
        }

        trainingJob = CoroutineScope(Dispatchers.Default).launch {
            for (epoch in 1..epochs) {
                var totalLoss = 0f
                var totalAccuracy = 0f
                var totalValidationLoss = 0f

                // process training set in batches
                trainSet.shuffled().chunked(batchSize).forEach { batch ->
                    val (batchLoss, batchAccuracy) = processBatch(batch)
                    totalLoss += batchLoss
                    totalAccuracy += batchAccuracy
                }
                val avgLoss = totalLoss / (trainSet.size / BATCH_SIZE)
                val avgAccuracy = totalAccuracy / (trainSet.size / BATCH_SIZE)

                // process validation set in batches
                validationSet.shuffled().chunked(batchSize).forEach { batch ->
                    val batchLoss = validateModel(batch)
                    totalValidationLoss += batchLoss
                }
                val avgValidationLoss = totalValidationLoss / (trainSet.size / BATCH_SIZE)


                onProgressUpdate(avgLoss, avgAccuracy, epoch, epochs)
                Log.d(
                    MODEL_LOG_TAG,
                    "Epoch $epoch/$epochs -  Accuracy: $avgAccuracy. Loss: $avgLoss. Validation Loss: $avgValidationLoss"
                )
            }
            isTraining = false
        }
    }


    private suspend fun processBatch(batch: List<TrainingSample>): Pair<Float, Float> =
        withContext(Dispatchers.Default) {
            val inputBuffer =
                ByteBuffer.allocateDirect(BATCH_SIZE * IMG_SIZE * IMG_SIZE * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
            val labelBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            batch.forEach { sample ->

                inputBuffer.put(sample.image.map { it.toByte() }.toByteArray())
                labelBuffer.putInt(sample.label)
            }

            val outputLossBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }
            val outputAccuracyBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }

            val inputs = mapOf(
                "x" to inputBuffer,
                "y" to labelBuffer
            )
            val outputs = mapOf("loss" to outputLossBuffer, "accuracy" to outputAccuracyBuffer)

            interpreter!!.runSignature(inputs, outputs, "train_fixed_batch")

            outputLossBuffer.rewind()
            outputAccuracyBuffer.rewind()
            outputLossBuffer.float to outputAccuracyBuffer.float
        }

    private suspend fun validateModel(validationSet: List<TrainingSample>): Float =
        withContext(Dispatchers.Default) {
            val inputBuffer =
                ByteBuffer.allocateDirect(BATCH_SIZE * IMG_SIZE * IMG_SIZE * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
            val labelBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            validationSet.forEach { sample ->

                inputBuffer.put(sample.image.map { it.toByte() }.toByteArray())
                labelBuffer.putInt(sample.label)
            }

            val outputLossBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }
            val outputAccuracyBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }

            val inputs = mapOf(
                "x" to inputBuffer,
                "y" to labelBuffer
            )
            val outputs = mapOf("loss" to outputLossBuffer, "accuracy" to outputAccuracyBuffer)

            interpreter!!.runSignature(inputs, outputs, "train_fixed_batch")

            outputLossBuffer.rewind()
            outputAccuracyBuffer.rewind()

            outputLossBuffer.float
        }



    override fun pauseTraining() {
        trainingJob?.cancel()
    }

    //*****************SAVE/LOAD*******************//

    // Save the model to the checkpoint
    override fun saveModel() {
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter!!.runSignature(inputs, outputs, "save")
        Log.d(Values.MODEL_LOG_TAG, "Model saved")

    }

    // Restore the model from the checkpoint
    override fun restoreModel() {
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        if (outputFile.exists()) {
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter!!.runSignature(inputs, outputs, "restore")
            Log.d(Values.MODEL_LOG_TAG, "Model loaded")
        } else {
            Log.e(Values.MODEL_LOG_TAG, "cant load model")
        }
    }

    //*****************CLOSE*******************//

    override fun close() {
        interpreter?.close()

        interpreter = null
        if (delegate is Closeable) {
            delegate.close()
        }
    }


    //*****************UTILS*******************//
    override fun getSamplesSize(): Int {
        return trainingSamples.size
    }


    //*****************CONSTANTS*******************//
    companion object {

        const val IMG_SIZE = 28
        const val BATCH_SIZE = 20

    }
}