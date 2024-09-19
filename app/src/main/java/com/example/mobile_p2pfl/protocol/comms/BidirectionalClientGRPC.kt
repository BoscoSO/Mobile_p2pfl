package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.proto.Node
import com.example.mobile_p2pfl.protocol.proto.NodeServiceGrpc
import com.google.protobuf.ByteString
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

class BidirectionalClientGRPC(val context: Context) {

    companion object {
        //        private const val HOST: String = "172.30.231.18"
        private const val HOST: String = "192.168.1.128"
        private const val PORT: Int = 50051
        private const val MAX_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB
    }

    private var channel: ManagedChannel? = null
    private var blockingStub: NodeServiceGrpc.NodeServiceBlockingStub? = null
    private var asyncStub: NodeServiceGrpc.NodeServiceStub? = null

    private var sessionId: String? = null
    private val clientId = UUID.randomUUID().toString()
    private val isRunning = AtomicBoolean(false)
    private var bidirectionalStream: StreamObserver<Node.Message>? = null

    init {
        channel = ManagedChannelBuilder.forAddress(HOST, PORT).apply {
//                if (uri.scheme == "https") {
//                    useTransportSecurity()
//                } else
            usePlaintext()
            maxInboundMessageSize(MAX_MESSAGE_SIZE)
            executor(Dispatchers.IO.asExecutor())
        }.build()

        blockingStub = NodeServiceGrpc.newBlockingStub(channel)
        asyncStub = NodeServiceGrpc.newStub(channel);
    }

    suspend fun handshake() {
        val request = Node.HandshakeRequest.newBuilder()
            .setClientId(clientId)
            .setVersion("1.0")
            .build()

        try {
            val response = blockingStub?.handshake(request)
            sessionId = response?.sessionId
            Log.d(GRPC_LOG_TAG, "Handshake successful. Session ID: $sessionId")
            startBidirectionalStream()
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Handshake failed: ${e.message}")
        }
    }

    fun disconnect() {
        sessionId?.let { sid ->
            val request = Node.DisconnectRequest.newBuilder()
                .setSessionId(sid)
                .build()

            try {
                blockingStub?.disconnect(request)
                println("Disconnected successfully")
                isRunning.set(false)
                bidirectionalStream?.onCompleted()
            } catch (e: Exception) {
                println("Disconnect failed: ${e.message}")
            }
        } ?: println("No active session to disconnect")
    }

    private suspend fun startBidirectionalStream() {
        isRunning.set(true)

        withContext<Any?>(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                bidirectionalStream =
                    asyncStub?.bidirectionalStream(object : StreamObserver<Node.Message> {

                        override fun onNext(message: Node.Message) {
                            when (message.cmd) {
                                "HEARTBEAT" -> handleHeartbeat()
                                "INIT_MODEL_RESPONSE" -> handleInitModel(message.payload,message.isLast)
                                "SET_MODEL_RESPONSE" -> Log.d(
                                    GRPC_LOG_TAG,
                                    "Received message set model response: ${message.getArgs(0)}"
                                )

                                else -> Log.d(GRPC_LOG_TAG, "Received message: ${message.cmd}")
                            }
                        }

                        override fun onError(t: Throwable) {
                            Log.e(GRPC_LOG_TAG, "Stream error: ${t.message}")
                            continuation.resumeWithException(t)
                            isRunning.set(false)
                        }

                        override fun onCompleted() {
                            Log.d(GRPC_LOG_TAG, "Stream completed")
                            isRunning.set(false)
                        }
                    })
            }
        }
        // Start sending periodic messages (if needed)
//        CoroutineScope(Dispatchers.IO).launch {
//            while (isRunning.get()) {
//                delay(30000) // Send a message every 5 seconds
//                sendMessage("VOTE", "Some payload")
//            }
//        }
    }


    fun sendMessage(cmd: String, payload: ByteString) {
        val message = Node.Message.newBuilder()
            .setSource(clientId)
            .setCmd(cmd)
            .setPayload(payload) // Convert String to ByteString or byteArray
            .build()
        bidirectionalStream?.onNext(message)
    }

    fun checkConnection(): Boolean {
        if (channel != null) {
            return channel!!.getState(true) == ConnectivityState.READY
        }
        return false
    }

    fun closeClient(): Boolean {
        disconnect()
        return if (channel != null) {
            if (!channel!!.isShutdown)
                channel!!.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            true
        } else {
            false
        }
    }


    fun sendWeights() {
        if(!isRunning.get()){
            Log.e(GRPC_LOG_TAG, "Not connected")
            return
        }
        val file = File(context.filesDir, CHECKPOINT_FILE_NAME)
        val weightsData = file.readBytes()

        try {
            val chunkSize = 1024 * 1024 // 1 MB chunks
            var offset = 0
            while (offset < weightsData.size) {
                val end = minOf(offset + chunkSize, weightsData.size)
                val chunk = weightsData.slice(offset until end).toByteArray()
                val isLast = end == weightsData.size
                val weightChunk = Node.Message.newBuilder()
                    .setSource(clientId)
                    .setCmd("SET_MODEL")
                    .setPayload(ByteString.copyFrom(chunk))
                    .setIsLast(isLast)
                    .build()
                bidirectionalStream?.onNext(weightChunk)
                offset = end
            }

        } catch (e: Exception) {
            bidirectionalStream?.onError(e)
            throw e
        }

    }

    fun initModel() {
        if(!isRunning.get()){
            Log.e(GRPC_LOG_TAG, "Not connected")
            return
        }
        val request = Node.Message.newBuilder()
            .setSource(clientId)
            .setCmd("INIT_MODEL")
            .setPayload(ByteString.copyFrom("INIT_MODEL".toByteArray()))
            .build()
        bidirectionalStream?.onNext(request)


    }

    private fun handleHeartbeat() {
        Log.d(GRPC_LOG_TAG, "Received heartbeat")
        val heartbeatResponse = Node.Message.newBuilder()
            .setSource(clientId)
            .setCmd("HEARTBEAT_RESPONSE")
            .addArgs("I'm here!")
            .build()
        bidirectionalStream?.onNext(heartbeatResponse)
    }


    // Variables para acumular el modelo
    private var modelOutputStream: FileOutputStream? = null
    private fun handleInitModel(payload: ByteString, isLast: Boolean) {
        try {
            if (modelOutputStream == null) {
                val outFile = File(context.filesDir, MODEL_FILE_NAME)
                modelOutputStream = FileOutputStream(outFile)

                Log.d(GRPC_LOG_TAG, "Model file initialized: ${outFile.absolutePath}")
            }

            val chunk = payload.toByteArray()
            modelOutputStream?.write(chunk)

            if (isLast) {
                modelOutputStream?.flush()
                modelOutputStream?.close()
                modelOutputStream = null

                Log.d(GRPC_LOG_TAG, "Model received and saved successfully.")
            }
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Error receiving or saving model", e)
        }
    }


//    suspend fun getModel(): ByteArray = withContext(Dispatchers.IO) {
//        suspendCancellableCoroutine { continuation ->
//            val modelData = mutableListOf<ByteArray>()
//
//
//            asyncStub!!.getModel(
//                Empty.getDefaultInstance(),
//                object : StreamObserver<Node.ModelChunk> {
//                    override fun onNext(chunk: Node.ModelChunk) {
//                        modelData.add(chunk.chunk.toByteArray())
//                    }
//
//                    override fun onError(t: Throwable) {
//                        continuation.resumeWithException(t)
//                    }
//
//                    override fun onCompleted() {
//                        var outputStream: FileOutputStream? = null
//                        val outFile = File(context.filesDir, MODEL_FILE_NAME)
//
//                        try {
//                            outputStream = FileOutputStream(outFile)
//                            val completeModel = ByteArrayOutputStream().use { outputStr ->
//                                modelData.forEach { chunk ->
//                                    outputStr.write(chunk)
//
//                                    outputStream.write(chunk)
//                                }
//                                outputStr.toByteArray()
//                            }
//                            outputStream.flush()
//
//                            Log.d(
//                                "ModelRetrieval",
//                                "Model saved successfully: ${outFile.absolutePath}"
//                            )
//
//                            continuation.resume(completeModel)
//                        } catch (e: Exception) {
//                            Log.e("ModelRetrieval", "Error retrieving or saving model", e)
//                            continuation.resumeWithException(e)
//                        } finally {
//                            outputStream?.close()
//                        }
//                    }
//                })
//        }
//    }

}