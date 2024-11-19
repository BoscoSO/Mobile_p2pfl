package com.example.mobile_p2pfl.ui.fragments.connection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import com.example.mobile_p2pfl.ui.ConnectionState
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {


    private val _infoStep = MutableLiveData<String>()
    val infoStep: LiveData<String> = _infoStep
    private val _infoInstruction = MutableLiveData<String>()
    val infoInstruction: LiveData<String> = _infoInstruction
    private val _infoResultLbl = MutableLiveData<String>()
    val infoResultLbl: LiveData<String> = _infoResultLbl
    private val _infoLoss = MutableLiveData<Float>()
    val infoLoss: LiveData<Float> = _infoLoss
    private val _infoAccuracy = MutableLiveData<Float>()
    val infoAccuracy: LiveData<Float> = _infoAccuracy
    private val _infoProgress = MutableLiveData<Float>()
    val infoProgress: LiveData<Float> = _infoProgress
    val _waitingInstructions = MutableLiveData<Boolean>().apply {
        value = true
    }
    val waitingInstructions: LiveData<Boolean> = _waitingInstructions


    val _checkIcon = MutableLiveData<Boolean>().apply {
        value = false
    }
    val checkIcon: LiveData<Boolean> = _checkIcon

    val _startFL = MutableLiveData<Boolean>().apply {
        value = false
    }
    val startFL: LiveData<Boolean> = _startFL


    private val _streamError = MutableLiveData<String>()
    val streamError: LiveData<String> = _streamError


    val grpcListener = object : GrpcEventListener {
        override fun startFederatedTraining() {
            _streamError.postValue("")
            _startFL.postValue(true)
            _checkIcon.postValue(false)
            _waitingInstructions.postValue(true)
        }

        override fun startInstruction(instruction: String) {
            _streamError.postValue("")
            _checkIcon.postValue(false)
            _waitingInstructions.postValue(false)
            _infoInstruction.postValue(instruction)
        }

        override fun updateStep(message: String) {
            _infoStep.postValue(message)
        }

        override fun updateResults(
            message: String,
            loss: Float,
            accuracy: Float
        ) {
            _infoResultLbl.postValue(message)
            _infoLoss.postValue(loss)
            _infoAccuracy.postValue(accuracy)
        }

        override fun updateProgress(progress: Float) {
            _infoProgress.postValue(progress)
        }

        override fun endInstruction() {
            _infoInstruction.postValue("none")
            _checkIcon.postValue(true)
            _waitingInstructions.postValue(true)
        }

        override fun endFederatedTraining() {
            _startFL.postValue(false)
        }

        override fun onError(message: String) {
            _streamError.postValue(message)
        }

    }




    val _sendLoading = MutableLiveData<Boolean>().apply {
        value = false
    }
    val sendLoading: LiveData<Boolean> = _sendLoading


    private val _info = MutableLiveData<String?>()
    val info: LiveData<String?> = _info

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error


    fun startLoading() {
        _sendLoading.postValue(true)
    }
    fun stopLoading() {
        _sendLoading.postValue(false)
    }


    fun setError(message: String) {
        _error.postValue(message)
    }
    fun setInfo(message: String) {
        _info.postValue(message)
    }


}