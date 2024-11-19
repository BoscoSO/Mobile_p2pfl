package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import com.example.mobile_p2pfl.ai.LearningModelController
import com.example.mobile_p2pfl.ai.TensorFlowLearnerInterface
import com.example.mobile_p2pfl.ai.conversor.SamplesProcessor
import com.example.mobile_p2pfl.ai.model.InterpreterProvider
import com.example.mobile_p2pfl.ai.model.InterpreterProviderInterface
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.LearningModelEventListener
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.lang.Math.log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.system.measureTimeMillis


class TensorFlowLearnerController(
    private val context: Context
) : TensorFlowLearnerInterface {


    /*****************VARIABLES*********************/

    private val interpreterProvider = InterpreterProvider(context)
    private val samplesProcessor = SamplesProcessor()

    private var eventListener: LearningModelEventListener? = null

    private var executor: ExecutorService? = null
    private val lock = Any()

    /*****************SETUP*********************/

    init {
        if (!isModelInitialized()) {
            eventListener?.onError("TFLite failed to init.")
        } else {
            restoreModel()
        }
    }

    /***************OVERRIDE*****************/
    // Add a training sample to the list.
    override fun addTrainingSample(image: Bitmap, number: Int) {
        samplesProcessor.addSample(image, number)

    }

    // Get the number of training samples.
    override fun getSamplesSize(): Int {
        return samplesProcessor.samplesSize()
    }

    // Classify the input image
    override fun classify(image: Bitmap): Recognition {
        val interpreter = interpreterProvider.getInterpreter()
        if (!interpreterProvider.isModelInitialized()) {
            throw IllegalStateException("Model is not initialized.")
        }
        synchronized(lock) {
            try {
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
                    interpreter!!.runSignature(inputs, outputs, "infer")
                }

                val outputLogits = outputLogitsBuffer.floatArray

                val outputArray = outputProbabilityBuffer.floatArray
                val maxIdx = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
                val confidence = outputArray[maxIdx]


                Log.d(MODEL_LOG_TAG, "Output probabilities: ${outputArray.contentToString()}")
                Log.d(MODEL_LOG_TAG, "Output logits: ${outputLogits.contentToString()}")
                return Recognition(label = maxIdx, confidence = confidence, timeCost = timeCost)
            } finally {

            }
        }
    }


    override suspend fun validate(): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            startTraining(1, onlyValidate = true)
            while (executor?.isShutdown == false) {
                delay(100)
            }

            avgValidationLoss to avgValidationAcc
        }
    }

    suspend fun trainAndWait(
        numEpochs: Int,
        grpcEventListener: GrpcEventListener
    ): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            startTraining(numEpochs)

            while (executor?.isShutdown == false) {
                grpcEventListener.updateResults(
                    "Training Results at epoch $epochPointer",
                    avgLoss,
                    avgAccuracy
                )
                grpcEventListener.updateProgress(progress)
                delay(100)
            }

            avgLoss to avgAccuracy
        }
    }

    override fun train(numEpochs: Int) {
        startTraining(numEpochs)
    }

    private var avgLoss = 0f
    private var avgAccuracy = 0f
    private var avgValidationAcc = 0.0f
    private var avgValidationLoss = 0.0f
    private var progress = 0.0f
    private var epochPointer = 0

    // Start the training process.
    private fun startTraining(numEpochs: Int, onlyValidate: Boolean = false) {
        val interpreter = interpreterProvider.getInterpreter()

        if (!interpreterProvider.isModelInitialized()) {
            throw IllegalStateException("Model is not initialized.")
        }

        executor = Executors.newSingleThreadExecutor()

        val config = interpreterProvider.getOptimalConfigFor(samplesProcessor.samplesSize())

        if (samplesProcessor.samplesSize() < (config.batchSize * 1.2f).toInt()) {
            throw RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    config.batchSize,
                    (samplesProcessor.samplesSize() * 1.2f).toInt()
                )
            )
        }

        eventListener?.onLoadingStarted()

        executor?.execute {
            synchronized(lock) {
                epochPointer = 0
                val totalSamples = (samplesProcessor.samplesSize() * 3 * 0.8).toInt()
                while (executor?.isShutdown == false && epochPointer < numEpochs) {
                    var totalLoss = 0f
                    var totalAccuracy = 0f
                    var totalValidationLoss = 0f
                    var totalValidationAccuracy = 0f

                    var totalSamplesProcessed = 0
                    epochPointer++
                    /******************************TRAINING*********************************/
                    if (!onlyValidate) {
                        var numBatchesProcessed = 0
                        samplesProcessor.trainingBatchesIterator(trainBatchSize = config.batchSize)
                            .forEach { batch ->
                                val (batchLoss, batchAccuracy) = processBatchTensors(
                                    interpreter!!,
                                    config,
                                    batch
                                )
                                totalLoss += batchLoss
                                totalAccuracy += batchAccuracy
                                numBatchesProcessed++
                                totalSamplesProcessed += batch.size

                                progress = calculateProgress(
                                    epochPointer,
                                    numEpochs,
                                    totalSamplesProcessed,
                                    totalSamples
                                )
                            }
                        avgLoss = totalLoss / numBatchesProcessed
                        avgAccuracy = totalAccuracy / numBatchesProcessed
                    }
                    /******************************VALIDATION*********************************/
                    var numSamplesProcessed = 0
                    samplesProcessor.trainingBatchesIterator(validationSet = true)
                        .forEach { batch ->
                            val (batchLoss, batchAccuracy) = validateModel(interpreter!!, batch)
                            totalValidationLoss += batchLoss
                            totalValidationAccuracy += batchAccuracy
                            numSamplesProcessed++
                        }
                    avgValidationAcc = totalValidationAccuracy / numSamplesProcessed
                    avgValidationLoss = totalValidationLoss / numSamplesProcessed


                    if (onlyValidate)
                        Log.d(
                            MODEL_LOG_TAG,
                            "Epoch: $epochPointer/$numEpochs || " +
                                    "Validation Acc $avgValidationAcc/1 || " +
                                    "Validation Loss $avgValidationLoss"
                        )
                    else
                        Log.d(
                            MODEL_LOG_TAG,
                            "Epoch: $epochPointer/$numEpochs || Samples:" +
                                    " Accuracy $avgAccuracy/1  || Loss $avgLoss ||" +
                                    " Validation Acc $avgValidationAcc/1 ||" +
                                    " Validation Loss $avgValidationLoss"
                        )


                    if (shouldStopTraining(avgLoss)) {
                        Log.d(MODEL_LOG_TAG, "Early stopping triggered. Training stopped.")
                        pauseTraining()
                    } else
                        eventListener?.updateProgress(
                            avgLoss,
                            avgAccuracy,
                            avgValidationAcc,
                            progress
                        )

                    if (epochPointer == numEpochs)
                        pauseTraining()

                }
            }
        }
    }


    private fun calculateProgress(
        currentEpoch: Int,
        totalEpochs: Int,
        processedSamples: Int,
        totalSamples: Int
    ): Float {
        val samplesPerEpoch = totalSamples.toFloat()
        val totalSamplesToProcess = samplesPerEpoch * totalEpochs
        val totalProcessedSamples = ((currentEpoch - 1) * samplesPerEpoch) + processedSamples

        return (totalProcessedSamples / totalSamplesToProcess) * 100f
    }

    // Pause the training process.
    override fun pauseTraining() {
        patienceCounter = 0
        bestLoss = Float.MAX_VALUE
        executor?.let { executorService ->
            Thread {
                try {
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        executorService.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    Log.e(MODEL_LOG_TAG, "Shutdown process was interrupted", e)
                    executorService.shutdownNow()
                    Thread.currentThread().interrupt()
                } finally {
                    Handler(Looper.getMainLooper()).post {
                        Log.d(MODEL_LOG_TAG, "Training paused")
                        eventListener?.onLoadingFinished()
                        //saveModel()
                    }
                }
            }.start()
        }
    }

    // Save the model to the checkpoint
    override fun saveModel() {
        val interpreter = interpreterProvider.getInterpreter()
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        val inputs: MutableMap<String, Any> = HashMap()
        inputs["checkpoint_path"] = outputFile.absolutePath
        val outputs: Map<String, Any> = HashMap()
        interpreter!!.runSignature(inputs, outputs, "save")
        Log.d(MODEL_LOG_TAG, "Model saved")
        //restoreModel()
    }

    // Restore the model from the checkpoint
    override fun restoreModel() {
        val interpreter = interpreterProvider.getInterpreter()
        val outputFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
        if (outputFile.exists()) {
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["checkpoint_path"] = outputFile.absolutePath
            val outputs: Map<String, Any> = HashMap()
            interpreter!!.runSignature(inputs, outputs, "restore")
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

    /******************TRAINING********************/
    // Process a batch of training samples.
    private fun processBatchTensors(
        interpreter: Interpreter,
        config: InterpreterProviderInterface.Config,
        batch: List<TrainingSample>
    ): Pair<Float, Float> {
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
            inputImageBuffer.buffer.put(sample.toByteBuffer())
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
        interpreter.runSignature(inputs, outputs, config.signature)

        val loss = outputLossBuffer.floatArray[0]
        val accuracy = outputAccuracyBuffer.floatArray[0]

        return loss to accuracy
    }

    // Validate the model on the validation set.
    private fun validateModel(
        interpreter: Interpreter,
        validationSet: List<TrainingSample>
    ): Pair<Float, Float> {
        var correctPredictions = 0
        var totalSamples = 0
        var totalLoss = 0.0f

        validationSet.forEach { sample ->
            val inputBuffer = ByteBuffer.allocateDirect(IMG_SIZE * IMG_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
                put(sample.image)
                rewind()
            }

            val outputProbabilitiesBuffer = ByteBuffer.allocateDirect(CLASSES * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            val outputLogitsBuffer = ByteBuffer.allocateDirect(CLASSES * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val inputs = mapOf("x" to inputBuffer)
            val outputs = mapOf(
                "output" to outputProbabilitiesBuffer,
                "logits" to outputLogitsBuffer
            )

            interpreter.runSignature(inputs, outputs, "infer")

            outputProbabilitiesBuffer.rewind()
            outputLogitsBuffer.rewind()

            val probabilities = FloatArray(CLASSES)
            val logits = FloatArray(CLASSES)


            outputProbabilitiesBuffer.asFloatBuffer().get(probabilities)
            outputLogitsBuffer.asFloatBuffer().get(logits)

            val predictedClass = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            if (predictedClass == sample.label) {
                correctPredictions++
            }
            val trueLabel = sample.label
            val loss = -ln(probabilities[trueLabel].toDouble()).toFloat()
            totalLoss += loss


            totalSamples++
        }

        val accuracy = correctPredictions.toFloat() / totalSamples
        val averageLoss = totalLoss / totalSamples

        return averageLoss to accuracy
    }


    /****************EARLY STOPPING****************/
    private val patience: Int = 9
    private val minDelta: Float = 0.001f
    private var bestLoss = Float.MAX_VALUE
    private var patienceCounter = 0

    // Check if early stopping should be triggered
    private fun shouldStopTraining(currentLoss: Float): Boolean {
        if (currentLoss < bestLoss - minDelta) {
            bestLoss = currentLoss
            patienceCounter = 0
        } else {
            patienceCounter++
        }

        return patienceCounter >= patience
    }


    /**************SAMPLE MANAGER******************/
    // Save samples to internal storage
    override fun saveSamplesToInternalStg(samplesFileName: String) {
        samplesProcessor.saveSamplesToInternalStorage(context, samplesFileName) // saving

    }

    // Load samples from internal storage
    override fun loadSavedSamples(title: String) {
        samplesProcessor.loadSamplesFromInternalStorage(context, title)
    }

    // List saved samples
    override fun listSavedSamplesAdapter(): ArrayAdapter<String> {
        val adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                samplesProcessor.listSavedSamples(context)
            )

        return adapter
    }

    // Clear all samples from the samples processor
    override fun clearAllSamples() {
        samplesProcessor.clearSamples()
    }

    /*******************OTHER**********************/
    // Set the event listener for the model controller
    fun setEventListener(eventListener: LearningModelEventListener) {
        this.eventListener = eventListener
    }

    // Set the number of threads for the interpreter
    fun setNumThreads(numThreads: Int) {
        if (interpreterProvider.setNumberOfThreads(numThreads))
            restoreModel()
    }

    // Check if the model is initialized
    fun isModelInitialized(): Boolean {
        return interpreterProvider.isModelInitialized()
    }

    /*****************CONSTANTS********************/
    companion object {
        const val FLOAT_SIZE = 4
        const val CLASSES = 10
        const val IMG_SIZE = 28

    }


    /***TESTER************************BORRAR LUEGO***********/
    /**********************************************************************************
    // test sin usar tensores (no hay mejora)
    private fun processBatch2(
    interpreter: Interpreter,
    config: InterpreterProvider.Config,
    batch: List<TrainingSample>
    ): Pair<Float, Float> {
    val batchSize = batch.size

    val inputBuffer =
    ByteBuffer.allocateDirect(batchSize * IMG_SIZE * IMG_SIZE * FLOAT_SIZE)
    .apply {
    order(ByteOrder.nativeOrder())
    }
    val labelBuffer = IntBuffer.allocate(batchSize)

    batch.forEach { sample ->
    inputBuffer.put(sample.toByteBuffer())
    labelBuffer.put(sample.label)
    }

    inputBuffer.rewind()
    labelBuffer.rewind()

    val outputLossBuffer = FloatBuffer.allocate(1)
    val outputAccuracyBuffer = FloatBuffer.allocate(1)

    val inputs = mapOf(
    "x" to inputBuffer,
    "y" to labelBuffer
    )
    val outputs = mapOf("loss" to outputLossBuffer, "accuracy" to outputAccuracyBuffer)

    interpreter.runSignature(inputs, outputs, config.signature)

    val loss = outputLossBuffer.get(0)
    val accuracy = outputAccuracyBuffer.get(0)

    Log.d(MODEL_LOG_TAG, "Training Loss: $loss, Accuracy: $accuracy")

    return loss to accuracy
    }
     ********************************************************************************************/
}