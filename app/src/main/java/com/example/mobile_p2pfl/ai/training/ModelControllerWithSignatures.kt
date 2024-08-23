package com.example.mobile_p2pfl.ai.training

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.example.mobile_p2pfl.ai.TrainingInterface
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.TRAINER_LOG_TAG
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min


class ModelControllerWithSignatures(
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
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            val nnApiDelegate = NnApiDelegate()
//            options.addDelegate(nnApiDelegate)
//        }
//
//        delegate?.let { options.addDelegate(it) }

        return try {
            val modelFile = FileUtil.loadMappedFile(context, MODEL_FILE_NAME)

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
            //val label = FloatArray(10) { 0f }
            //label[number] = 1f

     //       samples.add(TrainingSample(imageFeatures, number))

        }
    }

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
                        val inputImage = Array(trainBatchSize) { FloatArray(28 * 28) }
                        val inputLabel = IntArray(trainBatchSize)


                        // Copy a training sample list into two different input training lists.
//                        trainingSamples.forEachIndexed { i, sample ->
//                            inputImage[i] = sample.bottleneck
//                            inputLabel[i] = sample.label
//                        }
                        val inputs = mapOf(
                            "x" to inputImage,
                            "y" to inputLabel
                        )
                        val outputs = mutableMapOf<String, Any>()
                        val loss = FloatArray(1)
                        outputs["loss"] = loss

                        interpreter!!.runSignature(inputs, outputs, "train")


                        totalLoss += loss[0]
                        numBatchesProcessed++
                        //Log.d(TRAINER_LOG_TAG, "Epoch, Batch ${numBatchesProcessed}, Loss: ${loss[0]}  | bestlost ${Float.MAX_VALUE}")
                    }

                    // Calculate the average loss after training all batches.
                    avgLoss = totalLoss / numBatchesProcessed
                    Log.d(TRAINER_LOG_TAG, "Average loss: $avgLoss")


                }
            }
        }
    }

//    private fun training2(
//        bottlenecks: MutableList<FloatArray>,
//        labels: MutableList<FloatArray>
//    ): Float {
//
//        val flattenedBottlenecks =
//            trainingBatchBottlenecks.flatMap { it.asIterable() }.toFloatArray()
//        val flattenedLabels = trainingBatchLabels.flatMap { it.asIterable() }.toFloatArray()
//
//        val inputs = mapOf(
//            "x" to flattenedBottlenecks,
//            "y" to flattenedLabels
//        )
//
//        val outputs = mutableMapOf<String, Any>()
//        val loss = FloatArray(1)
//        outputs["loss"] = loss
//
//        interpreter!!.runSignature(inputs, outputs, "train")
//
//        return loss[0]
//    }
//
//    private fun training(
//        bottlenecks: MutableList<FloatArray>,
//        labels: MutableList<FloatArray>
//    ): Float {
//
//        val NUM_EPOCHS = 100
//        val BATCH_SIZE = 100
//        val NUM_TRAININGS = 60000
//        val NUM_BATCHES = NUM_TRAININGS / BATCH_SIZE
//
//        // Run training for a few steps.
//        val losses = FloatArray(NUM_EPOCHS)
//        for (epoch in 0 until NUM_EPOCHS) {
//            for (batchIdx in 0 until NUM_BATCHES) {
//                val inputs: MutableMap<String, Any> =
//                    HashMap()
//                inputs["x"] = trainImageBatches[batchIdx]
//                inputs["y"] = trainLabelBatches[batchIdx]
//
//                val outputs: MutableMap<String, Any> =
//                    HashMap()
//                val loss = FloatBuffer.allocate(1)
//                outputs["loss"] = loss
//
//                interpreter!!.runSignature(inputs, outputs, "train")
//
//                // Record the last loss.
//                if (batchIdx == NUM_BATCHES - 1) losses[epoch] = loss[0]
//            }
//
//        }
//
//        return 0f
//    }

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
    override fun getSamplesSize(): Int {
        return samples.size
    }

    // Returns the input shape of the model.
    override fun getInputShape(): Size {
        return Size(targetWidth, targetHeight)
    }


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
        private const val VALUES_OUTPUTS_SIZE = 10
        private const val EXPECTED_BATCH_SIZE = 20
    }
}