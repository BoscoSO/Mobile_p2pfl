package com.example.mobile_p2pfl.common

import android.graphics.BitmapFactory
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import com.example.mobile_p2pfl.ui.fragments.inference.InferenceFragment
import com.example.mobile_p2pfl.ui.fragments.training.TrainingFragment
import org.tensorflow.lite.support.image.TensorImage
import java.io.Serializable
import java.nio.ByteBuffer


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
    fun toTensorImage(): TensorImage {
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
        return TensorImage.fromBitmap(bitmap)
    }

    companion object {
        fun fromByteBuffer(buffer: ByteBuffer, label: Int): TrainingSample {
            buffer.rewind()
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)
            return TrainingSample(byteArray, label)
        }
        fun fromTensorImage(tensorImage: TensorImage, label: Int): TrainingSample {
            return fromByteBuffer(tensorImage.buffer, label)
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



interface LearningModelEventListener {
    fun updateProgress(loss: Float, accuracy: Float, validationAcc: Float)
    fun onLoadingStarted()
    fun onLoadingFinished()
    fun onError(message: String)
}

interface GrpcEventListener {
    fun onLoadingStarted()
    fun onLoadingFinished()
    fun onError(message: String)
}
