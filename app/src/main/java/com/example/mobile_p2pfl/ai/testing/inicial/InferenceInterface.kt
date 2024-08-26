package com.example.mobile_p2pfl.ai.testing.inicial

import android.graphics.Bitmap
import com.example.mobile_p2pfl.common.Recognition

interface InferenceInterface {

    fun classify(image: Bitmap) : Recognition

    fun close()

}