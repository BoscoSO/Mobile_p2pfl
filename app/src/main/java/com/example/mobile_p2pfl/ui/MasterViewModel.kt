package com.example.mobile_p2pfl.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.comms.ProxyClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MasterViewModel : ViewModel() {

    /*******************************CONNECTION********************************************/

    val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private var grpcClient: ProxyClient = ProxyClient() // BidirectionalClientGRPC()

    fun initializeConnection(context: Context) {
        grpcClient = ProxyClient(context)
    }

    fun connect(loadingListener: GrpcEventListener) {
        grpcClient.setEventListener(loadingListener)

        _connectionState.value = ConnectionState.CONNECTING
        viewModelScope.launch {
            var isConnected = grpcClient.checkConnection()

            val timeout = 10_000L  // time limit
            val startTime = System.currentTimeMillis()
            while (!isConnected) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Log.e(GRPC_LOG_TAG, "Connection timeout exceeded.")
                    break
                }

                delay(100)  // Wait before checking the connection again
                isConnected = grpcClient.checkConnection()
            }
            _connectionState.value =
                if (isConnected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
            if (isConnected) {
                grpcClient.setLearner(modelController) // testing
            }
        }
    }

    fun sendWeights() {
        viewModelScope.launch {
            try {
//                grpcClient.sendWeights()

                grpcClient.startMainStream()
            } catch (e: Exception) {
                // errors
                Log.e(GRPC_LOG_TAG, "Error sending weights: ${e.message}")
            }
        }
    }

    fun initModel() {
        viewModelScope.launch {
            try {
                grpcClient.initModel()
            } catch (e: Exception) {
                // errors
            }
        }
    }

    fun disconnect() {
        _connectionState.postValue(ConnectionState.DISCONNECTED)
//        grpcClient.disconnect()
    }

    /*******************************MODEL********************************************/

    lateinit var modelController: TensorFlowLearnerController

    // whether is training or not
    val _isTraining = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isTraining: LiveData<Boolean> = _isTraining


    fun initializeModelController(context: Context, numThreads: Int) {
        modelController = TensorFlowLearnerController(context) //, Device.CPU
        modelController.setNumThreads(numThreads)
    }

    fun setNumThreads(numThreads: Int) {
        modelController.setNumThreads(numThreads)
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