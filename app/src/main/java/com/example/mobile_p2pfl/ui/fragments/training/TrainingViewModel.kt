package com.example.mobile_p2pfl.ui.fragments.training

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.mobile_p2pfl.ai.controller.LearningModel

class TrainingViewModel() : ViewModel() {

    // number of threads
    val _numThreads = MutableLiveData<Int>().apply {
        value=2
    }
    val numThreads: LiveData<Int> = _numThreads


    // list of training samples
    val _trainningSamples = MutableLiveData<List<TrainingSample>>().apply {
        value= emptyList<TrainingSample>()
    }
    val trainningSamples: LiveData<List<TrainingSample>> = _trainningSamples

    val _loadedSamples = MutableLiveData<Int>().apply {
        value=0
    }
    val loadedSamples: LiveData<Int> = _loadedSamples


    // list of old training samples for new interpreters build
    val _oldTrainningSamples = MutableLiveData<List<TrainingSample>>().apply {
        value= emptyList<TrainingSample>()
    }


    // Training sample class
    data class TrainingSample(val image: Bitmap, val number: Int)

}