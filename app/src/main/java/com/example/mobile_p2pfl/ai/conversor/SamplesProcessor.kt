package com.example.mobile_p2pfl.ai.conversor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController.Companion.IMG_SIZE
import com.example.mobile_p2pfl.common.TrainingSample
import com.example.mobile_p2pfl.common.Values.MODEL_LOG_TAG
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class SamplesProcessor:SamplesProcessorInterface {

    private val samples = mutableListOf<TrainingSample>()

    /*********************************PROCESS IMAGE************************************************/
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeWithCropOrPadOp(IMG_SIZE, IMG_SIZE))
        .add(TransformToGrayscaleOp())
        .add(NormalizeOp(0f, 255f))
        .build()

    // Preprocess the image and return a TensorImage object
    override fun getProcessedImage(bitmap: Bitmap): TensorImage {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return imageProcessor.process(tensorImage)
    }

    /*********************************SAMPLES MANAGER************************************************/

    // Add a new sample to the list
    override fun addSample(bitmap: Bitmap, label: Int) {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedTensorImage = imageProcessor.process(tensorImage)

        samples.add(TrainingSample.fromTensorImage(processedTensorImage, label))
        samples.shuffle()
    }

    // Clear the list of samples
    override fun clearSamples() {
        samples.clear()
    }

    // Return the number of samples in the list
    override fun samplesSize(): Int = samples.size

    /*********************************SAVE AND LOAD************************************************/

    // Save samples to internal storage
    override fun saveSamplesToInternalStorage(context: Context, title: String) {
        val directory = File(context.filesDir, "saved_samples")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "$title.dat")

        try {
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(samples)
            }
            Log.d(MODEL_LOG_TAG, "SamplesProcessor: Muestras guardadas correctamente")
        } catch (e: IOException) {
            Log.e(MODEL_LOG_TAG, "SamplesProcessor: Error al guardar muestras", e)
        }
    }

    // Load the list of samples from the internal storage
    override fun loadSamplesFromInternalStorage(context: Context, title: String) {
        val directory = File(context.filesDir, "saved_samples")
        val file = File(directory, title)
        val samples = mutableListOf<TrainingSample>()

        if (file.exists()) {
            try {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    val loadedSamples = ois.readObject() as? List<TrainingSample>
                    if (loadedSamples != null) {
                        samples.addAll(loadedSamples)
                    }
                }
                Log.d(MODEL_LOG_TAG, "SamplesProcessor: Muestras cargadas correctamente")
            } catch (e: Exception) {
                Log.e(MODEL_LOG_TAG, "SamplesProcessor: Error al cargar muestras", e)
                e.printStackTrace()
            }
        } else {
            Log.d(MODEL_LOG_TAG, "SamplesProcessor: No se encontró el archivo de muestras")
        }

        this.samples.addAll(samples)
    }

    // List the files in the internal storage directory
    override fun listSavedSamples(context: Context): List<String> {
        val directory = File(context.filesDir, "saved_samples")
        val fileNames =
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }

        return fileNames
    }

    /******************************AUGMENTATION************************************************/
    // Apply data augmentation to the samples
    private fun getAugmentedSamples(): List<TrainingSample> {
        val augmentedSamples = mutableListOf<TrainingSample>()

        samples.forEach { sample ->
            augmentedSamples.add(sample)
            augmentedSamples.add(
                TrainingSample.fromByteBuffer(
                    flip(sample.toByteBuffer()),
                    sample.label
                )
            )
            augmentedSamples.add(
                TrainingSample.fromByteBuffer(
                    flip(
                        sample.toByteBuffer(),
                        sx = 1f,
                        sy = -1f
                    ), sample.label
                )
            )
        }
        return augmentedSamples
    }

    // Flip the image horizontally or vertically
    private fun flip(imageBuffer: ByteBuffer, sx: Float = -1f, sy: Float = 1f): ByteBuffer {
        val bitmap = Bitmap.createBitmap(IMG_SIZE, IMG_SIZE, Bitmap.Config.ARGB_8888)
        imageBuffer.rewind()
        bitmap.copyPixelsFromBuffer(imageBuffer)

        val matrix = Matrix().apply { postScale(sx, sy) }
        val flippedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        val flippedBuffer = ByteBuffer.allocateDirect(IMG_SIZE * IMG_SIZE * 4)
        flippedBuffer.order(ByteOrder.nativeOrder())
        flippedBitmap.copyPixelsToBuffer(flippedBuffer)
        flippedBuffer.rewind()

        bitmap.recycle()
        flippedBitmap.recycle()

        return flippedBuffer
    }

    /********************************ITERATOR*************************************************/
    // Return an iterator for the list of samples
    override fun trainingBatchesIterator(
        trainBatchSize: Int,
        validationSet: Boolean
    ): Iterator<List<TrainingSample>> {

        val trainingSamples = getAugmentedSamples()
        val (valSet, trainSet) = trainingSamples.shuffled().let {
            (it.subList(
                0,
                (it.size * 0.2f).toInt()
            ) to it.subList((it.size * 0.2f).toInt(), it.size))
        }

        val iterableSamples = if (validationSet) valSet else trainSet

        return object : Iterator<List<TrainingSample>> {

            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < iterableSamples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= iterableSamples.size) {
                    iterableSamples.subList(
                        iterableSamples.size - trainBatchSize,
                        iterableSamples.size
                    )
                } else {
                    iterableSamples.subList(fromIndex, toIndex)
                }
            }
        }
    }
}
