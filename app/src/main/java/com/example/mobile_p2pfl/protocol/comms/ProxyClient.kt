package com.example.mobile_p2pfl.protocol.comms

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.controller.ModelAutoController
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController
import com.example.mobile_p2pfl.common.GrpcConnectionListener
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.protocol.IClientConnection
import com.example.mobile_p2pfl.protocol.messages.CommandsHandler
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ProxyClient(private val conectionListener : GrpcConnectionListener): IClientConnection {

    companion object {
//        private const val HOST: String = "172.30.231.18" // Local
        private const val HOST: String = "192.168.1.129" // ipv4 ethernet
        private const val PORT: Int = 50051
        private const val MAX_RETRY_DELAY = 32000L // 32 seconds
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forAddress(HOST, PORT)
        .usePlaintext()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .executor(Dispatchers.IO.asExecutor())
        .intercept(LoggingInterceptor())
        .build()


    private var asyncStub = NodeGrpc.newStub(channel)
    private var bidirectionalStream: StreamObserver<NodeOuterClass.EdgeMessage>? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)

    private var commandsHandler: CommandsHandler? = null


    init {
        monitorConnectionState()
    }

   // Monitor the connection state
    private fun monitorConnectionState() {
        coroutineScope.launch {
            while (isActive) {
                channel.notifyWhenStateChanged(ConnectivityState.READY) {
                    if (channel.getState(false) == ConnectivityState.TRANSIENT_FAILURE) {
                        conectionListener.disconected()
                    }
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }

    override fun connect() {
        channel = ManagedChannelBuilder.forAddress(HOST, PORT)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .executor(Dispatchers.IO.asExecutor())
            .intercept(LoggingInterceptor())
            .build()

        asyncStub = NodeGrpc.newStub(channel)
    }

    // Start the bidirectional stream with the server
    override fun mainStream() {
        isRunning.set(true)
        commandsHandler?.init()

        bidirectionalStream =
            asyncStub?.mainStream(object : StreamObserver<NodeOuterClass.EdgeMessage> {

                override fun onNext(message: NodeOuterClass.EdgeMessage) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val response = commandsHandler?.handleCommands(message)?.await()
                        bidirectionalStream?.onNext(response)
                    }
                }

                override fun onError(t: Throwable) {
                    isRunning.set(false)
                    commandsHandler?.notifyError("Server closed connection")
                }

                override fun onCompleted() {
                    commandsHandler?.notifyEnd()
                    isRunning.set(false)
                }
            })

    }

    // Close the client channel
    override fun closeClient(): Boolean {
        if (!channel.isShutdown)
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        return true
    }

    /*************************Utilities**************************************************/

    // set commands handler
    override fun setCommandsHandler(
        context: Context,
        learnerController: TensorFlowLearnerController,
        eventListener: GrpcEventListener
    ) {
        this.commandsHandler = CommandsHandler(
            context,
            ModelAutoController(learnerController),
            eventListener,
            coroutineScope
        )
    }

    // Check if the client is connected to the server
    override fun checkConnection(): Boolean {
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

}
