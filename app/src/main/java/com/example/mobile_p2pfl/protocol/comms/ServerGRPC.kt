package com.example.mobile_p2pfl.protocol.comms

import android.net.Uri
import android.util.Log
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IServerConnection
import com.example.mobile_p2pfl.protocol.proto.Node.ResponseMessage
import com.example.mobile_p2pfl.protocol.proto.Node.Weights
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc.NodeServicesBlockingStub
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc.NodeServicesStub
import com.google.protobuf.ByteString
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit


class ServerGRPC : IServerConnection {

    companion object {
        private const val HOST: String = "172.30.231.18"
        private const val PORT: Int = 50051
    }

    private var channel: ManagedChannel? = null
    private var blockingStub: NodeServicesBlockingStub? = null
    private var asyncStub: NodeServicesStub? = null

    init {
        channel = ManagedChannelBuilder.forAddress(HOST,PORT).apply {
            usePlaintext()
            executor(Dispatchers.IO.asExecutor())
        }.build()

        blockingStub = NodeServicesGrpc.newBlockingStub(channel)
        asyncStub = NodeServicesGrpc.newStub(channel);
        Log.d(GRPC_LOG_TAG, "Connected")
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

            blockingStub = NodeServicesGrpc.newBlockingStub(channel)
            asyncStub = NodeServicesGrpc.newStub(channel);

            Log.d(GRPC_LOG_TAG, "Connecting to ${uri.host}:${uri.port}")

            var i = 0
            // Espera hasta que el estado sea READY
            while (channel!!.getState(true) != ConnectivityState.READY) {
                Log.d(GRPC_LOG_TAG, "Esperando que la conexión esté lista...")
                delay(100) // Espera 100 ms antes de volver a comprobar
                if (i++ > 40) return@withContext false // Si ha pasado más de 4 segundos, devuelve false
            }
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
                channel!!.shutdown().awaitTermination(5,TimeUnit.SECONDS)
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


    fun sendWeightsSync(weightsData: ByteArray?): ResponseMessage {
        val weights = Weights.newBuilder()
            .setWeights(ByteString.copyFrom(weightsData))
            .build()

        var response: ResponseMessage
        try {
            response = blockingStub!!.sendWeights(weights)
        } catch (e: java.lang.Exception) {
            Log.e(GRPC_LOG_TAG, "Error sending weights: " + e.message)
            response = ResponseMessage.newBuilder()
                .setError("Error: " + e.message)
                .build()
        }

        return response
    }
    fun sendWeightsAsync(
        weightsData: ByteArray?,
        responseObserver: StreamObserver<ResponseMessage?>?
    ) {
        val weights = Weights.newBuilder()
            .setWeights(ByteString.copyFrom(weightsData))
            .build()

        asyncStub!!.sendWeights(weights, responseObserver)
    }






}