package com.example.mobile_p2pfl.ui.fragments.training

import android.widget.ArrayAdapter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TrainingViewModel: ViewModel() {

    /********************CONFIG BEFORE TRAINING*************************/
    // number of epochs
    val _numEpochs = MutableLiveData<Int>().apply {
        value = 5
    }
    val numEpochs: LiveData<Int> = _numEpochs

    // number of threads
    val _numThreads = MutableLiveData<Int>().apply {
        value = 2
    }
    val numThreads: LiveData<Int> = _numThreads

    // loaded samples
    val _loadedSamples = MutableLiveData<Int>().apply {
        value = 0
    }
    val loadedSamples: LiveData<Int> = _loadedSamples

    /********************CONFIG AFTER TRAINING*************************/

    // save samples to internal storage or not
    val _saveSamples = MutableLiveData<Boolean>().apply {
        value = false
    }
    // clear samples from internal storage or not
    val _clearSamples = MutableLiveData<Boolean>().apply {
        value = false
    }

    /***********************SAVED SAMPLES******************************/

    private val _samplesAdapter = MutableLiveData<ArrayAdapter<String>?>()
    val samplesAdapter: LiveData<ArrayAdapter<String>?> = _samplesAdapter

    fun setSavedSamplesAdapter(adapter: ArrayAdapter<String>) {
        _samplesAdapter.postValue(adapter)
    }


    /**********************LISTENER EVENTS*****************************/

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
    private val _progress = MutableLiveData<Float?>()
    val progress: LiveData<Float?> = _progress

    fun clearProgress() {
        _progress.postValue(0.0f)
    }
    fun udateProgress(loss: Float, accuracy: Float, valAcc: Float, progress: Float) {
        _lastLoss.postValue(loss)
        _lastAccuracy.postValue(accuracy)
        _lastValAccuracy.postValue(valAcc)
        _progress.postValue(progress)
    }


}