package com.example.mobile_p2pfl.ui.fragments.inference

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R
import com.example.mobile_p2pfl.common.Recognition
import com.example.mobile_p2pfl.databinding.FragmentInferenceBinding
import com.example.mobile_p2pfl.ui.MasterViewModel

class InferenceFragment : Fragment() {

    private var _binding: FragmentInferenceBinding? = null
    private val binding get() = _binding!!


    private lateinit var masterViewModel: MasterViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        val inferenceViewModel =
//            ViewModelProvider(this)[InferenceViewModel::class.java]

        _binding = FragmentInferenceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        masterViewModel = ViewModelProvider(requireActivity())[MasterViewModel::class.java]


        init()

        return root
    }

    private fun init() {

//        val npNumber = binding.npPrediction
//        npNumber.minValue = 0
//        npNumber.maxValue = 9
//        npNumber.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        binding.btnDetect.setOnClickListener { onDetectClick() }
        binding.btnClear.setOnClickListener { clearResult() }


    }


    private fun onDetectClick() {
        if (!masterViewModel.modelController.isModelInitialized()) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_no_model,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (masterViewModel._isTraining.value == true) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (binding.fpvInferenceDraw.empty) {
            Toast.makeText(binding.root.context, R.string.please_write_a_digit, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val image: Bitmap = binding.fpvInferenceDraw.exportToBitmap(
            28, 28
        )
        val result = masterViewModel.modelController.classify(image)
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