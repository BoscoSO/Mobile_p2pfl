package com.example.mobile_p2pfl.ai.training;

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.mobile_p2pfl.ai.inference.argMax
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.Values.INFERENCE_LOG_TAG
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap


class TfLiteModelActions(private val tfModel: TfLiteModel) : Closeable {

    companion object {
        private const val FLOAT_BYTES = 4
        private const val NUM_CLASSES = 10
        private const val VALUES_OUTPUTS_SIZE = 10
    }

    private lateinit var outputTensor: Tensor
    private lateinit var outputBuffer: TensorBuffer


    /***********************INIT**********************************/
    fun initializeParameters(modelParameters: Array<ByteBuffer?>) {
        val inputTensor = tfModel.getInterpreter().getInputTensor(0)
        val inputShape = inputTensor.shape()
        val inputSize = inputShape.reduce { acc, i -> acc * i } * FLOAT_BYTES

        val zero = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
            putFloat(0, 0.0f)
        }

//        val zero = ByteBuffer.allocateDirect(FLOAT_BYTES).apply {
//            order(ByteOrder.nativeOrder())
//            putFloat(0, 0.0f)
//        }

        val outputs = TreeMap<Int, Any?>()
        for (paramIdx in modelParameters.indices) {
            outputs[paramIdx] = modelParameters[paramIdx]
        }

        tfModel.getInterpreter().runForMultipleInputsOutputs(arrayOf(zero), outputs)
        for (buffer in modelParameters) {
            buffer!!.rewind()
        }
        outputTensor = tfModel.getInterpreter().getOutputTensor(0)
        outputBuffer =
            TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

    }

    /***********************BOTTLENECK************************************/
    fun getNumBottleneckFeatures(): Int {
        return tfModel.getInterpreter().getOutputTensor(0).numElements();
    }

    fun getBottleneckShape(): IntArray {
        return tfModel.getInterpreter().getOutputTensor(0).shape();
    }

    fun generateBottleneck(image: ByteBuffer, outBottleneck: ByteBuffer?): ByteBuffer {
        var aux = outBottleneck

        synchronized(this) {
            if (aux == null) {
                aux = ByteBuffer.allocateDirect(getNumBottleneckFeatures() * FLOAT_BYTES)
            }
            tfModel.getInterpreter().run(image, aux)
            image.rewind()
            outBottleneck!!.rewind()

            return outBottleneck;
        }
    }

    fun extractImageFeature(image: Bitmap): FloatArray {
        val inputBuffer = convertBitmapToByteBuffer(image)
        val outputBuffer = Array(1) { FloatArray(VALUES_OUTPUTS_SIZE) }

        try {
            tfModel.getInterpreter().run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("TFLite", "Error running model", e)
        }

        return outputBuffer[0]
    }

    fun getInputShape(): IntArray {
        return tfModel.getInterpreter().getInputTensor(0).shape()
    }
    // Convert the given image to a ByteBuffer.
    fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val targetWidth = getInputShape()[2]
        val targetHeight = getInputShape()[1]

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

    /**********************TRAIN************************************/
    fun calculateGradients(
        bottleneckBatch: ByteBuffer,
        classBatch: ByteBuffer,
        modelParameters: Array<ByteBuffer?>, modelGradients: Array<ByteBuffer?>
    ): Float {
        if (modelParameters.size != modelGradients.size) {
            throw IllegalArgumentException(
                "Parameter array size (${modelParameters.size}) is different from gradient array size (${modelGradients.size})"
            )
        }
        if (tfModel.getInterpreter().outputTensorCount != modelParameters.size + 1) {
            throw IllegalArgumentException(
                "Model expected ${tfModel.getInterpreter().inputTensorCount - 1} parameter tensors, but got ${modelParameters.size}"
            )
        }

        val lossBuffer = ByteBuffer.allocateDirect(FLOAT_BYTES).apply {
            order(ByteOrder.nativeOrder())
        }

        val outputs = TreeMap<Int, Any?>()
        outputs[0] = lossBuffer
        for (outputIndex in 1 until tfModel.getInterpreter().outputTensorCount) {
            outputs[outputIndex] = modelGradients[outputIndex - 1]
        }

        val inputs = arrayOfNulls<Any>(modelParameters.size)
        inputs[0] = bottleneckBatch
        inputs[1] = classBatch
        System.arraycopy(modelParameters, 0, inputs, 0, modelParameters.size)

        tfModel.getInterpreter().runForMultipleInputsOutputs(inputs, outputs)

        bottleneckBatch.rewind()
        classBatch.rewind()
        for (buffer in modelParameters) {
            buffer!!.rewind()
        }
        for (buffer in modelGradients) {
            buffer!!.rewind()
        }

        lossBuffer.rewind()
        return lossBuffer.float
    }

    fun getBatchSize(): Int {
        return tfModel.getInterpreter().getInputTensor(0).shape()[0]
    }

    fun getParameterSizes(): IntArray {
        val parameterSizes = IntArray(tfModel.getInterpreter().inputTensorCount)
        for (inputIndex in 0 until tfModel.getInterpreter().inputTensorCount) {
            parameterSizes[inputIndex] =
                tfModel.getInterpreter().getInputTensor(inputIndex).numElements()
        }
        return parameterSizes
    }

    fun getParameterShapes(): Array<IntArray> {
        val interpreter = tfModel.getInterpreter()
        val parameterShapes = Array(interpreter.inputTensorCount) { IntArray(0) }
        for (inputIndex in 0 until interpreter.inputTensorCount) {
            val inputTensor = interpreter.getInputTensor(inputIndex)
            parameterShapes[inputIndex] = IntArray(inputTensor.numDimensions())
            System.arraycopy(
                inputTensor.shape(),
                0,
                parameterShapes[inputIndex],
                0,
                inputTensor.numDimensions()
            )
        }
        return parameterShapes
    }

    /**********************INFERENCE************************************/

    fun runInference(imageBuffer: ByteBuffer): Recognition {
        val start = SystemClock.uptimeMillis()
        tfModel.getInterpreter().run(imageBuffer, outputBuffer.buffer.rewind())
        val end = SystemClock.uptimeMillis()
        val timeCost = end - start

        val probs = outputBuffer.floatArray
        val top = probs.argMax()
        Log.v(
            INFERENCE_LOG_TAG,
            "classify(): timeCost = $timeCost, top = $top, probs = ${probs.contentToString()}"
        )

        return Recognition(top, probs[top], timeCost)
    }


    /**********************OPTIMIZER************************************/

    fun performStep(
        currentParams: Array<ByteBuffer?>,
        gradients: Array<ByteBuffer?>,
        newParams: Array<ByteBuffer?>
    ) {

        val inputs = arrayOfNulls<Any>(currentParams.size + gradients.size)
        System.arraycopy(currentParams, 0, inputs, 0, currentParams.size)
        System.arraycopy(gradients, 0, inputs, currentParams.size, gradients.size)

        val outputs = TreeMap<Int, Any?>()
        for (paramIdx in newParams.indices) {
            outputs[paramIdx] = newParams[paramIdx]
        }

        tfModel.getInterpreter().runForMultipleInputsOutputs(inputs, outputs)
        for (buffer in currentParams) {
            buffer!!.rewind()
        }
        for (buffer in gradients) {buffer!!.rewind()
        }
        for (buffer in newParams) {
            buffer!!.rewind()
        }
    }

    fun stateElementSizes(): IntArray {
        val numVariables =
            tfModel.getInterpreter().inputTensorCount - tfModel.getInterpreter().outputTensorCount

        val result = IntArray(tfModel.getInterpreter().inputTensorCount - numVariables * 2)
        for (inputIdx in numVariables * 2 until tfModel.getInterpreter().inputTensorCount) {
            result[inputIdx - numVariables * 2] =
                tfModel.getInterpreter().getInputTensor(inputIdx).numElements()
        }

        return result
    }

    /***********************CLOSE**********************************/
    override fun close() {
        tfModel.close()
    }
}