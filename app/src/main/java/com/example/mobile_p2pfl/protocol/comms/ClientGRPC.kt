package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IClientConnection
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
import java.io.File
import java.util.concurrent.TimeUnit


class ClientGRPC : IClientConnection {

    companion object {
        private const val HOST: String = "172.30.231.18"
        private const val PORT: Int = 50051
    }

    private var channel: ManagedChannel? = null
    private var blockingStub: NodeServicesBlockingStub? = null
    private var asyncStub: NodeServicesStub? = null

    init {
        channel = ManagedChannelBuilder.forAddress(HOST, PORT).apply {
            usePlaintext()
            executor(Dispatchers.IO.asExecutor())
        }.build()

        blockingStub = NodeServicesGrpc.newBlockingStub(channel)
        asyncStub = NodeServicesGrpc.newStub(channel);
        Log.d(GRPC_LOG_TAG, "Connected")
    }

    override suspend fun connectToServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (channel != null && channel!!.getState(true) == ConnectivityState.READY) {
                true
            }

            channel = ManagedChannelBuilder.forAddress(HOST, PORT).apply {
//                if (uri.scheme == "https") {
//                    useTransportSecurity()
//                } else
                usePlaintext()
                executor(Dispatchers.IO.asExecutor())
            }.build()

            blockingStub = NodeServicesGrpc.newBlockingStub(channel)
            asyncStub = NodeServicesGrpc.newStub(channel);

            Log.d(GRPC_LOG_TAG, "Connecting to ${HOST}:${PORT}")
            Log.d(GRPC_LOG_TAG, "Connected")
            true
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Connection failed: ${e.message}")
            false
        }
    }


    fun checkConnection(): Boolean {
        if (channel != null) {
            return channel!!.getState(true) == ConnectivityState.READY
        }
        return false
    }


    override fun fetchModel(): Boolean {

        //val response  = stub!!.
        if (channel != null) {
            return channel!!.getState(true) == ConnectivityState.READY
        }

        return false
    }

    override fun sendModel(context: Context, responseObserver: StreamObserver<ResponseMessage>) {

        val file = File(context.filesDir, "checkpoint.ckpt")
        val weightsData = file.readBytes()

        val weights = Weights.newBuilder()
            .setWeights(ByteString.copyFrom(weightsData))
            .build()

        asyncStub!!.sendWeights(weights, responseObserver)


    }

    override fun disconnect(): Boolean {
        if (channel != null) {
            if (!channel!!.isShutdown)
                channel!!.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            return true
        } else
            return false

    }

}