package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream


class ModelAutoController(
    private val learner: TensorFlowLearnerController
) {


    /*****************VARIABLES*********************/

    /*****************SETUP*********************/

    init {

    }


    suspend fun validate(): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            try {
                if (!learner.listSavedSamplesAdapter().isEmpty) {
                    val samplesFile = learner.listSavedSamplesAdapter().getItem(0)!!
                    learner.loadSavedSamples(samplesFile)
                }

                // Ejecutar validate() y obtener los resultados
                val acc = learner.validate()

                // Limpiar las muestras y guardar el modelo
                learner.clearAllSamples()


                0.0f to acc
            } catch (e: Exception) {
                0.0f to 0.0f
            }
        }
    }

    suspend fun train(numEpochs: Int):  Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            try {
                if (!learner.listSavedSamplesAdapter().isEmpty) {
                    val samplesFile = learner.listSavedSamplesAdapter().getItem(0)!!
                    learner.loadSavedSamples(samplesFile)
                }

                val res = learner.trainAndWait(numEpochs)

                learner.clearAllSamples()

                res
            } catch (e: Exception) {
                0.0f to 0.0f
            }
        }
    }

    fun getWeightsCkpt(context: Context): ByteString?{
        try {
            val file = File(context.filesDir, CHECKPOINT_FILE_NAME)
            if (!file.exists()) {
                return null
            }

            FileInputStream(file).use { inputStream ->
                return ByteString.readFrom(inputStream)
            }
        } catch (e: Exception) {
            Log.e(MODEL_LOG_TAG, "Error reading checkpoint file: ${e.message}")
            return null
        }
    }

}