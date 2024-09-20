package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.GrpcEventListener
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

class BidirectionalClientGRPC(val context: Context? = null) {

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
        sessionId=null
        try {
            val response = blockingStub?.handshake(request)
            sessionId = response?.sessionId
            Log.d(GRPC_LOG_TAG, "Handshake successful. Session ID: $sessionId")
            startBidirectionalStream()
        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Handshake failed: ${e.message}")
            eventListener!!.onError("no signal")
        }
    }

    fun disconnect() {
        sessionId?.let { sid ->
            val request = Node.DisconnectRequest.newBuilder()
                .setSessionId(sid)
                .build()

            try {
                blockingStub?.disconnect(request)
                Log.d(GRPC_LOG_TAG, "Disconnect successful")
                isRunning.set(false)
                bidirectionalStream?.onCompleted()
            } catch (e: Exception) {
                Log.e(GRPC_LOG_TAG, "Disconnect failed: ${e.message}")
            }
        } ?: Log.e(GRPC_LOG_TAG, "Session ID is null")
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
                                "INIT_MODEL_RESPONSE" -> handleInitModelResponse(
                                    message.payload,
                                    message.isLast
                                )
                                "SET_MODEL_RESPONSE" -> handleSetModelResponse(message)

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


    private var eventListener: GrpcEventListener? = null
    fun setEventListener(listener: GrpcEventListener) {
        this.eventListener = listener
    }


    fun sendWeights() {
        if (this.eventListener == null){
            Log.e(GRPC_LOG_TAG, "eventListener is null")
            return
        }
        this.eventListener!!.onLoadingStarted()

        if (!isRunning.get()) {
            Log.e(GRPC_LOG_TAG, "Not connected")
            this.eventListener!!.onError("Not connected")
            return
        }
        val file = File(context!!.filesDir, CHECKPOINT_FILE_NAME)
        if (!file.exists()) {
            Log.e(GRPC_LOG_TAG, "Weights file not found")
            this.eventListener!!.onError("Weights file not found")
            return
        }
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
            this.eventListener!!.onError("can't send weights")
            throw e
        }

    }

    fun initModel() {
        if (this.eventListener == null){
            Log.e(GRPC_LOG_TAG, "eventListener is null")
            return
        }
        this.eventListener!!.onLoadingStarted()

        if (!isRunning.get()) {
            Log.e(GRPC_LOG_TAG, "Not connected")
            this.eventListener!!.onError("Not connected")
            return
        }
        val request = Node.Message.newBuilder()
            .setSource(clientId)
            .setCmd("INIT_MODEL")
            .setPayload(ByteString.copyFrom("INIT_MODEL".toByteArray()))
            .build()
        bidirectionalStream?.onNext(request)


    }


    // Variable para acumular el modelo
    private var modelOutputStream: FileOutputStream? = null

    private fun handleInitModelResponse(payload: ByteString, isLast: Boolean) {
        try {
            if (modelOutputStream == null) { //first time
                val outFile = File(context!!.filesDir, MODEL_FILE_NAME)
                modelOutputStream = FileOutputStream(outFile)
                Log.d(GRPC_LOG_TAG, "Model file initialized: ${outFile.absolutePath}")
            }

            val chunk = payload.toByteArray()
            modelOutputStream?.write(chunk)

            if (isLast) { //last time
                modelOutputStream?.flush()
                modelOutputStream?.close()
                modelOutputStream = null


                this.eventListener!!.onLoadingFinished()

                Log.d(GRPC_LOG_TAG, "Model received and saved successfully.")
            }
        } catch (e: Exception) {
            this.eventListener!!.onError("Can't save model")
            Log.e(GRPC_LOG_TAG, "Error receiving or saving model", e)
        }
    }

    private fun handleSetModelResponse(message: Node.Message) {
        if (this.eventListener != null)
            this.eventListener!!.onLoadingFinished()
        else
            Log.e(GRPC_LOG_TAG, "sendWeightsListener is null")

        Log.d(GRPC_LOG_TAG, "Received message set model response: ${message.getArgs(0)}")
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

}