package com.example.mobile_p2pfl.ai.conversor

import android.content.Context
import android.graphics.Bitmap
import com.example.mobile_p2pfl.common.TrainingSample
import org.tensorflow.lite.support.image.TensorImage

interface SamplesProcessorInterface {

    // Preprocess the image and return a TensorImage object
    fun getProcessedImage(bitmap: Bitmap): TensorImage

    /*********************************SAMPLES MANAGER************************************************/
    // Add a new sample to the list
    fun addSample(bitmap: Bitmap, label: Int)

    // Clear the list of samples
    fun clearSamples()

    // Return the number of samples in the list
    fun samplesSize(): Int

    /*********************************SAVE AND LOAD************************************************/
    // Save the list of samples to the internal storage
    fun saveSamplesToInternalStorage(context: Context, title: String)

    // Load the list of samples from the internal storage
    fun loadSamplesFromInternalStorage(context: Context, title: String)

    // List the files in the internal storage directory
    fun listSavedSamples(context: Context): List<String>

    /********************************ITERATOR*************************************************/
    // Return an iterator for the list of samples
    fun trainingBatchesIterator(
        trainBatchSize: Int = 1,
        validationSet: Boolean = false
    ): Iterator<List<TrainingSample>>
}