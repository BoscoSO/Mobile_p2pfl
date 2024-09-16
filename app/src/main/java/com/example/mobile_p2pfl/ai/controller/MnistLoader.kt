package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.ui.fragments.training.TrainingViewModel
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

class MnistLoader {

    /***************SAVE SAMPLES*******************/

    fun saveTrainingSamples(context: Context, fileName: String, newSamples: List<TrainingSample>) {
        val file = File(context.filesDir, fileName)
        val allSamples = mutableListOf<TrainingSample>()

        if (file.exists()) {
            try {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    val existingSamples = ois.readObject() as? List<TrainingSample>
                    if (existingSamples != null) {
                        allSamples.addAll(existingSamples)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        allSamples.addAll(newSamples)

        try {
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(allSamples)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /***************LOAD SAMPLES*******************/

    fun loadTrainingSamples(context: Context, fileName: String): List<TrainingSample>? {
        val file = File(context.filesDir, fileName)

        if (!file.exists()) return null

        return try {
            ObjectInputStream(FileInputStream(file)).use { ois ->
                @Suppress("UNCHECKED_CAST")
                ois.readObject() as? List<TrainingSample>
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}