package com.example.mobile_p2pfl.protocol.comms

import android.net.Uri
import android.util.Log
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IServerConnection
import com.example.mobile_p2pfl.protocol.proto.Node
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext

class ServerGRPC : IServerConnection {

    private var channel: ManagedChannel? = null


    override suspend fun connectToServer(uri: Uri): Boolean {

        //val stub: NodesServicesGrpc.MyServiceBlockingStub = NodesServicesGrpc.newBlockingStub(channel)
        //val request: HandShakeRequest = HandShakeRequest.newBuilder().setName("World").build()
        //val response: ResponseMessage = stub.sayHello(request)

        //var a : Node.HandShakeRequest = Node.HandShakeRequest.newBuilder().setAddr("World").build()

        return withContext(Dispatchers.IO) {
            try {
                if (channel != null) {
                    return@withContext channel!!.getState(true) == ConnectivityState.READY
                }
                channel = let {
                    Log.d(GRPC_LOG_TAG, "Connecting to ${uri.host}:${uri.port}")

                    val builder = ManagedChannelBuilder.forAddress(uri.host, uri.port)
                    if (uri.scheme == "https") {
                        builder.useTransportSecurity()
                    } else {
                        builder.usePlaintext()
                    }

                    builder.executor(Dispatchers.IO.asExecutor()).build()
                }

                Log.d(GRPC_LOG_TAG, "Connected")
                return@withContext (channel!!.getState(true) == ConnectivityState.READY)
            } catch (e: Exception) {

                Log.e(GRPC_LOG_TAG, "Connection failed: " + e.message.toString())
                return@withContext false
            }

        }
    }

    override fun disconnect(): Boolean {
        if (channel != null) {
            if (!channel!!.isShutdown)
                channel!!.shutdown()
            return true
        } else
            return false

    }

    override fun fetchModel(): Boolean {

        if (channel == null)
            return false
        if (channel!!.getState(true) == ConnectivityState.READY) {

            return true
        }

        return false
    }

    override fun sendModel(): Boolean {
        return false
    }
}