package com.example.mobile_p2pfl.ui.fragments.training

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
import com.example.mobile_p2pfl.ai.training.Trainer
import com.example.mobile_p2pfl.common.Values.TRAINER_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentTrainingBinding
import java.io.IOException

class TrainingFragment : Fragment() {

    private var _binding: FragmentTrainingBinding? = null

    private val binding get() = _binding!!


    private lateinit var trainer: Trainer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val trainingViewModel =
            ViewModelProvider(this)[TrainingViewModel::class.java]


        _binding = FragmentTrainingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        init()

        return root
    }

    private fun init() {
        initTrainer()
        initView()
    }


    private fun initTrainer() {
        try {
            trainer = Trainer(binding.root.context)
            Log.v(TRAINER_FRAG_LOG_TAG, "Trainer initialized")
        } catch (e: IOException) {
            Toast.makeText(binding.root.context, R.string.failed_to_create_trainer, Toast.LENGTH_LONG).show()
            Log.e(TRAINER_FRAG_LOG_TAG, "init(): Failed to create Trainer", e)
        }
    }

    private fun initView() {
        val npNumber = binding.npNumber
        npNumber.minValue= 0
        npNumber.maxValue= 9

        binding.btnAddSample.setOnClickListener{ addSampleClickListener()}
        binding.btnClearSample.setOnClickListener{ clearSampleClickListener()}
        binding.btnTraining.setOnClickListener{ onTrainingClick() }
    }

    private fun onTrainingClick() {
        if (binding.btnTraining.isChecked){
            trainer.startTraining()

        }else{
            trainer.pauseTraining()
        }

    }


    private fun addSampleClickListener(){

        val image: Bitmap = binding.fpvInferenceDraw.exportToBitmap(
            trainer.getInputShape().width, trainer.getInputShape().height)
        val number = binding.npNumber.value

        Log.v(TRAINER_FRAG_LOG_TAG, "Sample added: $number")
        trainer.addTrainingSample(image, number)


        binding.fpvInferenceDraw.clear()



    }
    private fun clearSampleClickListener(){
        binding.fpvInferenceDraw.clear()

    }


    override fun onDestroyView() {
        super.onDestroyView()
        trainer.closeTrainer()
        _binding = null
    }


}
