package com.example.mobile_p2pfl.ai

import android.graphics.Bitmap
import android.util.Size
import com.example.mobile_p2pfl.common.Recognition
import java.io.Closeable

interface LearningModelController : Closeable {

    fun classify(image: Bitmap): Recognition

    fun addTrainingSample(image: Bitmap, number: Int)

    fun startTraining()

    fun pauseTraining()

    fun getSamplesSize(): Int

    fun saveModel()

    fun restoreModel()
}