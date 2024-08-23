package com.example.mobile_p2pfl.ai.training;

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import com.example.mobile_p2pfl.ai.temp.TfliteModelLoaderInterface
import com.example.mobile_p2pfl.common.Constants.MODEL_FILE_NAME
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode


class TfliteModelLoader(context: Context) :
    TfliteModelLoaderInterface {

    private var assetManager: AssetManager? = context.assets


    override fun loadTfliteModel(): TfLiteModel {
        return TfLiteModel(loadMappedFile());
    }


    @Throws(IOException::class)
    fun loadMappedFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor =
            assetManager!!.openFd(MODEL_FILE_NAME)//a ver

        val inputStream = FileInputStream(fileDescriptor.fileDescriptor);
        val fileChannel = inputStream.channel;
        val startOffset = fileDescriptor.startOffset;
        val declaredLength = fileDescriptor.declaredLength;

        return fileChannel.map(MapMode.READ_ONLY, startOffset, declaredLength);
    }

    fun getMappedModel(context: Context): MappedByteBuffer {
        val file = File(context.filesDir, MODEL_FILE_NAME)
        val fileInputStream = FileInputStream(file)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()).apply {
            fileChannel.close()
            fileInputStream.close()
        }
    }
}
