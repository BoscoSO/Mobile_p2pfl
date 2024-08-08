package com.example.mobile_p2pfl.ai

import android.graphics.Bitmap
import com.example.mobile_p2pfl.common.Recognition

interface InferenceInterface {

    fun classify(image: Bitmap) : Recognition

    fun close()

}