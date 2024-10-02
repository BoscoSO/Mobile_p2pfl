package com.example.mobile_p2pfl.ai.conversor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import com.example.mobile_p2pfl.ai.model.InterpreterProvider.Companion.IMG_SIZE
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ColorSpaceType
import org.tensorflow.lite.support.image.ImageOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ImageProperties
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.random.Random


data class TrainingSample(val image: TensorImage, val label: Int)

data class SerialTrainingSample(val image: ByteBuffer, val label: Int)

class SamplesProcessor {
    private val samples = mutableListOf<TrainingSample>()


    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeWithCropOrPadOp(IMG_SIZE, IMG_SIZE))
        .add(TransformToGrayscaleOp())
        .add(NormalizeOp(0f, 255f))
        .build()

    //fail
    fun saveToJson(context: Context, label: String) {
        val serializableSamples = samples.map { sample ->
            val byteBuffer = sample.image.buffer
            SerialTrainingSample(byteBuffer, sample.label)
        }
        val file = File(context.filesDir, "training_samples_$label.json")

        try {
            val jsonArray = JSONArray()
            for (sample in serializableSamples) {
                val jsonObject = JSONObject()
                val byteArray = ByteArray(sample.image.remaining())
                sample.image.get(byteArray)
                val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

                jsonObject.put("image", base64Image)
                jsonObject.put("label", sample.label)
                jsonArray.put(jsonObject)
            }
            Log.v("JSON", " saved complete ${serializableSamples.size} total samples ")
            file.writeText(jsonArray.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    //fail
    fun loadFromJson(context: Context, label: Int) {
        val file = File(context.filesDir, "training_samples_$label.json")
        val jsonString = file.readText()
        val jsonArray = JSONArray(jsonString)
        val samplesaux = mutableListOf<TrainingSample>()


        val dataType = DataType.FLOAT32


        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val base64Image = jsonObject.getString("image")
            val intLabel = jsonObject.getInt("label")

            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val byteBuffer = ByteBuffer.wrap(imageBytes)


            val imgProperties=ImageProperties.builder()
                .setWidth(28)
                .setHeight(28)
                .setColorSpaceType(ColorSpaceType.GRAYSCALE)
                .build()

            val tensorImage = TensorImage(dataType)
            tensorImage.load(byteBuffer,imgProperties)

            //val processedImage = imageProcessor.process(tensorImage)

            Log.v("JSON", " loaded sample $intLabel with image size ${tensorImage.width}x${tensorImage.height}")
            samplesaux.add(TrainingSample(tensorImage, intLabel))
        }

        samples.addAll(samplesaux)
        samples.shuffle()

        Log.v("JSON", " loaded complete ${samplesaux.size} total samples : ${samples.size} ")
    }




    fun samplesSize(): Int = samples.size
    fun isSamplesEmpty(): Boolean = samples.isEmpty()

    fun clearSamples() {
        samples.clear()
    }
    fun getSamples(): List<TrainingSample> = samples


    fun getProcessedImage(bitmap: Bitmap): TensorImage {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return imageProcessor.process(tensorImage)
    }

    fun addSample(bitmap: Bitmap, label: Int) {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedTensorImage = imageProcessor.process(tensorImage)

        samples.add(TrainingSample(processedTensorImage, label))
        samples.shuffle()

    }


    private fun flip(image: TensorImage, sx: Float = -1f, sy: Float = 1f): TensorImage {
        var bitmap = image.bitmap
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val matrix = Matrix().apply { postScale(sx, sy) }
        val flippedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        return getProcessedImage(flippedBitmap)
    }

    /******************************AUGMENTATION************************************************/
    private fun getAugmentedSamples(): List<TrainingSample> {
        val augmentedSamples = mutableListOf<TrainingSample>()

        samples.forEach { sample ->
            augmentedSamples.add(sample)
            augmentedSamples.add(TrainingSample(flip(sample.image), sample.label))
            augmentedSamples.add(TrainingSample(flip(sample.image, sx = 1f, sy = -1f), sample.label))
        }
        return augmentedSamples
    }
    /********************************ITERATOR*************************************************/
    fun trainingBatchesIterator(trainBatchSize: Int): Iterator<List<TrainingSample>> {
        val trainingSamples = getAugmentedSamples()

        return object : Iterator<List<TrainingSample>> {

            private var nextIndex = 0

            override fun hasNext(): Boolean {
                return nextIndex < trainingSamples.size
            }

            override fun next(): List<TrainingSample> {
                val fromIndex = nextIndex
                val toIndex: Int = nextIndex + trainBatchSize
                nextIndex = toIndex
                return if (toIndex >= trainingSamples.size) {
                    trainingSamples.subList(
                        trainingSamples.size - trainBatchSize,
                        trainingSamples.size
                    )
                } else {
                    trainingSamples.subList(fromIndex, toIndex)
                }
            }
        }
    }
}
