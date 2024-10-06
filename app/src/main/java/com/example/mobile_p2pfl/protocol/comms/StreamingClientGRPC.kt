package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IClientConnection
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException


class StreamingClientGRPC : IClientConnection {

    companion object {
//        private const val HOST: String = "172.30.231.18"
        private const val HOST: String = "192.168.1.128"
        private const val PORT: Int = 50051
        private const val MAX_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB
    }

    private var channel: ManagedChannel? = null
//    private var blockingStub: NodeServicesBlockingStub? = null
//    private var asyncStub: NodeServicesStub? = null

    init {
//        channel = ManagedChannelBuilder.forAddress(HOST, PORT).apply {
//            usePlaintext()
//            executor(Dispatchers.IO.asExecutor())
//        }.build()
//
//        blockingStub = NodeServicesGrpc.newBlockingStub(channel)
//        asyncStub = NodeServicesGrpc.newStub(channel);
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
                maxInboundMessageSize(MAX_MESSAGE_SIZE)
                executor(Dispatchers.IO.asExecutor())
            }.build()
//
//            blockingStub = NodeServicesGrpc.newBlockingStub(channel)
//            asyncStub = NodeServicesGrpc.newStub(channel);

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


    fun checkConnection(): Boolean {
        if (channel != null) {
            return channel!!.getState(true) == ConnectivityState.READY
        }
        return false
    }


    override suspend fun sendWeights(context: Context) {//: Node.ResponseMessage =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                continuation.resumeWithException(Exception("Wrong Proto"))
//
//                val file = File(context.filesDir, CHECKPOINT_FILE_NAME)
//                val weightsData = file.readBytes()
//
//                val responseObserver = object : StreamObserver<Node.ResponseMessage> {
//                    override fun onNext(response: Node.ResponseMessage) {
//                        continuation.resume(response)
//                    }
//
//                    override fun onError(t: Throwable) {
//                        continuation.resumeWithException(t)
//                    }
//
//                    override fun onCompleted() {
//                        // No necesitamos hacer nada aquí ya que resumimos en onNext
//                    }
//                }
//
//                val requestObserver = asyncStub!!.sendWeights(responseObserver)
//
//                try {
//                    val chunkSize = 1024 * 1024 // 1 MB chunks
//                    var offset = 0
//                    while (offset < weightsData.size) {
//                        val end = minOf(offset + chunkSize, weightsData.size)
//                        val chunk = weightsData.slice(offset until end).toByteArray()
//                        val isLast = end == weightsData.size
//                        val weightChunk = Node.WeightChunk.newBuilder()
//                            .setChunk(ByteString.copyFrom(chunk))
//                            .setIsLast(isLast)
//                            .build()
//                        requestObserver.onNext(weightChunk)
//                        offset = end
//                    }
//                    requestObserver.onCompleted()
//                } catch (e: Exception) {
//                    requestObserver.onError(e)
//                    throw e
//                }
            }
        }
    }


    override suspend fun getModel(context: Context): ByteArray = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val modelData = mutableListOf<ByteArray>()

            continuation.resumeWithException(Exception("Wrong Proto"))
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
        }
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