package com.example.mobile_p2pfl.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.ai.controller.LearningModelTester
import com.example.mobile_p2pfl.ai.testing.ModelControllerWithSignatures
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.protocol.comms.ClientGRPC
import com.example.mobile_p2pfl.protocol.comms.StreamingClientGRPC
import kotlinx.coroutines.launch

class MasterViewModel : ViewModel() {

    /*******************************CONNECTION********************************************/

    val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    lateinit var grpcClient: StreamingClientGRPC //ClientGRPC

    fun initializeConnection() {
        grpcClient = StreamingClientGRPC()
        _connectionState.value = ConnectionState.CONNECTING
        viewModelScope.launch {
            val isConnected = grpcClient.connectToServer()
            _connectionState.value =
                if (isConnected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        grpcClient.disconnect()
    }

    /*******************************MODEL********************************************/

    lateinit var modelController: LearningModelTester //ModelControllerWithSignatures //todo testing training

    // whether is training or not
    val _isTraining = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isTraining: LiveData<Boolean> = _isTraining


    fun initializeModelController(context: Context, numThreads: Int, device: Device = Device.CPU) {
        modelController = LearningModelTester(context) //, Device.CPU
        modelController.setNumThreads(numThreads)
        Log.v("MODEL CONTROLLER", "Model controller initialized numthreads: $numThreads")
    }
    fun setNumThreads(numThreads: Int) {
        modelController.setNumThreads(numThreads)
        Log.v("MODEL CONTROLLER", "Model controller initialized numthreads: $numThreads")
    }


    /*************************************************************************************/
    override fun onCleared() {
        super.onCleared()
        disconnect()
        modelController.close()
        _isTraining.value = false


    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}