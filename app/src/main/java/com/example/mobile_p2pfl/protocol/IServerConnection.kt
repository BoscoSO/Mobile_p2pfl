package com.example.mobile_p2pfl.protocol

import android.net.Uri

interface IServerConnection {

    suspend fun connectToServer(uri: Uri) : Boolean

    fun disconnect() : Boolean

    fun fetchModel() : Boolean

    fun sendModel() : Boolean

 //TODO listeners?
}