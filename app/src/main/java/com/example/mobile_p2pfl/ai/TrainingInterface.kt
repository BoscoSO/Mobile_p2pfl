package com.example.mobile_p2pfl.ai

import android.graphics.Bitmap
import android.util.Size

interface TrainingInterface {


    fun addTrainingSample(image: Bitmap, number: Int)

    fun startTraining()

    fun pauseTraining()

    fun closeTrainer()

    fun getSamplesSize() : Int
    fun getInputShape() : Size
}