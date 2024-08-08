package com.example.mobile_p2pfl.common

import com.example.mobile_p2pfl.ai.inference.Classifier
import com.example.mobile_p2pfl.ai.training.Trainer
import com.example.mobile_p2pfl.protocol.comms.ServerGRPC
import com.example.mobile_p2pfl.ui.fragments.inference.InferenceFragment
import com.example.mobile_p2pfl.ui.fragments.training.TrainingFragment


object Constants {
    const val MODEL_FILE_NAME: String = "mnist.tflite"

}
object Values {
    val TRAINER_LOG_TAG: String = Trainer::class.java.simpleName
    val GRPC_LOG_TAG: String = ServerGRPC::class.java.simpleName
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
