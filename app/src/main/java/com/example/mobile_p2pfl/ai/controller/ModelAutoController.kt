package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


class ModelAutoController(
    private val learner: TensorFlowLearnerController
) {


    /*****************VARIABLES*********************/

    /*****************SETUP*********************/

    init {

    }


    suspend fun validate(context: Context, messageWeights: ByteString): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            try {
                saveWeights(context, messageWeights)

                if (!learner.listSavedSamplesAdapter().isEmpty) {
                    learner.loadSavedSamples("samples_set.dat")
                }
                learner.restoreModel()

                val res = learner.validate()

                learner.clearAllSamples()

                res
            } catch (e: Exception) {
                0.0f to 0.0f
            }
        }
    }

    suspend fun train(context: Context, messageWeights: ByteString, numEpochs: Int):  Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            try {
                saveWeights(context,messageWeights)

                if (!learner.listSavedSamplesAdapter().isEmpty) {
                    learner.loadSavedSamples("samples_set.dat")
                }
                learner.restoreModel()

                val res = learner.trainAndWait(numEpochs)

                learner.saveModel()
                learner.clearAllSamples()

                res
            } catch (e: Exception) {
                0.0f to 0.0f
            }
        }
    }

    private fun saveWeights(context: Context, payload: ByteString ) {
        var modelOutputStream: FileOutputStream? = null
        try {
            val outFile = File(context.filesDir, CHECKPOINT_FILE_NAME)
            modelOutputStream = FileOutputStream(outFile)
            Log.d(GRPC_LOG_TAG, "Ckpt file initialized: ${outFile.absolutePath}")

            val chunk = payload.toByteArray()
            modelOutputStream.write(chunk)

            modelOutputStream.flush()
            modelOutputStream.close()
            modelOutputStream = null

            Log.d(GRPC_LOG_TAG, "ckpt saved successfully.")
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Error receiving or saving model", e)
        }
    }

    fun getWeightsCkpt(context: Context): ByteString?{
        try {
            val file = File(context.filesDir, CHECKPOINT_FILE_NAME)
            if (!file.exists()) {
                return null
            }

            Log.d(MODEL_LOG_TAG, "Reading checkpoint file: ${file.absolutePath}")
            FileInputStream(file).use { inputStream ->
                return ByteString.readFrom(inputStream)
            }
        } catch (e: Exception) {
            Log.e(MODEL_LOG_TAG, "Error reading checkpoint file: ${e.message}")
            return null
        }
    }

}