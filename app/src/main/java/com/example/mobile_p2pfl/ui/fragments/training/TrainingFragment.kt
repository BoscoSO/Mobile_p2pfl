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
import com.example.mobile_p2pfl.ai.controller.LearningModel.Companion.IMG_SIZE
import com.example.mobile_p2pfl.ai.controller.MnistLoader
import com.example.mobile_p2pfl.common.Values.TRAINER_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentTrainingBinding
import com.example.mobile_p2pfl.ui.MasterViewModel

class TrainingFragment : Fragment() {

    private var _binding: FragmentTrainingBinding? = null
    private val binding get() = _binding!!

    private var _trainingViewModel: TrainingViewModel? = null
    private val trainingViewModel get() = _trainingViewModel!!

    private lateinit var masterViewModel: MasterViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _trainingViewModel = ViewModelProvider(this)[TrainingViewModel::class.java]
        masterViewModel = ViewModelProvider(requireActivity())[MasterViewModel::class.java]

        initView()
        setupTrainer()

        return root
    }


    private fun setupTrainer() {
        if (masterViewModel._isTraining.value == true) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (trainingViewModel._oldTrainningSamples.value!!.isNotEmpty()) {
                trainingViewModel._trainningSamples.value =
                    trainingViewModel._trainningSamples.value?.plus(
                        trainingViewModel._oldTrainningSamples.value!!
                    )
                trainingViewModel._oldTrainningSamples.value = emptyList()
                loadNewSamples()
                Log.v(
                    TRAINER_FRAG_LOG_TAG,
                    "loading old samples..." + trainingViewModel._trainningSamples.value!!.size
                )
            }
        }
    }

    private fun initView() {
        val npNumber = binding.npNumber
        npNumber.minValue = 0
        npNumber.maxValue = 9

        binding.sbThreadsSelector.progress = trainingViewModel._numThreads.value!! - 1
        binding.tvThreadsNumbLbl.text = trainingViewModel._numThreads.value.toString()
        binding.btnTraining.isChecked = masterViewModel._isTraining.value!!


        masterViewModel.isTraining.observe(viewLifecycleOwner) { isTraining ->
            binding.btnAddSample.isEnabled = !isTraining
            binding.sbThreadsSelector.isEnabled = !isTraining
        }
        trainingViewModel.numThreads.observe(viewLifecycleOwner) { numThreads ->
            if (masterViewModel._isTraining.value == false) {
                masterViewModel.setNumThreads(numThreads)
                setupTrainer()
            }
        }
        trainingViewModel.trainningSamples.observe(viewLifecycleOwner) { samples ->
            binding.tvSamplesNumbLbl.text = samples.size.toString()
        }
        trainingViewModel.loadedSamples.observe(viewLifecycleOwner) { samples ->
            Log.v(TRAINER_FRAG_LOG_TAG, "loaded samples: $samples")
            binding.tvLoadedSamplesNumbLbl.text = samples.toString()
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
        binding.btnAddSample.setOnClickListener {
            //MnistLoader().resavesamples(binding.root.context)
            //masterViewModel.modelController.startTraining2(masterViewModel.grpcClient)

            addSampleClickListener()
        }
        binding.btnClearSample.setOnClickListener {
            binding.fpvInferenceDraw.clear()
        }
        binding.btnTraining.setOnClickListener {
            onTrainingClick()
        }
    }

    private fun onTrainingClick() {

//        val number = binding.npNumber.value.toString() //test

        if (binding.btnTraining.isChecked) {
            //masterViewModel.modelController.mnistTraining() //test

            if (loadNewSamples()) {
//                masterViewModel.modelController.saveSamples(number)// test


                try{
                    masterViewModel.modelController.startTraining()
                }catch (e:Exception){

                    Log.v(TRAINER_FRAG_LOG_TAG, "Training couldn't start")
                }
                masterViewModel._isTraining.value = true
                Log.v(TRAINER_FRAG_LOG_TAG, "Training started")
            } else {

                Log.v(TRAINER_FRAG_LOG_TAG, "Training couldn't start")
                binding.btnTraining.isChecked = false
            }

        } else {
//            val asd=masterViewModel.modelController.loadsamples(number)// test

            masterViewModel.modelController.pauseTraining()
            masterViewModel._isTraining.value = false
            masterViewModel.modelController.saveModel()

            Log.v(TRAINER_FRAG_LOG_TAG, "Training paused")
        }

    }

    private fun loadNewSamples(): Boolean {
        if (!masterViewModel.modelController.isModelInitialized()) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_no_model,
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val trainer = masterViewModel.modelController
        val samples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._trainningSamples.value ?: emptyList()
        val oldSamples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._oldTrainningSamples.value ?: emptyList()

        if (samples.size > 8 || trainer.getSamplesSize() > 8) {
            for (sample in samples) {
                trainer.addTrainingSample(sample.image, sample.label)
            }
            trainingViewModel._oldTrainningSamples.value = oldSamples + samples
            trainingViewModel._trainningSamples.value = emptyList()
            trainingViewModel._loadedSamples.value = trainer.getSamplesSize()
            return true
        } else {
            Toast.makeText(
                binding.root.context,
                R.string.exception_too_few_samples,
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }


    private fun addSampleClickListener() {
        val image: Bitmap = binding.fpvInferenceDraw.exportToBitmap(
            IMG_SIZE, IMG_SIZE
        )
        val number = binding.npNumber.value

        val samples: List<TrainingViewModel.TrainingSample> =
            trainingViewModel._trainningSamples.value ?: emptyList()
        val newSample = TrainingViewModel.TrainingSample(image, number)

        trainingViewModel._trainningSamples.value = samples + newSample
        binding.fpvInferenceDraw.clear()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        //trainingViewModel._isTraining.value = false
        //trainingViewModel._trainer.value!!.closeTrainer()
        _binding = null
    }


}
