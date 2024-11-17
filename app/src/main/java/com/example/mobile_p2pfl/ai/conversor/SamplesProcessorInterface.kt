package com.example.mobile_p2pfl.ai.conversor

import android.content.Context
import android.graphics.Bitmap
import com.example.mobile_p2pfl.common.TrainingSample
import org.tensorflow.lite.support.image.TensorImage

interface SamplesProcessorInterface {
    // Preprocesa una imagen para su uso en el modelo
    fun getProcessedImage(bitmap: Bitmap): TensorImage

    /*********************************SAMPLES MANAGER************************************************/
    // Añade una muestra a la lista
    fun addSample(bitmap: Bitmap, label: Int)

    // Limpia la lista de muestras
    fun clearSamples()

    // Devuelve el número de muestras en la lista
    fun samplesSize(): Int

    /*********************************SAVE AND LOAD************************************************/
    // Guarda las muestras en el almacenamiento interno
    fun saveSamplesToInternalStorage(context: Context, title: String)

    // Carga las muestras desde el almacenamiento interno
    fun loadSamplesFromInternalStorage(context: Context, title: String)

    // Lista los nombres de los archivos de muestras guardados
    fun listSavedSamples(context: Context): List<String>

    /********************************ITERATOR*************************************************/
    // Crea un iterador para obtener lotes de muestras de entrenamiento o validación
    fun trainingBatchesIterator(
        trainBatchSize: Int = 1,
        validationSet: Boolean = false
    ): Iterator<List<TrainingSample>>
}