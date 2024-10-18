package com.example.mobile_p2pfl.ai

import android.graphics.Bitmap
import android.util.Size
import android.widget.ArrayAdapter
import com.example.mobile_p2pfl.common.Recognition
import java.io.Closeable

interface TensorFlowLearnerInterface : Closeable {
    data class ValidationResults(
        val accuracy: Float
    )

    fun classify(image: Bitmap): Recognition

    suspend fun validate(): Float

    fun train(numEpochs: Int)

    fun pauseTraining()


    fun addTrainingSample(image: Bitmap, number: Int)

    fun getSamplesSize(): Int


    fun saveModel()

    fun restoreModel()



    fun saveSamplesToInternalStg(samplesFileName: String)

    fun loadSavedSamples(title: String)

    fun listSavedSamplesAdapter(): ArrayAdapter<String>

    fun clearAllSamples()
}