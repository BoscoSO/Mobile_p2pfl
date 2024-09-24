package com.example.mobile_p2pfl.ai.controller

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.TrainingSample
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

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

    fun resavesamples(context: Context) {
        var samples = loadTrainingSamples(context, "training_samples.dat")
        saveTrainingSamplesJson(context,"all_training_samples_0.json", samples!!)

        for (num in 1..9) {
            samples = loadTrainingSamples(context, "training_samples_$num.dat")
            saveTrainingSamplesJson(context,"all_training_samples$num.json", samples!!)
        }
    }
    private fun saveTrainingSamplesJson(context: Context, title:String ,newSamples: List<TrainingSample>) {
        val file = File(context.filesDir, title)

        try {
            val jsonArray = JSONArray()
            for (sample in newSamples) {
                val jsonObject = JSONObject()
                jsonObject.put("image", sample.image.toString(Charsets.ISO_8859_1))
                jsonObject.put("label", sample.label)
                jsonArray.put(jsonObject)
            }
            Log.v("JSON"," saved complete")
            file.writeText(jsonArray.toString())
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