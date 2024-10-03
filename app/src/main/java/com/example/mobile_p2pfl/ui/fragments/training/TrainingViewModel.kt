package com.example.mobile_p2pfl.ui.fragments.training

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TrainingViewModel() : ViewModel() {

    // number of threads
    val _numThreads = MutableLiveData<Int>().apply {
        value = 2
    }
    val numThreads: LiveData<Int> = _numThreads


//    // list of training samples
//    val _trainningSamples = MutableLiveData<List<TrainingSample>>().apply {
//        value = emptyList<TrainingSample>()
//    }
//    val trainningSamples: LiveData<List<TrainingSample>> = _trainningSamples

    val _loadedSamples = MutableLiveData<Int>().apply {
        value = 0
    }
    val loadedSamples: LiveData<Int> = _loadedSamples


    // list of old training samples for new interpreters build
//    val _oldTrainningSamples = MutableLiveData<List<TrainingSample>>().apply {
//        value = emptyList<TrainingSample>()
//    }


    // Training sample class
//    data class TrainingSample(val image: Bitmap, val label: Int)


    /*********************************************/

    val _loading = MutableLiveData<Boolean>().apply {
        value = false
    }
    val loading: LiveData<Boolean> = _loading


    private val _info = MutableLiveData<String?>()
    val info: LiveData<String?> = _info

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error


    fun startLoading() {
        _loading.postValue(true)
    }

    fun stopLoading() {
        _loading.postValue(false)
    }

    fun setError(message: String) {
        _error.postValue(message)
    }

    fun setInfo(message: String) {
        _info.postValue(message)
    }

    private val _lastLoss = MutableLiveData<Float?>()
    val lastLoss: LiveData<Float?> = _lastLoss
    private val _lastAccuracy = MutableLiveData<Float?>()
    val lastAccuracy: LiveData<Float?> = _lastAccuracy
    private val _lastValAccuracy = MutableLiveData<Float?>()
    val lastValAccuracy: LiveData<Float?> = _lastValAccuracy

    fun udateProgress(loss: Float, accuracy: Float, valAcc: Float) {
        _lastLoss.postValue(loss)
        _lastAccuracy.postValue(accuracy)
        _lastValAccuracy.postValue(valAcc)
    }


}