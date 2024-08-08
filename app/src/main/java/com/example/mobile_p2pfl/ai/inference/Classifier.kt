package com.example.mobile_p2pfl.ai.inference

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.example.mobile_p2pfl.ai.InferenceInterface
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.Values.INFERENCE_LOG_TAG
import com.example.mobile_p2pfl.common.Values.TRAINER_LOG_TAG
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class Classifier(
    private val context: Context,
    private var numThreads: Int = 4,
    device: Device = Device.CPU

) : InferenceInterface {


    //*****************VARIABLES*********************//

    private var interpreter: Interpreter? = null

    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }

    private var targetWidth: Int = 0
    private var targetHeight: Int = 0


    private lateinit var outputTensor: Tensor
    private lateinit var imagePixels: IntArray
    private lateinit var imageBuffer: ByteBuffer
    private lateinit var outputBuffer: TensorBuffer



    //*****************SETUP*********************//
    init {
        if (initModelInterpreter()) {
            targetWidth = interpreter!!.getInputTensor(0).shape()[2]
            targetHeight = interpreter!!.getInputTensor(0).shape()[1]

            outputTensor = interpreter!!.getOutputTensor(0)
            imagePixels = IntArray(targetHeight * targetWidth)
            imageBuffer = ByteBuffer.allocateDirect(4 * targetHeight * targetWidth).apply {
                order(ByteOrder.nativeOrder())
            }
            outputBuffer =
                TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

        } else {
            Log.e(TRAINER_LOG_TAG, "TFLite failed to init.")
        }
    }

    // Initialize the TFLite interpreter.
    private fun initModelInterpreter(): Boolean {
        val options = Interpreter.Options()
        options.numThreads = numThreads

        delegate?.let { options.delegates.add(it) }

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

    // Classifies a image
    override fun classify(image: Bitmap): Recognition {
        if (interpreter == null) {
            initModelInterpreter()
        }

        convertBitmapToByteBuffer(image)

        val start = SystemClock.uptimeMillis()
        interpreter?.run(imageBuffer, outputBuffer.buffer.rewind())
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

    // Closes the interpreter
    override fun close() {
        interpreter?.close()
        if (delegate is Closeable) {
            delegate.close()
        }
    }

    //*****************UTILS*******************//
    // Loads the input image into a ByteBuffer.
    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        imageBuffer.rewind()
        bitmap.getPixels(imagePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in 0 until targetWidth * targetHeight) {
            val pixel: Int = imagePixels[i]
            imageBuffer.putFloat(convertPixel(pixel))
        }
    }

    // Convert to grayscale
    private fun convertPixel(color: Int): Float {
        return (255 - ((color shr 16 and 0xFF) * 0.299f
                + (color shr 8 and 0xFF) * 0.587f
                + (color and 0xFF) * 0.114f)) / 255.0f
    }

    // Returns the input shape of the model.
    fun getInputShape(): Size {
        return Size(targetWidth, targetHeight)
    }

}
// Returns the index of the biggest number in the FloatArray
fun FloatArray.argMax(): Int {
    return this.withIndex().maxByOrNull { it.value }?.index
        ?: throw IllegalArgumentException("Cannot find arg max in empty list")
}
