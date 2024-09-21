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
import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BidirectionalClientGRPC(
    private val context: Context? = null
) {

    companion object {
        //        private const val HOST: String = "172.30.231.18"
        private const val HOST: String = "192.168.1.128"// por wifi
        private const val PORT: Int = 50051
        private const val MAX_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB
    }

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(HOST, PORT)
        .usePlaintext()
        .maxInboundMessageSize(MAX_MESSAGE_SIZE)
        .executor(Dispatchers.IO.asExecutor())
        .build()

    //private var blockingStub: NodeServiceGrpc.NodeServiceBlockingStub? = null
    private var asyncStub = NodeServiceGrpc.newStub(channel)
    private val clientId = UUID.randomUUID().toString()
    private var sessionId: String? = null

    /***********************Testing async********************************************/
//    private val _messageFlow = MutableSharedFlow<Node.Message>()
//    val messageFlow = _messageFlow.asSharedFlow()
//    private val sendChannel = Channel<Node.Message>()

    private var bidirectionalStream: StreamObserver<Node.Message>? = null
    private val isRunning = AtomicBoolean(false)
    private var eventListener: GrpcEventListener? = null


//    init {
//        channel = ManagedChannelBuilder.forAddress(HOST, PORT).apply {
//            usePlaintext()
//            maxInboundMessageSize(MAX_MESSAGE_SIZE)
//            executor(Dispatchers.IO.asExecutor())
//        }.build()
//
//        blockingStub = NodeServiceGrpc.newBlockingStub(channel)
//        asyncStub = NodeServiceGrpc.newStub(channel)
//    }


    // Handshake with the server
    suspend fun handshake() = suspendCancellableCoroutine<Unit> { continuation ->
        val request = Node.HandshakeRequest.newBuilder()
            .setClientId(clientId)
            .setVersion("1.0")
            .build()
        sessionId = null


        asyncStub.handshake(request, object : StreamObserver<Node.HandshakeResponse> {
            override fun onNext(response: Node.HandshakeResponse) {
                sessionId = response.sessionId
                Log.d(GRPC_LOG_TAG, "Handshake successful. Session ID: $sessionId")
                startBidirectionalStream()
                continuation.resume(Unit)
            }

            override fun onError(t: Throwable) {
                Log.e(GRPC_LOG_TAG, "Handshake failed: ${t.message}")
                eventListener?.onError("No signal")
                continuation.resumeWithException(t)
            }

            override fun onCompleted() {
                // Not used for unary calls
            }
        })
    }

    // Send a disconnect message to the server
    fun disconnect() {
        sessionId?.let { sid ->
            val request = Node.DisconnectRequest.newBuilder()
                .setSessionId(sid)
                .build()

            asyncStub.disconnect(request, object : StreamObserver<Empty> {
                override fun onNext(response: Empty) {
                    // Not used for unary calls
                }

                override fun onError(t: Throwable) {
                    Log.e(GRPC_LOG_TAG, "Disconnect failed: ${t.message}")
                }

                override fun onCompleted() {
                    Log.d(GRPC_LOG_TAG, "Disconnect successful")
                    isRunning.set(false)
                    bidirectionalStream?.onCompleted()
                }
            })


        } ?: Log.e(GRPC_LOG_TAG, "Session ID is null")
    }

    // Start the bidirectional stream with the server
    private fun startBidirectionalStream() {
        isRunning.set(true)

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
                    isRunning.set(false)
                    Log.e(GRPC_LOG_TAG, "Stream error: ${t.message}")
                    eventListener!!.onError("END")
                    //continuation.resumeWithException(t)
                }

                override fun onCompleted() {
                    Log.d(GRPC_LOG_TAG, "Stream completed")
                    isRunning.set(false)
                }
            })


        // Start sending periodic messages (if needed)
//        CoroutineScope(Dispatchers.IO).launch {
//            while (isRunning.get()) {
//                delay(30000) // Send a message every 5 seconds
//                sendMessage("VOTE", "Some payload")
//            }
//        }
    }

    // Close the client channel
    fun closeClient(): Boolean {
        disconnect()
        if (!channel.isShutdown)
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)

        return true

    }

    /*************************Utilities**************************************************/

    // set listener for grpc events
    fun setEventListener(listener: GrpcEventListener) {
        this.eventListener = listener
    }

    // Check if the client is connected to the server
    fun checkConnection(): Boolean {
        return channel.getState(true) == ConnectivityState.READY
    }


    /*************************SENDERS**************************************************/

    // Send a message Set_model to the server
    fun sendWeights() {
        if (this.eventListener == null) {
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


    // Send a message Init_model to the server
    fun initModel() {
        if (this.eventListener == null) {
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

    /*************************HANDLERS**************************************************/

    // Variable para acumular el modelo
    private var modelOutputStream: FileOutputStream? = null

    // Handle the response of the Init_model message
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

    // Handle the response of the Set_model message
    private fun handleSetModelResponse(message: Node.Message) {
        if (this.eventListener != null)
            this.eventListener!!.onLoadingFinished()
        else
            Log.e(GRPC_LOG_TAG, "sendWeightsListener is null")

        Log.d(GRPC_LOG_TAG, "Received message set model response: ${message.getArgs(0)}")
    }

    // Handle the heartbeat message
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
