package com.example.mobile_p2pfl.ui.fragments.connection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import com.example.mobile_p2pfl.ui.ConnectionState
import kotlinx.coroutines.launch

class ConnectionViewModel : ViewModel() {


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