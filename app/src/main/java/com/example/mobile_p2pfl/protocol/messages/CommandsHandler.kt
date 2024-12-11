package com.example.mobile_p2pfl.protocol.messages

import android.content.Context
import android.util.Log
import com.example.mobile_p2pfl.ai.controller.ModelAutoController
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import edge_node.NodeOuterClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.random.Random

class CommandsHandler(
    private val context: Context,
    private val learnerController: ModelAutoController,
    private val eventListener: GrpcEventListener,
    private val coroutineScope: CoroutineScope
) {

    fun init(){
        eventListener.startFederatedTraining()
    }

    // Handle incoming messages from the server
    fun handleCommands(message: NodeOuterClass.EdgeMessage): Deferred<NodeOuterClass.EdgeMessage> {
        return coroutineScope.async {
            when (message.cmd) {
                "vote" -> handleVoteMessage(message)
                "validate" -> handleValidateMessage(message)
                "train" -> handleTrainMessage(message)
                else -> NodeOuterClass.EdgeMessage.newBuilder()
                    .setId(message.id)
                    .setCmd("error")
                    .setMessage(0, "Unknown command: ${message.cmd}")
                    .build()
            }
        }
    }

    /*************************LISTENER**************************************************/

    fun notifyError(message: String) {
        eventListener.onError(message)
        eventListener.endFederatedTraining()
    }
    fun notifyEnd(){
        eventListener.endFederatedTraining()
    }

    /*************************HANDLERS**************************************************/

    // Handle vote message from the server
    private fun handleVoteMessage(
        message: NodeOuterClass.EdgeMessage
    ): NodeOuterClass.EdgeMessage {
        eventListener.startInstruction(message.cmd)
        eventListener.updateStep("Sending vote...")
        val candidates = message.messageList

        // Gen vote
        val TRAIN_SET_SIZE = 4
        val samples = minOf(TRAIN_SET_SIZE, candidates.size)
        eventListener.updateProgress(50f)

        val nodesVoted = candidates.shuffled().take(samples)
        val weights = List(samples) { i ->
            (Random.nextInt(1001) / (i + 1))
        }
        eventListener.updateProgress(100f)
        eventListener.updateStep("Vote sent, waiting for next step")
        eventListener.endInstruction()
        return NodeOuterClass.EdgeMessage.newBuilder()
            .setId(message.id)
            .setCmd("vote_response")
            .addAllMessage(nodesVoted) // Añade los nodos votados
            .addAllMessage(weights.map { it.toString() }) // Añade los pesos como strings
            .build()
    }

    // Handle validate message from the server
    private suspend fun handleValidateMessage(
        message: NodeOuterClass.EdgeMessage
    ): NodeOuterClass.EdgeMessage {
        return try {
            eventListener.startInstruction(message.cmd)
            eventListener.updateStep("Processing validation...")
            val messageWeights = message.weights

            val (loss, accuracy) = learnerController.validate(context, eventListener, messageWeights)

            val metricsList = listOf("loss", loss.toString(), "accuracy", accuracy.toString())

            Log.d(GRPC_LOG_TAG, "Validation results: loss=$loss, accuracy=$accuracy")
            eventListener.endInstruction()
            NodeOuterClass.EdgeMessage.newBuilder()
                .setId(message.id)
                .setCmd("validate_response")
                .addAllMessage(metricsList)
                .build()
        } catch (e: Exception) {
            // Manejar el error, enviando una respuesta de error
            Log.e(GRPC_LOG_TAG, "Validation failed: ${e.message}")
            NodeOuterClass.EdgeMessage.newBuilder()
                .setId(message.id)
                .setCmd("validate_response")
                .setMessage(0, "Validation failed: ${e.message}")
                .build()

        }
    }

    // Handle train message from the server
    private suspend fun handleTrainMessage(
        message: NodeOuterClass.EdgeMessage
    ): NodeOuterClass.EdgeMessage {
        return try {

            eventListener.startInstruction(message.cmd)
            eventListener.updateStep("Processing training...")
            val messageWeights = message.weights

            Log.d(GRPC_LOG_TAG, "Starting training")
            val epochs = message.messageList[0].toInt()
            val (loss, accuracy) = learnerController.train(context, eventListener, messageWeights, epochs)


            Log.d(GRPC_LOG_TAG, "Training completed with loss $loss and accuracy $accuracy")
            eventListener.updateStep("Sending new weights to server...")
            val weights = learnerController.getWeights(context)

            Log.d(GRPC_LOG_TAG, "weights sent, size: ${weights?.size()}")

            eventListener.endInstruction()
            NodeOuterClass.EdgeMessage.newBuilder()
                .setId(message.id)
                .setCmd("train_response")
                .setWeights(weights)
                .build()


        } catch (e: Exception) {
            Log.e(GRPC_LOG_TAG, "Training failed: ${e.message}")
            NodeOuterClass.EdgeMessage.newBuilder()
                .setId(message.id)
                .setCmd("train_response")
                .setMessage(0, "Training failed: ${e.message}")
                .build()

        }
    }


}