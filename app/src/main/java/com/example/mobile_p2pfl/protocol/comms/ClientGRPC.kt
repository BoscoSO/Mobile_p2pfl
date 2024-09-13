package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IClientConnection
import com.example.mobile_p2pfl.protocol.proto.Node.ResponseMessage
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc.NodeServicesBlockingStub
import com.example.mobile_p2pfl.protocol.proto.NodeServicesGrpc.NodeServicesStub
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    }

    override suspend fun connectToServer(): Boolean = withContext(Dispatchers.IO) {
        val timeout = 10_000L  // Límite de tiempo 10 segundos
        val startTime = System.currentTimeMillis()

        try {
            if (channel != null && channel!!.getState(true) == ConnectivityState.READY) {
                return@withContext true
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

            // Wait for the connection to be established
            while (channel!!.getState(true) != ConnectivityState.READY) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Log.e(GRPC_LOG_TAG, "Connection timeout exceeded.")
                    return@withContext false  // Timeout
                }
                delay(100)  // Wait before checking the connection again
                channel!!.resetConnectBackoff()  // Reset the backoff
            }

            Log.d(GRPC_LOG_TAG, "Connected to ${HOST}:${PORT}")
            true
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Connection failed: ${e.message}")
            false
        }
    }


    // se cambio el estilo del proto
    override suspend fun getModel(context: Context): ByteArray {
        TODO("Not yet implemented")
//        asyncStub!!.getWeights(Empty.getDefaultInstance(),
//            object : StreamObserver<Weights> {
//
//            override fun onNext(value: Weights) {
//                var outputStream: FileOutputStream? = null
//                val outFile = File(context.filesDir, MODEL_FILE_NAME)
//
//                try{
//                    Log.i(GRPC_LOG_TAG, "Async Response: " + value.weights)
//                    val modelData = value.weights.toByteArray()
//
//
//                    outputStream = FileOutputStream(outFile)
//                    outputStream.write(modelData)
//                    outputStream.flush()
//
//                    Log.d("ModelRetrieval", "FetchModel: Model saved successfully: ${outFile.absolutePath}")
//                } catch (e: Exception) {
//                    Log.e("ModelRetrieval", "Error retrieving or saving model", e)
//                } finally {
//                    outputStream?.close()
//                }
//            }
//
//            override fun onError(t: Throwable) {
//                Log.e(GRPC_LOG_TAG, "Error in fetchModel async call: " + t.message)
//            }
//
//            override fun onCompleted() {
//                Log.i(GRPC_LOG_TAG, "Async call fetchModel completed")
//            }
//        })
    }


    // se cambio el estilo del proto
    override suspend fun sendWeights(context: Context): ResponseMessage {
        TODO("Not yet implemented")
//        val file = File(context.filesDir, "checkpoint.ckpt")
//        val weightsData = file.readBytes()
//
//        val weights = Weights.newBuilder()
//            .setWeights(ByteString.copyFrom(weightsData))
//            .build()
//
//        asyncStub!!.sendWeights(weights, object : StreamObserver<ResponseMessage> {
//
//            override fun onNext(value: ResponseMessage) {
//                Log.i(GRPC_LOG_TAG, "Async Response: " + value.error)
//            }
//
//            override fun onError(t: Throwable) {
//                Log.e(GRPC_LOG_TAG, "Error in async call: " + t.message)
//            }
//
//            override fun onCompleted() {
//                Log.i(GRPC_LOG_TAG, "Async call completed")
//            }
//        })


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