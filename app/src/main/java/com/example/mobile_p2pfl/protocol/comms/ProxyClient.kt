package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.TensorFlowLearnerInterface
import com.example.mobile_p2pfl.ai.controller.LearningModel
import com.example.mobile_p2pfl.ai.controller.ModelAutoController
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController
import com.example.mobile_p2pfl.common.Constants.CHECKPOINT_FILE_NAME
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import edge_node.NodeGrpc
import edge_node.NodeOuterClass
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ConnectivityState
import io.grpc.ForwardingClientCall
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class ProxyClient(
    private val context: Context? = null
) {

    companion object {
//                        private const val HOST: String = "172.30.231.18"
        //private const val HOST: String = "127.0.0.1"

        private const val HOST: String = "192.168.1.128"// por wifi
        private const val PORT: Int = 50051
        private const val MAX_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB

        private const val MAX_RETRY_DELAY = 32000L // 32 seconds
    }

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(HOST, PORT)
        .usePlaintext()
        // .maxInboundMessageSize(MAX_MESSAGE_SIZE)
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .executor(Dispatchers.IO.asExecutor())
        .intercept(LoggingInterceptor())
        .build()


    private var asyncStub = NodeGrpc.newStub(channel)

    private val clientId = UUID.randomUUID().toString()
    private var sessionId: String? = null


    /***********************Testing async Proxy_Node********************************************/
//    private val _messageFlow = MutableSharedFlow<Node.Message>()
//    val messageFlow = _messageFlow.asSharedFlow()
//    private val sendChannel = Channel<Node.Message>()

    private var bidirectionalStream: StreamObserver<NodeOuterClass.EdgeMessage>? = null
    private val isRunning = AtomicBoolean(false)
    private var eventListener: GrpcEventListener? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        monitorConnectionState()
    }


    private fun monitorConnectionState() {
        coroutineScope.launch {
            while (isActive) {
                channel.notifyWhenStateChanged(ConnectivityState.READY) {
                    if (channel.getState(false) == ConnectivityState.TRANSIENT_FAILURE) {
                        reconnect()
                    }
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }

    fun startMainStream() {

        mainStream()
    }

    // Start the bidirectional stream with the server
    private fun mainStream() {
        isRunning.set(true)

        bidirectionalStream =
            asyncStub?.mainStream(object : StreamObserver<NodeOuterClass.EdgeMessage> {

                override fun onNext(message: NodeOuterClass.EdgeMessage) {
                    when (message.cmd) {
                        "vote" -> {
                            Log.d(GRPC_LOG_TAG, "Received vote message")
                            handleVoteMessage(message)
                        }

                        "validate" -> {
                            Log.d(GRPC_LOG_TAG, "Received validate message")
                            handleValidateMessage(message)
                        }

                        "train" -> {
                            Log.d(GRPC_LOG_TAG, "Received train message")
                            handleTrainMessage(message)
                        }

                        else -> Log.d(GRPC_LOG_TAG, "Received message: ${message.cmd}")
                    }
                }

                override fun onError(t: Throwable) {
                    isRunning.set(false)
                    Log.e(GRPC_LOG_TAG, "Stream error: ${t.message}")
                    eventListener?.onError("END")
                }

                override fun onCompleted() {
                    Log.d(GRPC_LOG_TAG, "Stream completed")
                    isRunning.set(false)
                }
            })

    }

    /*************************HANDLERS**************************************************/

    //    private val learner : LearningModel = LearningModel(context!!)
    private fun handleVoteMessage(message: NodeOuterClass.EdgeMessage) {
        val candidates = message.messageList

        // Gen vote
        val TRAIN_SET_SIZE = 4
        val samples = minOf(TRAIN_SET_SIZE, candidates.size)
        val nodesVoted = candidates.shuffled().take(samples)
        val weights = List(samples) { i ->
            (Random.nextInt(1001) / (i + 1)).toInt()
        }
        val response = NodeOuterClass.EdgeMessage.newBuilder()
            .setId(message.id)
            .setCmd("vote_response")
            .addAllMessage(nodesVoted) // Añade los nodos votados
            .addAllMessage(weights.map { it.toString() }) // Añade los pesos como strings
            .build()

        bidirectionalStream?.onNext(response)
    }


    private var learnerController: ModelAutoController? = null
    fun setLearner(learner: TensorFlowLearnerController) {
        this.learnerController = ModelAutoController(learner)
    }


    private fun handleValidateMessage(message: NodeOuterClass.EdgeMessage) {
        coroutineScope.launch {
            try {
                val (loss, accuracy) = learnerController?.validate() ?: Pair(0f, 0f)

                val metricsList = listOf("loss", loss.toString(), "accuracy", accuracy.toString())

                Log.d(GRPC_LOG_TAG, "Validation results: loss=$loss, accuracy=$accuracy")

                val response = NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(message.id)
                    .setCmd("validate_response")
                    .addAllMessage(metricsList)
                    .build()

                withContext(Dispatchers.Main) {
                    bidirectionalStream?.onNext(response)
                }
            } catch (e: Exception) {
                // Manejar el error, enviando una respuesta de error
                Log.e(GRPC_LOG_TAG, "Validation failed: ${e.message}")
                val errorResponse = NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(message.id)
                    .setCmd("validate_response")
                    .setMessage(0, "Validation failed: ${e.message}")
                    .build()

                withContext(Dispatchers.Main) {
                    bidirectionalStream?.onNext(errorResponse)
                }
            }
        }
    }

    private fun handleTrainMessage(message: NodeOuterClass.EdgeMessage) {
        coroutineScope.launch {
            try {
                val epochs = message.messageList[0].toInt()
                val (loss, accuracy) = learnerController?.train(epochs) ?: (0.0f to 0.0f)

//                val combinedBytes = ByteBuffer.allocate(8).apply {
//                    putFloat(loss)
//                    putFloat(accuracy)
//                }.array()

                val weights = learnerController?.getWeightsCkpt(context!!)

                Log.d(GRPC_LOG_TAG, "Training completed with loss $loss and accuracy $accuracy")
                val response = NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(message.id)
                    .setCmd("train_response")
                    .setWeights(weights)
                    .build()

                withContext(Dispatchers.Main) {
                    bidirectionalStream?.onNext(response)
                }

            } catch (e: Exception) {
                Log.e(GRPC_LOG_TAG, "Training failed: ${e.message}")
                val errorResponse = NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(message.id)
                    .setCmd("train_response")
                    .setMessage(0, "Training failed: ${e.message}")
                    .build()

                withContext(Dispatchers.Main) {
                    bidirectionalStream?.onNext(errorResponse)
                }
            }
        }
    }

    // Variable para acumular el modelo
    private var modelOutputStream: FileOutputStream? = null

    private fun saveWeights(payload: ByteString) {
        try {
            if (modelOutputStream == null) { //first time
                val outFile = File(context!!.filesDir, CHECKPOINT_FILE_NAME)
                modelOutputStream = FileOutputStream(outFile)
                Log.d(GRPC_LOG_TAG, "Chkpt file initialized: ${outFile.absolutePath}")
            }

            val chunk = payload.toByteArray()
            modelOutputStream?.write(chunk)

            modelOutputStream?.flush()
            modelOutputStream?.close()
            modelOutputStream = null

            this.eventListener?.onLoadingFinished()

            Log.d(GRPC_LOG_TAG, "Model received and saved successfully.")

        } catch (e: Exception) {
            this.eventListener?.onError("Can't save model")
            Log.e(GRPC_LOG_TAG, "Error receiving or saving model", e)
        }
    }


    /***************************************************************************/
    /***************************************************************************/
    /***************************************************************************/
    private fun reconnect() {
        coroutineScope.launch {
            var retryDelay = 1000L
            while (!isRunning.get() && retryDelay <= MAX_RETRY_DELAY) {
                try {
                    delay(retryDelay)
                    mainStream()
                    break
                } catch (e: Exception) {
                    Log.e(GRPC_LOG_TAG, "Reconnection failed: ${e.message}")
                    retryDelay *= 2
                }
            }
        }
    }

    // Close the client channel
    fun closeClient(): Boolean {
        coroutineScope.cancel()
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

    private class LoggingInterceptor : ClientInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(
            method: MethodDescriptor<ReqT, RespT>?,
            callOptions: CallOptions?,
            next: Channel
        ): ClientCall<ReqT, RespT> {
            return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)
            ) {
                override fun sendMessage(message: ReqT) {
                    Log.d(GRPC_LOG_TAG, "Sending message: $message")
                    super.sendMessage(message)
                }
            }
        }
    }

    /*************************SENDERS**************************************************/

// Send a message Set_model to the server
    suspend fun sendWeights() {
        this.eventListener?.onLoadingStarted()

        if (!isRunning.get()) {
            Log.e(GRPC_LOG_TAG, "Not connected")
            this.eventListener?.onError("Not connected")
            return
        }
        val file = File(context!!.filesDir, CHECKPOINT_FILE_NAME)
        if (!file.exists()) {
            Log.e(GRPC_LOG_TAG, "Weights file not found")
            this.eventListener?.onError("Weights file not found")
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
                val weightChunk = NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(0) // id de que?
                    .setCmd("SET_MODEL")
                    .setCmdBytes(ByteString.copyFrom("SET_MODEL".toByteArray()))
                    .setWeights(ByteString.copyFrom(chunk))
                    .setMessage(0, isLast.toString()) // islast en arg[0] o no hace falta?
                    .build()
                bidirectionalStream?.onNext(weightChunk)
                offset = end
                delay(10)
            }

        } catch (e: Exception) {
            bidirectionalStream?.onError(e)
            this.eventListener?.onError("can't send weights")
            throw e
        }

    }

    // Send a message Init_model to the server
    fun initModel() {
        this.eventListener?.onLoadingStarted()

        if (!isRunning.get()) {
            Log.e(GRPC_LOG_TAG, "Not connected")
            this.eventListener?.onError("Not connected")
            return
        }
        val request = NodeOuterClass.EdgeMessage.newBuilder()
            .setCmd("INIT_MODEL")
            .setCmdBytes(ByteString.copyFrom("INIT_MODEL".toByteArray()))
            .build()

        bidirectionalStream?.onNext(request)
    }


}
