package com.example.mobile_p2pfl.ui.fragments.training

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
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

    private var _trainingViewModel: TrainingViewModel? = null
    private val trainingViewModel get() = _trainingViewModel!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _trainingViewModel = ViewModelProvider(this)[TrainingViewModel::class.java]

        init()

        return root
    }

    private fun init() {
        initView()
        if (trainingViewModel._trainer.value == null)
            initTrainer(trainingViewModel._numThreads.value!!)
    }


    private fun initTrainer(numThreads: Int) {
        if (trainingViewModel._isTraining.value == true) {
            Toast.makeText(
                context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_LONG
            ).show()
        } else
            try {
                trainingViewModel._trainer.value =
                    Trainer(binding.root.context, numThreads) //, Device.CPU

                if (trainingViewModel._oldTrainningSamples.value!!.isNotEmpty()) {
                    trainingViewModel._trainningSamples.value =
                        trainingViewModel._trainningSamples.value?.plus(
                            trainingViewModel._oldTrainningSamples.value!!
                        )
                    trainingViewModel._oldTrainningSamples.value = emptyList()
                    loadNewSamples()
                    Log.v(TRAINER_FRAG_LOG_TAG, "load new samples")
                }

                Log.v(TRAINER_FRAG_LOG_TAG, "Trainer initialized")
            } catch (e: IOException) {
                Toast.makeText(
                    context,
                    R.string.exception_failed_to_create_trainer,
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TRAINER_FRAG_LOG_TAG, "init(): Failed to create Trainer", e)
            }
    }

    private fun initView() {
        val npNumber = binding.npNumber
        npNumber.minValue = 0
        npNumber.maxValue = 9

        binding.sbThreadsSelector.progress = trainingViewModel._numThreads.value!! - 1
        binding.tvThreadsNumbLbl.text = trainingViewModel._numThreads.value.toString()
        binding.btnTraining.isChecked = trainingViewModel._isTraining.value!!


        trainingViewModel.isTraining.observe(viewLifecycleOwner) { isTraining ->
            binding.btnAddSample.isEnabled = !isTraining
            binding.sbThreadsSelector.isEnabled = !isTraining
        }
        trainingViewModel.numThreads.observe(viewLifecycleOwner) { numThreads ->
            if (trainingViewModel._isTraining.value == false)
                 initTrainer(numThreads)
        }
        trainingViewModel.trainningSamples.observe(viewLifecycleOwner) { samples ->
            binding.tvSamplesNumbLbl.text = samples.size.toString()
        }

        binding.sbThreadsSelector.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvThreadsNumbLbl.text = (progress + 1).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                trainingViewModel._numThreads.value = seekBar!!.progress + 1
            }
        })
        binding.btnAddSample.setOnClickListener { addSampleClickListener() }
        binding.btnClearSample.setOnClickListener { clearSampleClickListener() }
        binding.btnTraining.setOnClickListener { onTrainingClick() }
    }

    private fun onTrainingClick() {
        if (binding.btnTraining.isChecked) {
            if (loadNewSamples()) {
                trainingViewModel._trainer.value?.startTraining()
                trainingViewModel._isTraining.value = true
                Log.v(TRAINER_FRAG_LOG_TAG, "Training started")
            } else {
                Log.v(TRAINER_FRAG_LOG_TAG, "Training couldn't start")
                binding.btnTraining.isChecked = false
            }

        } else {
            trainingViewModel._trainer.value?.pauseTraining()
            trainingViewModel._isTraining.value = false
            Log.v(TRAINER_FRAG_LOG_TAG, "Training paused")
        }

    }

    private fun loadNewSamples(): Boolean {
        val trainer = trainingViewModel._trainer.value!!
        val samples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._trainningSamples.value ?: emptyList()
        val oldSamples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._oldTrainningSamples.value ?: emptyList()

        if (samples.size > 2 || trainer.getSamplesSize() > 2) {
            for (sample in samples) {
                trainer.addTrainingSample(sample.image, sample.number)
            }
            trainingViewModel._oldTrainningSamples.value = oldSamples + samples
            trainingViewModel._trainningSamples.value = emptyList()
            return true
        } else {
            Toast.makeText(
                context,
                R.string.exception_too_few_samples,
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }


    private fun addSampleClickListener() {
        val trainer = trainingViewModel._trainer.value!!
        val image: Bitmap = binding.fpvInferenceDraw.exportToBitmap(
            trainer.getInputShape().width, trainer.getInputShape().height
        )
        val number = binding.npNumber.value


        //trainer.addTrainingSample(image, number)


        val samples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._trainningSamples.value ?: emptyList()
        val newSample = TrainingViewModel.TrainingSample(image, number)

        trainingViewModel._trainningSamples.value = samples + newSample



        binding.fpvInferenceDraw.clear()
    }

    private fun clearSampleClickListener() {
        binding.fpvInferenceDraw.clear()

    }


    override fun onDestroyView() {
        super.onDestroyView()
        //trainingViewModel._isTraining.value = false
        //trainingViewModel._trainer.value!!.closeTrainer()
        _binding = null
    }


}
