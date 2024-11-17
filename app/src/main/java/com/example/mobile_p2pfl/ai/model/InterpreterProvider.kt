package com.example.mobile_p2pfl.ai.model

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.model.InterpreterProviderInterface.Config
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class InterpreterProvider(private val context: Context, device: Device = Device.CPU) :
    InterpreterProviderInterface {

    /********************************DELEGATE*************************************************/
    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> getNnApiDelegate()
        Device.GPU -> getGpuDelegate()
    }

    private fun getNnApiDelegate(): Delegate? {
        return try {
            val options = NnApiDelegate.Options().apply {
                executionPreference = NnApiDelegate.Options.EXECUTION_PREFERENCE_FAST_SINGLE_ANSWER
                allowFp16 = true
                useNnapiCpu = false
                // setAcceleratorName("qti-default")
            }
            NnApiDelegate(options)
        } catch (e: Exception) {
            Log.w(MODEL_LOG_TAG, "InterpreterProvider: NNAPI not supported: ${e.message}")
            null
        }
    }

    private fun getGpuDelegate(): Delegate? {
        val compatList = CompatibilityList()
        return if (compatList.isDelegateSupportedOnThisDevice) {
            try {
                GpuDelegate(compatList.bestOptionsForThisDevice)
            } catch (e: Exception) {
                Log.w(
                    MODEL_LOG_TAG,
                    "InterpreterProvider: GPU Delegate not supported: ${e.message}"
                )
                null
            }
        } else {
            Log.w(MODEL_LOG_TAG, "InterpreterProvider: GPU Delegate not supported on this device")
            null
        }
    }

    /********************************VARIABLES*************************************************/

    private var interpreter: Interpreter? = null
    private var isModelInitialized: Boolean = false
    private var numThreadsOp: Int = 2

    /********************************INIT*****************************************************/

    init {
        interpreter = createInterpreter()
    }

    override fun isModelInitialized(): Boolean {
        return isModelInitialized
    }

    override fun setNumberOfThreads(numThreads: Int): Boolean {
        if (numThreads == numThreadsOp && interpreter != null)
            return false
        numThreadsOp = numThreads
        interpreter = createInterpreter()
        return isModelInitialized
    }

    /************************INTERPRETER MANAGER**********************************************/
    // Return interpreter
    override fun getInterpreter(): Interpreter? {
        if (interpreter == null) {
            interpreter = createInterpreter()
        }
        return interpreter
    }

    // Create interpreter
    private fun createInterpreter(): Interpreter? {
        val options = Interpreter.Options().apply {
            numThreads = numThreadsOp
            setUseXNNPACK(true)//testing
            delegate?.let { delegates.add(it) }
        }
        try {
            val model = getMappedModel()
            val interpreter = Interpreter(model, options)

            isModelInitialized = true
            Log.d(MODEL_LOG_TAG, "InterpreterProvider: Model initialized")
            return interpreter
        } catch (e: Exception) {
            isModelInitialized = false
            Log.e(
                MODEL_LOG_TAG,
                "InterpreterProvider: Error creating interpreter: ${e.printStackTrace()}"
            )
            return null
        }
    }

    // Load model from internal storage
    private fun getMappedModel(): MappedByteBuffer {
        val file = File(context.filesDir, MODEL_FILE_NAME)
        val fileInputStream = FileInputStream(file)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()).apply {
            fileChannel.close()
            fileInputStream.close()
        }
    }

    /*******************************CONFIG****************************************************/



    override fun getOptimalConfigFor(samplesSize: Int): Config =
        when {
            samplesSize <= 32 -> Config.XS
            samplesSize <= 64 -> Config.S
            samplesSize <= 128 -> Config.M
            else -> Config.L
        }

    /********************************OTHER****************************************************/

    fun close() {
        interpreter?.close()
        interpreter = null
        if (delegate is Closeable) {
            delegate.close()
        }
    }

    companion object {
        const val IMG_SIZE = 28
    }
}