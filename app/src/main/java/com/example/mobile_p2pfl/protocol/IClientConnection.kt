package com.example.mobile_p2pfl.protocol

import android.content.Context
import com.example.mobile_p2pfl.protocol.proto.Node
import io.grpc.stub.StreamObserver

interface IClientConnection {

    suspend fun connectToServer() : Boolean

    fun disconnect() : Boolean

    fun fetchModel() : Boolean

    fun sendModel(context : Context, responseObserver: StreamObserver<Node.ResponseMessage>)
}