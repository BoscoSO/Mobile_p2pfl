package com.example.mobile_p2pfl.common

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC

import com.example.mobile_p2pfl.ui.fragments.inference.InferenceFragment
import com.example.mobile_p2pfl.ui.fragments.training.TrainingFragment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


object Constants {
    const val MODEL_FILE_NAME: String = "mobile_net.tflite"
    const val CHECKPOINT_FILE_NAME: String = "mobile_net.ckpt"

}
object Values {
    val MODEL_LOG_TAG: String = LearningModel::class.java.simpleName
    val GRPC_LOG_TAG: String = ClientGRPC::class.java.simpleName

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

data class TrainingSample(val image: ByteArray, val label: Int) : Serializable{
    fun toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(image.size)
        buffer.put(image)
        buffer.rewind()
        return buffer
    }
    companion object {
        fun fromByteBuffer(buffer: ByteBuffer, label: Int): TrainingSample {
            buffer.rewind()
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)
            return TrainingSample(byteArray, label)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrainingSample

        if (!image.contentEquals(other.image)) return false
        if (label != other.label) return false

        return true
    }

    override fun hashCode(): Int {
        var result = image.hashCode()
        result = 31 * result + label
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
