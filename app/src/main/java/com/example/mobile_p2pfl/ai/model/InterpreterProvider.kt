package com.example.mobile_p2pfl.ai.model

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Device
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class InterpreterProvider(private val context: Context, device: Device = Device.CPU) {

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
            Log.w("InterpreterProvider", "NNAPI not supported: ${e.message}")
            null
        }
    }
    private fun getGpuDelegate(): Delegate? {
        val compatList = CompatibilityList()
        return if (compatList.isDelegateSupportedOnThisDevice) {
            try {
                GpuDelegate(compatList.bestOptionsForThisDevice)
            } catch (e: Exception) {
                Log.w("InterpreterProvider", "GPU Delegate not supported: ${e.message}")
                null
            }
        } else {
            Log.w("InterpreterProvider", "GPU Delegate not supported on this device")
            null
        }
    }


    enum class Config(val signature: String, val batchSize: Int) {
        XS("train_fixed_batch_xs", 8),
        S("train_fixed_batch_s", 16),
        M("train_fixed_batch_m", 32),
        L("train_fixed_batch_l", 64)
    }

    fun getOptimalConfigFor(samplesSize: Int): Config =
        when {
            samplesSize <= 32 -> Config.XS
            samplesSize <= 64 -> Config.S
            samplesSize <= 128 -> Config.M
            else -> Config.L
        }

    private var lastBatchSize: Int = 1


    private var interpreter: Interpreter? = null
    private var isModelInitialized: Boolean = false
    private var numThreadsOp: Int = 2

    init {
        interpreter = createInterpreter(1)
    }

    fun isModelInitialized(): Boolean {
        return isModelInitialized
    }

    fun setNumberOfThreads(numThreads: Int) {

        numThreadsOp = numThreads
        interpreter = createInterpreter(lastBatchSize)
    }

    fun getInferInterpreter(): Interpreter {
        interpreter = createInterpreter(1)
        return interpreter!!
    }

    fun getTrainerInterpreter(config: Config): Interpreter {

        if (interpreter == null || config.batchSize != lastBatchSize) {
            interpreter?.close()

            interpreter = createInterpreter(config.batchSize)
        }
        return interpreter!!
    }

    private fun createInterpreter(batchSize: Int): Interpreter? {
        val options = Interpreter.Options().apply {
            numThreads = numThreadsOp
            setUseXNNPACK(true)//testing
            delegate?.let { delegates.add(it) }
        }

        try {
            val model = getMappedModel()
            val interpreter = Interpreter(model, options)

            interpreter.resizeInput(0, intArrayOf(batchSize, IMG_SIZE, IMG_SIZE, 1))
            lastBatchSize = batchSize
            isModelInitialized = true
            return interpreter

        } catch (e: Exception) {
            isModelInitialized = false
            Log.e("InterpreterProvider", "Error creating interpreter: ${e.printStackTrace()}")
            return null
        }
    }

    private fun getMappedModel(): MappedByteBuffer {
        val file = File(context.filesDir, MODEL_FILE_NAME)
        val fileInputStream = FileInputStream(file)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()).apply {
            fileChannel.close()
            fileInputStream.close()
        }
    }


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