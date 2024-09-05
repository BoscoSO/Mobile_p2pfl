package com.example.mobile_p2pfl.common

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.inference.Classifier
import com.example.mobile_p2pfl.ai.training.Trainer
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import com.example.mobile_p2pfl.ui.fragments.inference.InferenceFragment
import com.example.mobile_p2pfl.ui.fragments.training.TrainingFragment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


object Constants {
    const val MODEL_FILE_NAME: String = "mnist.tflite"

}
object Values {
    val TRAINER_LOG_TAG: String = Trainer::class.java.simpleName
    val GRPC_LOG_TAG: String = ClientGRPC::class.java.simpleName
    val INFERENCE_LOG_TAG: String = Classifier::class.java.simpleName

    val INFERENCE_FRAG_LOG_TAG: String = InferenceFragment::class.java.simpleName
    val TRAINER_FRAG_LOG_TAG: String = TrainingFragment::class.java.simpleName
}


enum class Device {
    CPU,
    NNAPI,
    GPU
}
data class Recognition(
    val label: Int,
    val confidence: Float,
    val timeCost: Long
)

data class TrainingSample(val bottleneck: FloatArray, val label: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrainingSample

        if (!bottleneck.contentEquals(other.bottleneck)) return false
        if (!label.contentEquals(other.label)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bottleneck.contentHashCode()
        result = 31 * result + label.contentHashCode()
        return result
    }
}


fun getMappedModel(context: Context): MappedByteBuffer {
    val file = File(context.filesDir, MODEL_FILE_NAME)
    val fileInputStream = FileInputStream(file)
    val fileChannel = fileInputStream.channel
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()).apply {
        fileChannel.close()
        fileInputStream.close()
    }
}
fun saveModelToInternalStorage(context: Context): String? {
    val assetManager = context.assets
    var inputStream: InputStream? = null
    var outputStream: FileOutputStream? = null
    val outFile = File(context.filesDir, MODEL_FILE_NAME)

    try {
        inputStream = assetManager.open(MODEL_FILE_NAME)
        outputStream = FileOutputStream(outFile)

        val buffer = ByteArray(1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }

        outputStream.flush()
    } catch (e: IOException) {
        e.printStackTrace()
        return null
    } finally {
        inputStream?.close()
        outputStream?.close()
        Log.d("MainActivity", "File copied successfully: ${outFile.absolutePath}")
    }

    return outFile.absolutePath
}