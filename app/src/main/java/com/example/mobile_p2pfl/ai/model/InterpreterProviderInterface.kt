package com.example.mobile_p2pfl.ai.model

import org.tensorflow.lite.Interpreter

interface InterpreterProviderInterface {

    // Devuelve si el modelo ha sido inicializado
    fun isModelInitialized(): Boolean

    // Establece el número de hilos de ejecución
    fun setNumberOfThreads(numThreads: Int): Boolean

    // Devuelve el intérprete de TensorFlow
    fun getInterpreter(): Interpreter?

    // Devuelve la configuración óptima para el tamaño de muestra
    fun getOptimalConfigFor(samplesSize: Int): Config

    // Clase de configuración para el intérprete
    enum class Config(val signature: String, val batchSize: Int) {
        XS("train_fixed_batch_xs", 8),
        S("train_fixed_batch_s", 16),
        M("train_fixed_batch_m", 32),
        L("train_fixed_batch_l", 64)
    }
}