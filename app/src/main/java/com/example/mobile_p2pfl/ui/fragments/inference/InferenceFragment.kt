package com.example.mobile_p2pfl.ui.fragments.inference

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R
import com.example.mobile_p2pfl.ai.inference.Classifier
import com.example.mobile_p2pfl.ai.ml_controller.LearningModel
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.common.Values.INFERENCE_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentInferenceBinding
import java.io.IOException

class InferenceFragment : Fragment() {

    private var _binding: FragmentInferenceBinding? = null

    private val binding get() = _binding!!

    private lateinit var classifier: LearningModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inferenceViewModel =
            ViewModelProvider(this)[InferenceViewModel::class.java]

        _binding = FragmentInferenceBinding.inflate(inflater, container, false)
        val root: View = binding.root


        init()

        return root
    }

    private fun init() {
        initClassifier()
        initView()
    }


    private fun initClassifier() {
        try {
            //
            // classifier = Classifier(binding.root.context)
            classifier = LearningModel(binding.root.context)
            Log.v(INFERENCE_FRAG_LOG_TAG, "Classifier initialized")
        } catch (e: IOException) {
            Toast.makeText(binding.root.context, R.string.exception_failed_to_create_classifier, Toast.LENGTH_LONG).show()
            Log.e(INFERENCE_FRAG_LOG_TAG, "init(): Failed to create Classifier", e)
        }
    }
    private fun initView() {
        binding.btnDetect.setOnClickListener { onDetectClick() }
        binding.btnClear.setOnClickListener { clearResult() }
    }



    private fun onDetectClick() {
        if (!this::classifier.isInitialized) {
            Log.e(INFERENCE_FRAG_LOG_TAG, "onDetectClick(): Classifier is not initialized")
            return
        } else if (binding.fpvInferenceDraw.empty) {
            Toast.makeText(binding.root.context, R.string.please_write_a_digit, Toast.LENGTH_SHORT).show()
            return
        }

        val image: Bitmap = binding.fpvInferenceDraw.exportToBitmap(
            28,28
        )
        val result = classifier.classify(image)
        renderResult(result)
    }

    private fun renderResult(result: Recognition) {
        binding.tvPrediction.text = java.lang.String.valueOf(result.label)
        binding.tvProbability.text = java.lang.String.valueOf(result.confidence)
        binding.tvTimecost.text = java.lang.String.format(
            getString(R.string.timecost_value),
            result.timeCost
        )
    }
    private fun clearResult() {
        binding.fpvInferenceDraw.clear()
        binding.tvPrediction.setText(R.string.empty)
        binding.tvProbability.setText(R.string.empty)
        binding.tvTimecost.setText(R.string.empty)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}