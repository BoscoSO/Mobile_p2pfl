package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.mobile_p2pfl.ai.LearningModelController
import com.example.mobile_p2pfl.ai.conversor.SamplesProcessor
import com.example.mobile_p2pfl.ai.model.InterpreterProvider
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import com.example.mobile_p2pfl.common.getMappedModel
import org.tensorflow.lite.DataType
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
import java.nio.IntBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis


class LearningModelTester(
    private val context: Context,
    device: Device = Device.CPU
) : LearningModelController {


    /*****************VARIABLES*********************/

    // LearningModel TFLite interpreter
//    private var interpreter: Interpreter? = null
    private val interpreterProvider = InterpreterProvider(context)

    // Executor for running inference
    private var executor: ExecutorService? = null

    // List of training samples
    private val samplesProcessor = SamplesProcessor()

    fun saveSamples(label: String) {
        samplesProcessor.saveToJson(context, label)

    }

    fun loadSamples(label: Int) {
        samplesProcessor.loadFromJson(context, label)
    }

    // Lock for thread-safety
    private val lock = Any()


    /*****************SETUP*********************/

    // Initialize LearningModel TFLite interpreter.
    init {

        if (!initModelInterpreter()) {
            Log.e(MODEL_LOG_TAG, "TFLite failed to init.")
        } else
            restoreModel()
    }

    // Initialize the TFLite interpreter.
    private fun initModelInterpreter(): Boolean {

        if (interpreterProvider.isModelInitialized())
            return true
        else
            return false

    }

    fun isModelInitialized(): Boolean {
        return interpreterProvider.isModelInitialized()
//        return interpreter != null
    }

    fun setNumThreads(numThreads: Int) {
        Log.d(MODEL_LOG_TAG, "Setting number of threads to $numThreads")
        interpreterProvider.setNumberOfThreads(numThreads)
    }

    /***************OVERRIDE*****************/

    // Classify the input image
    override fun classify(image: Bitmap): Recognition {
        if (!interpreterProvider.isModelInitialized()) {
            throw IllegalStateException("Model is not initialized.")
        }
        synchronized(lock) {
            try {
//                val reshape = intArrayOf(1, IMG_SIZE, IMG_SIZE, 1)// test,
//                interpreter.resizeInput(0, reshape)// test

                val inputImageBuffer = samplesProcessor.getProcessedImage(image)
                val outputProbabilityBuffer =
                    TensorBuffer.createFixedSize(
                        intArrayOf(1, CLASSES),
                        DataType.FLOAT32
                    )
                val outputLogitsBuffer = TensorBuffer.createFixedSize(
                    intArrayOf(1, CLASSES),
                    DataType.FLOAT32
                )

                val timeCost = measureTimeMillis {
                    val inputs = mapOf(
                        "x" to inputImageBuffer.buffer
                    )
                    val outputs = mutableMapOf<String, Any>(
                        "output" to outputProbabilityBuffer.buffer,
                        "logits" to outputLogitsBuffer.buffer
                    )
                    interpreterProvider.getInferInterpreter().runSignature(inputs, outputs, "infer")
                }

                val outputLogits = outputLogitsBuffer.floatArray

                val outputArray = outputProbabilityBuffer.floatArray
                val maxIdx = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
                val confidence = outputArray[maxIdx]


                Log.d("Inference", "Output probabilities: ${outputArray.contentToString()}")
                Log.d("Inference", "Output logits: ${outputLogits.contentToString()}")
                return Recognition(label = maxIdx, confidence = confidence, timeCost = timeCost)
            } finally {

            }
        }
    }

    // Add a training sample to the list.
    override fun addTrainingSample(image: Bitmap, number: Int) {
        samplesProcessor.addSample(image, number)

    }

    // Get the number of training samples.
    override fun getSamplesSize(): Int {
        return samplesProcessor.samplesSize()
    }

    // Start the training process.
    override fun startTraining() {// utiliza buffers
        if (!interpreterProvider.isModelInitialized()) {
            throw IllegalStateException("Model is not initialized.")
        }

        executor = Executors.newSingleThreadExecutor()
        val config = interpreterProvider.getOptimalConfigFor(samplesProcessor.samplesSize())
        //min(max(1, samplesProcessor.samplesSize()), BATCH_SIZE)

        if (samplesProcessor.samplesSize() < config.batchSize) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    config.batchSize,
                    samplesProcessor.samplesSize()
                )
            )
        }

        executor?.execute {
            synchronized(lock) {
                // Keep training until the helper pause or close.
                while (executor?.isShutdown == false) {
                    var totalLoss = 0f
                    var totalAccuracy = 0f
                    var numBatchesProcessed = 0

                    //val allSamples = samplesProcessor.getAugmentedSamples()
                    //val batches = allSamples.chunked(config.batchSize)

                    samplesProcessor.trainingBatchesIterator(config.batchSize).forEach { batch ->
                        val batchSize = batch.size
                        Log.v(MODEL_LOG_TAG, "batch size $batchSize")
                        /*********************INPUTS*********************/
                        val inputImageBuffer =
                            ByteBuffer.allocateDirect(batchSize * IMG_SIZE * IMG_SIZE * FLOAT_SIZE)
                                .apply {
                                    order(ByteOrder.nativeOrder())
                                }
                        val labelBuffer = IntBuffer.allocate(batchSize)

                        batch.forEach { sample ->
                            inputImageBuffer.put(sample.image.buffer)
                            labelBuffer.put(sample.label)
                        }
                        inputImageBuffer.rewind()
                        labelBuffer.rewind()

                        val inputs = mapOf("x" to inputImageBuffer, "y" to labelBuffer)

                        /*********************OUTPUTS*********************/
                        val outputLossBuffer = FloatBuffer.allocate(1)
                        val outputAccuracyBuffer = FloatBuffer.allocate(1)

                        val outputs = mutableMapOf<String, Any>(
                            "loss" to outputLossBuffer,
                            "accuracy" to outputAccuracyBuffer
                        )

                        /**********************TRAIN**********************/
                        val interpreter = interpreterProvider.getTrainerInterpreter(config)
                        val timeCost = measureTimeMillis {
                            interpreter.runSignature(inputs, outputs, config.signature)
                        }

                        val loss = outputLossBuffer.get(0)
                        val accuracy = outputAccuracyBuffer.get(0)


                        totalLoss += loss
                        totalAccuracy += accuracy
                        numBatchesProcessed++
//todo prevent overtraining
                        Log.d(
                            MODEL_LOG_TAG,
                            "Training Loss: $loss, Accuracy: $accuracy, Time cost: $timeCost"
                        )

                        if (shouldStopTraining(loss)) {
                            Log.d(MODEL_LOG_TAG, "Early stopping triggered. Training stopped.")
                            pauseTraining()
                        }

                    }

                    val avgLoss = totalLoss / numBatchesProcessed
                    val avgAccuracy = totalAccuracy / numBatchesProcessed
                    Log.d(MODEL_LOG_TAG, "Average loss: $avgLoss, Average accuracy: $avgAccuracy")
                }
            }
        }
    }

    fun startTraining3() { // utiliza tensores
        if (!interpreterProvider.isModelInitialized()) {
            throw IllegalStateException("Model is not initialized.")
        }

        executor = Executors.newSingleThreadExecutor()
//        val trainBatchSize = min(max(1, samplesProcessor.samplesSize()), BATCH_SIZE)
        val config =
            interpreterProvider.getOptimalConfigFor(samplesProcessor.samplesSize()) //min(max(1, samplesProcessor.samplesSize()), BATCH_SIZE)

        if (samplesProcessor.samplesSize() < config.batchSize) {
            throw RuntimeException(
                "Too few samples to start training: need ${config.batchSize}, got ${samplesProcessor.samplesSize()}"
            )
        }

        executor?.execute {
            synchronized(lock) {
                while (executor?.isShutdown == false) {
                    var totalLoss = 0f
                    var totalAccuracy = 0f
                    var numBatchesProcessed = 0

                    val allSamples = samplesProcessor.getSamples()
                    val batches = allSamples.chunked(config.batchSize)

                    batches.forEach { batch ->
                        val batchSize = batch.size

                        /*********************INPUTS*********************/
                        val inputImageBuffer = TensorBuffer.createFixedSize(
                            intArrayOf(batchSize, IMG_SIZE, IMG_SIZE, 1),
                            DataType.FLOAT32
                        )
                        val labelBuffer = TensorBuffer.createFixedSize(
                            intArrayOf(batchSize),
                            DataType.FLOAT32
                        )

                        batch.forEachIndexed { index, sample ->
                            inputImageBuffer.buffer.position(index * IMG_SIZE * IMG_SIZE * FLOAT_SIZE)
                            inputImageBuffer.buffer.put(sample.image.buffer)

                            labelBuffer.buffer.putInt(sample.label)
                        }
                        inputImageBuffer.buffer.rewind()
                        labelBuffer.buffer.rewind()

                        val inputs = mapOf(
                            "x" to inputImageBuffer.buffer,
                            "y" to labelBuffer.buffer
                        )

                        /*********************OUTPUTS*********************/
                        val outputLossBuffer =
                            TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)
                        val outputAccuracyBuffer =
                            TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)

                        val outputs = mutableMapOf<String, Any>(
                            "loss" to outputLossBuffer.buffer,
                            "accuracy" to outputAccuracyBuffer.buffer
                        )

                        /**********************TRAIN**********************/
                        val interpreter = interpreterProvider.getTrainerInterpreter(config)
                        val timeCost = measureTimeMillis {
                            interpreter.runSignature(inputs, outputs, config.signature)
                        }

                        val loss = outputLossBuffer.floatArray[0]
                        val accuracy = outputAccuracyBuffer.floatArray[0]

                        totalLoss += loss
                        totalAccuracy += accuracy
                        numBatchesProcessed++

                        Log.d(
                            MODEL_LOG_TAG,
                            "Training Loss: $loss, Accuracy: $accuracy, Time cost: $timeCost"
                        )

                        if (shouldStopTraining(loss)) {
                            Log.d(MODEL_LOG_TAG, "Early stopping triggered. Training stopped.")
                            pauseTraining()
                        }
                    }

                    val avgLoss = totalLoss / numBatchesProcessed
                    val avgAccuracy = totalAccuracy / numBatchesProcessed
                    Log.d(MODEL_LOG_TAG, "Average loss: $avgLoss, Average accuracy: $avgAccuracy")
                }
            }
        }
    }

    private val patience: Int = 5
    private val minDelta: Float = 0.001f
    private var bestLoss = Float.MAX_VALUE

    private var patienceCounter = 0

    private fun shouldStopTraining(currentLoss: Float): Boolean {
        if (currentLoss < bestLoss - minDelta) {
            bestLoss = currentLoss
            patienceCounter = 0
        } else {
            patienceCounter++
        }

        return patienceCounter >= patience
    }

    // Pause the training process.
    override fun pauseTraining() {
//        executor?.shutdownNow()
//        executor?.awaitTermination(4, TimeUnit.SECONDS)
        executor?.let { executorService ->
            executorService.shutdownNow()
            try {
                if (!executorService.awaitTermination(8, TimeUnit.SECONDS)) {
                    Log.e(MODEL_LOG_TAG, "ExecutorService did not terminate in the specified time.")
                }
            } catch (e: InterruptedException) {
                Log.e(MODEL_LOG_TAG, "Awaiting termination was interrupted: ${e.message}")
                Thread.currentThread().interrupt()
            }
        }
    }

    // Save the model to the checkpoint
    override fun saveModel() {
        val interpreter = interpreterProvider.getInferInterpreter()
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter.runSignature(inputs, outputs, "save")
        Log.d(MODEL_LOG_TAG, "Model saved")

    }

    // Restore the model from the checkpoint
    override fun restoreModel() {
        val interpreter = interpreterProvider.getInferInterpreter()
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        if (outputFile.exists()) {
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter.runSignature(inputs, outputs, "restore")
            Log.d(MODEL_LOG_TAG, "Model loaded")
        } else {
            Log.e(MODEL_LOG_TAG, "cant load model")
        }
    }

    // Close the TFLite interpreter.
    override fun close() {
        executor?.shutdownNow()
        interpreterProvider.close()
        executor = null
    }


    /*****************CONSTANTS********************/
    companion object {

        const val FLOAT_SIZE = 4
        const val CLASSES = 10
        const val IMG_SIZE = 28

    }
}