package com.example.mobile_p2pfl.ai

import android.graphics.Bitmap

interface TrainingInterface {


    fun addTrainingSample(image: Bitmap, number: Int)

    fun startTraining()

    fun pauseTraining()

    fun closeTrainer()
}