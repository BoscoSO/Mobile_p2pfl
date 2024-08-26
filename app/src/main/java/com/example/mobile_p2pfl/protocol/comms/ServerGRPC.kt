package com.example.mobile_p2pfl.protocol.comms

import android.net.Uri
import android.util.Log
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IServerConnection
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


class ServerGRPC : IServerConnection {

    private var channel: ManagedChannel? = null
    private var stub: NodeServicesGrpc.NodeServicesBlockingStub? = null


    init {


    }
    override suspend fun connectToServer(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (channel != null && channel!!.getState(true) == ConnectivityState.READY) {
                return@withContext true
            }

            channel = ManagedChannelBuilder.forAddress(uri.host, uri.port).apply {
                if (uri.scheme == "https") {
                    useTransportSecurity()
                } else {
                    usePlaintext()
                }
                executor(Dispatchers.IO.asExecutor())
            }.build()

            Log.d(GRPC_LOG_TAG, "Connecting to ${uri.host}:${uri.port}")

            var i = 0
            // Espera hasta que el estado sea READY
            while (channel!!.getState(true) != ConnectivityState.READY) {
                Log.d(GRPC_LOG_TAG, "Esperando que la conexión esté lista...")
                delay(100) // Espera 100 ms antes de volver a comprobar
                if(i++>40) return@withContext false // Si ha pasado más de 4 segundos, devuelve false
            }


            stub = NodeServicesGrpc.newBlockingStub(channel)

            Log.d(GRPC_LOG_TAG, "Connected")
            return@withContext true
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Connection failed: ${e.message}")
            return@withContext false
        }
    }


//    fun checkConnection(): Boolean {
//        if (channel != null) {
//            return channel!!.getState(true) == ConnectivityState.READY
//        }
//        return false
//    }

    override fun disconnect(): Boolean {
        if (channel != null) {
            if (!channel!!.isShutdown)
                channel!!.shutdown()
            return true
        } else
            return false

    }


    override fun fetchModel(): Boolean {

        //val response  = stub!!.
        if (channel != null) {
            return channel!!.getState(true) == ConnectivityState.READY
        }

        return false
    }

    override fun sendModel(): Boolean {
        return false
    }
}