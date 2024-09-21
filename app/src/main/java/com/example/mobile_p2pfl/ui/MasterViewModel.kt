package com.example.mobile_p2pfl.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.common.Device
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.comms.BidirectionalClientGRPC
import com.example.mobile_p2pfl.ui.fragments.connection.ConnectionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MasterViewModel : ViewModel() {

    /*******************************CONNECTION********************************************/

    val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    var grpcClient: BidirectionalClientGRPC =
        BidirectionalClientGRPC() //StreamingClientGRPC //ClientGRPC

    fun initializeConnection(context: Context) {
        grpcClient = BidirectionalClientGRPC(context)
    }

    fun connect(loadingListener: GrpcEventListener) {
        grpcClient.setEventListener(loadingListener)

        _connectionState.value = ConnectionState.CONNECTING
        viewModelScope.launch {
            var isConnected = grpcClient.checkConnection()

            val timeout = 10_000L  // Límite de tiempo 10 segundos
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
                grpcClient.handshake()
            }
        }
    }

    fun sendWeights() {
        viewModelScope.launch {
            try {
                grpcClient.sendWeights()
            } catch (e: Exception) {
                // Manejar errores al enviar pesos
            }
        }
    }

    fun initModel() {
        viewModelScope.launch {
            try {
                grpcClient.initModel()
            } catch (e: Exception) {
                // Manejar errores al recibir el modelo
            }
        }
    }

    fun disconnect() {
        _connectionState.postValue(ConnectionState.DISCONNECTED)
        grpcClient.disconnect()
    }

    /*******************************MODEL********************************************/

    lateinit var modelController: LearningModel

    // whether is training or not
    val _isTraining = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isTraining: LiveData<Boolean> = _isTraining


    fun initializeModelController(context: Context, numThreads: Int, device: Device = Device.CPU) {
        modelController = LearningModel(context, numThreads, device) //, Device.CPU
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