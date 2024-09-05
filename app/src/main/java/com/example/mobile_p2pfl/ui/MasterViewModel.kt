package com.example.mobile_p2pfl.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.ai.inference.Classifier
import com.example.mobile_p2pfl.ai.training.Trainer
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import kotlinx.coroutines.launch

class MasterViewModel: ViewModel() {

    /*******************************CONNECTION********************************************/

    val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    lateinit var  grpcClient: ClientGRPC

    fun initializeConnection() {
        grpcClient = ClientGRPC()
        _connectionState.value = ConnectionState.CONNECTING
        viewModelScope.launch {
            val isConnected = grpcClient.connectToServer() ?: false
            _connectionState.value = if (isConnected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        grpcClient.disconnect()
    }

    /*******************************TRAINING********************************************/

    val _trainer = MutableLiveData<Trainer>().apply {
        value=null
    }

    // whether is training or not
    val _isTraining = MutableLiveData<Boolean>().apply {
        value=false
    }
    val isTraining: LiveData<Boolean> = _isTraining

    /*******************************INFERENCE********************************************/
    lateinit var classifier: Classifier




    /*************************************************************************************/
    override fun onCleared() {
        super.onCleared()
        disconnect()
        _trainer.value?.closeTrainer()
        _isTraining.value=false
        classifier.close()


    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}