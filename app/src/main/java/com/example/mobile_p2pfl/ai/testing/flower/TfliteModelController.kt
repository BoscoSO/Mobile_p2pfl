package com.example.mobile_p2pfl.ai.testing;

import android.graphics.Bitmap
import android.util.Log
import com.example.mobile_p2pfl.ai.temp.TfliteModelLoaderInterface
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.Values.TRAINER_LOG_TAG
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.util.TreeMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.max
import kotlin.math.min


class TfliteModelController(modelLoader: TfliteModelLoaderInterface, classes: Collection<String>) : Closeable {

    class TrainingSample internal constructor(var bottleneck: ByteBuffer, var className: String)
    class TestingSample internal constructor(var bottleneck: ByteBuffer, var className: String)


    fun interface LossConsumer {
        fun onLoss(epoch: Int, loss: Float)
    }


    val FLOAT_BYTES: Int = 4

    val NUM_THREADS: Int =
        max(1.0, (Runtime.getRuntime().availableProcessors() - 1).toDouble()).toInt()

    private var bottleneckShape: IntArray

    private var classesMap: Map<String, Int>? = null
    private var classesByIdx: Array<String>

    private var tfliteModel: TfLiteModelActions? = null

    private val trainingSamples: List<TrainingSample> = ArrayList()
    private val testingSamples: List<TestingSample> = ArrayList()

    private var modelParameters: Array<ByteBuffer?>
    private var nextModelParameters: Array<ByteBuffer?>

    private var optimizerState: Array<ByteBuffer?>
    private var nextOptimizerState: Array<ByteBuffer?>

    private var trainingBatchBottlenecks: ByteBuffer? = null
    private var trainingBatchClasses: ByteBuffer? = null
    private var zeroBatchClasses: ByteBuffer? = null

    private var modelGradients: Array<ByteBuffer?>

    private var inferenceBottleneck: ByteBuffer? = null

    private val executor: ExecutorService = Executors.newFixedThreadPool(NUM_THREADS)

    private val trainingLock: Lock = ReentrantLock()
    private val parameterLock: ReadWriteLock = ReentrantReadWriteLock()
    private val inferenceLock: Lock = ReentrantLock()

    private var isTerminating = false


    init {
        classesByIdx = classes.toTypedArray<String>()
        classesMap = TreeMap()
        for (classIdx in classes.indices) {
            (classesMap as TreeMap<String, Int>)[classesByIdx[classIdx]] = classIdx
        }
        try {
            tfliteModel = TfLiteModelActions(modelLoader.loadTfliteModel())

        } catch (e: IOException) {
            throw RuntimeException("Couldn't read underlying models for TransferLearningModel", e)
        }

        this.bottleneckShape = tfliteModel!!.getBottleneckShape()
        val modelParameterSizes = tfliteModel!!.getParameterSizes()

        modelParameters = arrayOfNulls(modelParameterSizes.size)
        modelGradients = arrayOfNulls(modelParameterSizes.size)
        nextModelParameters = arrayOfNulls(modelParameterSizes.size)

        for (parameterIndex in modelParameterSizes.indices) {
            val bufferSize: Int = modelParameterSizes[parameterIndex] * FLOAT_BYTES
            modelParameters[parameterIndex] = allocateBuffer(bufferSize)
            modelGradients[parameterIndex] = allocateBuffer(bufferSize)
            nextModelParameters[parameterIndex] = allocateBuffer(bufferSize)
        }
        tfliteModel!!.initializeParameters(modelParameters)


        val optimizerStateElementSizes = tfliteModel!!.stateElementSizes()
        optimizerState = arrayOfNulls(optimizerStateElementSizes.size)
        nextOptimizerState = arrayOfNulls(optimizerStateElementSizes.size)
        for (elemIdx in optimizerState.indices) {
            val bufferSize: Int =
                optimizerStateElementSizes[elemIdx] * FLOAT_BYTES
            optimizerState[elemIdx] = allocateBuffer(bufferSize)
            nextOptimizerState[elemIdx] = allocateBuffer(bufferSize)
            fillBufferWithZeros(
                optimizerState[elemIdx]
            )
        }
        trainingBatchBottlenecks =
            allocateBuffer(getTrainBatchSize() * numBottleneckFeatures() * FLOAT_BYTES)
        val batchClassesNumElements: Int = getTrainBatchSize() * classes.size

        trainingBatchClasses = allocateBuffer(batchClassesNumElements * FLOAT_BYTES)
        zeroBatchClasses = allocateBuffer(batchClassesNumElements * FLOAT_BYTES)

        for (idx in 0 until batchClassesNumElements) {
            zeroBatchClasses!!.putFloat(0f)
        }
        zeroBatchClasses!!.rewind()
        inferenceBottleneck = allocateBuffer(numBottleneckFeatures() * FLOAT_BYTES)

    }

    fun getBottleneckShape(): IntArray {
        return this.bottleneckShape
    }


    // Añade una nueva muestra para el entrenamiento/prueba.
    fun addSample(image: FloatArray, className: String?, isTraining: Boolean): Future<Void?> {
        checkNotTerminating()

        require(classesMap!!.containsKey(className)) {
            String.format(
                "Class \"%s\" is not one of the classes recognized by the model", className
            )
        }

        return executor.submit<Void?> {
            val imageBuffer: ByteBuffer =
                allocateBuffer(image.size * FLOAT_BYTES)
            for (f in image) {
                imageBuffer.putFloat(f)
            }
            imageBuffer.rewind()

            if (Thread.interrupted()) {
                return@submit null
            }
            val bottleneck = tfliteModel!!.generateBottleneck(imageBuffer, null)

            trainingLock.lockInterruptibly()
            try {
                if (isTraining)
                    (trainingSamples as ArrayList<TrainingSample>).add(
                        TrainingSample(
                            bottleneck,
                            className!!
                        )
                    )
                else
                    (testingSamples as ArrayList<TestingSample>).add(
                        TestingSample(
                            bottleneck,
                            className!!
                        )
                    )
            } finally {
                trainingLock.unlock()
            }
            null
        }
    }

    // Entrena el modelo en las muestras previamente añadidas.
    fun train(numEpochs: Int, lossConsumer: LossConsumer?): Future<Void> {
        checkNotTerminating()

        Log.e("DDFF", getTrainBatchSize().toString() + "")

        if (trainingSamples.size < getTrainBatchSize()) {
            throw java.lang.RuntimeException(
                String.format(
                    "Too few samples to start training: need %d, got %d",
                    getTrainBatchSize(), trainingSamples.size
                )
            )
        }

        return executor.submit<Void> {
            trainingLock.lock()
            try {
                epochLoop@ for (epoch in 0 until numEpochs) {
                    var totalLoss = 0f
                    var numBatchesProcessed = 0

                    for (batch in trainingBatches()) {
                        if (Thread.interrupted()) {
                            break@epochLoop
                        }

                        trainingBatchClasses!!.put(zeroBatchClasses!!)
                        trainingBatchClasses!!.rewind()
                        zeroBatchClasses!!.rewind()

                        for (sampleIdx in batch.indices) {
                            val sample =
                                batch[sampleIdx]
                            trainingBatchBottlenecks!!.put(sample.bottleneck)
                            sample.bottleneck.rewind()

                            // Fill trainingBatchClasses with one-hot.
                            val position: Int =
                                (sampleIdx * classesMap!!.size + classesMap!![sample.className]!!) * FLOAT_BYTES
                            trainingBatchClasses!!.putFloat(position, 1f)
                        }
                        trainingBatchBottlenecks!!.rewind()

                        val loss =
                            tfliteModel!!.calculateGradients(
                                trainingBatchBottlenecks!!,
                                trainingBatchClasses!!,
                                modelParameters,
                                modelGradients
                            )
                        totalLoss += loss
                        numBatchesProcessed++

                        tfliteModel!!.performStep(
                            modelParameters,
                            modelGradients,
                            // optimizerState,
                            nextModelParameters,
                            //nextOptimizerState
                        )

                        var swapBufferArray: Array<ByteBuffer?>

                        // Swap optimizer state with its next version.
                        swapBufferArray = optimizerState
                        optimizerState = nextOptimizerState
                        nextOptimizerState = swapBufferArray

                        // Swap model parameters with their next versions.
                        parameterLock.writeLock().lock()
                        try {
                            swapBufferArray = modelParameters
                            modelParameters = nextModelParameters
                            nextModelParameters = swapBufferArray
                        } finally {
                            parameterLock.writeLock().unlock()
                        }
                    }
                    val avgLoss = totalLoss / numBatchesProcessed

                    Log.d(TRAINER_LOG_TAG, "Average loss: $avgLoss")

                    //lossConsumer?.onLoss(epoch, avgLoss)
                }

                return@submit null
            } finally {
                trainingLock.unlock()
            }
        }
    }

    fun trainingInProgress(): Boolean {
        if (trainingLock.tryLock()) {
            trainingLock.unlock()
            return false
        } else {
            return true
        }
    }


    // Realiza predicciones en una imagen.
    fun predict(bitmapImage: Bitmap): Recognition? {

        checkNotTerminating()
        inferenceLock.lock()

        try {
            if (isTerminating) {
                return null
            }

            val image = tfliteModel!!.convertBitmapToByteBuffer(bitmapImage)


            parameterLock.readLock().lock()
            val result : Recognition
            try {
                result = tfliteModel!!.runInference(image)
            } finally {
                parameterLock.readLock().unlock()
            }

            return result
        } finally {
            inferenceLock.unlock()
        }
    }


    // Escribe los valores actuales de los parámetros del modelo a un canal de escritura.
    @Throws(IOException::class)
    fun saveParameters(outputChannel: GatheringByteChannel) {
        parameterLock.readLock().lock()
        try {
            outputChannel.write(modelParameters)
            for (buffer in modelParameters) {
                buffer!!.rewind()
            }
        } finally {
            parameterLock.readLock().unlock()
        }
    }


    // Devuelve los valores actuales de los parámetros del modelo.
    fun getParameters(): Array<ByteBuffer?> {
        return modelParameters
    }


    // Carga los valores de los parámetros del modelo desde un canal de lectura.
    @Throws(IOException::class)
    fun loadParameters(inputChannel: ScatteringByteChannel) {
        parameterLock.writeLock().lock()
        try {
            inputChannel.read(modelParameters)
            for (buffer in modelParameters) {
                buffer!!.rewind()
            }
        } finally {
            parameterLock.writeLock().unlock()
        }
    }


    fun getInputShape(): IntArray {
        return tfliteModel!!.getInputShape()
    }
    fun getTrainBatchSize(): Int {
        return tfliteModel!!.getBatchSize()
    }


    private fun trainingBatches(): Iterable<List<TrainingSample>> {
        if (!trainingLock.tryLock()) {
            throw RuntimeException("Thread calling trainingBatches() must hold the training lock")
        }
        trainingLock.unlock()

        trainingSamples.shuffled()
        return Iterable {
            object : Iterator<List<TrainingSample>> {
                private var nextIndex = 0

                override fun hasNext(): Boolean {
                    return nextIndex < trainingSamples.size
                }

                override fun next(): List<TrainingSample> {
                    val fromIndex = nextIndex
                    val toIndex = nextIndex + getTrainBatchSize()
                    nextIndex = toIndex
                    return if (toIndex >= trainingSamples.size) {
                        trainingSamples.subList(
                            trainingSamples.size - getTrainBatchSize(),
                            trainingSamples.size
                        )
                    } else {
                        trainingSamples.subList(fromIndex, toIndex)
                    }
                }
            }
        }
    }

    private fun checkNotTerminating() {
        check(!isTerminating) { "Cannot operate on terminating model" }
    }

    private fun numBottleneckFeatures(): Int {
        var result = 1
        for (size in bottleneckShape) {
            result *= size
        }

        return result
    }

    // Cierra el modelo y libera los recursos.
    override fun close() {
        isTerminating = true
        executor.shutdownNow()

        inferenceLock.lock()

        try {
            val ok = executor.awaitTermination(5, TimeUnit.SECONDS)
            if (!ok) {
                throw java.lang.RuntimeException("Model thread pool failed to terminate")
            }

            tfliteModel!!.close()
        } catch (e: InterruptedException) {
            // no-op
        } finally {
            inferenceLock.unlock()
        }
    }

    // Crea un buffer de memoria dinámica.
    private fun allocateBuffer(capacity: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(capacity)
        buffer.order(ByteOrder.nativeOrder())
        return buffer
    }

    // Devuelve el número de muestras de entrenamiento.
    fun getSize_Training(): Int {
        return trainingSamples.size
    }

    // Devuelve el número de muestras de prueba.
    fun getSize_Testing(): Int {
        return testingSamples.size
    }


    // Actualiza los valores de los parámetros del modelo.
    fun updateParameters(newParams: Array<ByteBuffer?>) {
        parameterLock.writeLock().lock()
        try {
            modelParameters = newParams
        } finally {
            parameterLock.writeLock().unlock()
        }
    }

    // Rellena un buffer con ceros.
    private fun fillBufferWithZeros(buffer: ByteBuffer?) {
        val bufSize = buffer!!.capacity()
        val chunkSize = min(1024.0, bufSize.toDouble()).toInt()

        val zerosChunk = allocateBuffer(chunkSize)
        for (idx in 0 until chunkSize) {
            zerosChunk.put(0.toByte())
        }
        zerosChunk.rewind()

        for (chunkIdx in 0 until bufSize / chunkSize) {
            buffer.put(zerosChunk)
        }
        for (idx in 0 until bufSize % chunkSize) {
            buffer.put(0.toByte())
        }
    }

}