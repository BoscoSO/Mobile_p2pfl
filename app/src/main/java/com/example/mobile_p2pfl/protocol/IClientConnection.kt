package com.example.mobile_p2pfl.protocol

import android.content.Context
import com.example.mobile_p2pfl.protocol.proto.Node
import io.grpc.stub.StreamObserver

interface IClientConnection {

    suspend fun connectToServer() : Boolean

    fun disconnect() : Boolean

    suspend fun sendWeights(context: Context): Node.ResponseMessage

    suspend fun getModel(context: Context): ByteArray



}