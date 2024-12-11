package com.example.mobile_p2pfl.protocol

import android.content.Context
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController
import com.example.mobile_p2pfl.common.GrpcEventListener
import io.grpc.stub.StreamObserver

interface IClientConnection {

    fun connect()

    fun closeClient() : Boolean

    fun mainStream()


    fun setCommandsHandler(
        context: Context,
        learnerController: TensorFlowLearnerController,
        eventListener: GrpcEventListener
    )

    fun checkConnection(): Boolean


}