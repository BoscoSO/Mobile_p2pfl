package com.example.mobile_p2pfl.ui.fragments.training

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R
import com.example.mobile_p2pfl.ai.controller.LearningModel.Companion.IMG_SIZE
import com.example.mobile_p2pfl.ai.controller.MnistLoader
import com.example.mobile_p2pfl.common.LearningModelEventListener
import com.example.mobile_p2pfl.common.Values.TRAINER_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentTrainingBinding
import com.example.mobile_p2pfl.ui.ConnectionState
import com.example.mobile_p2pfl.ui.MasterViewModel
import java.io.File

class TrainingFragment : Fragment() {

    private var _binding: FragmentTrainingBinding? = null
    private val binding get() = _binding!!

    private var _trainingViewModel: TrainingViewModel? = null
    private val trainingViewModel get() = _trainingViewModel!!

    private lateinit var masterViewModel: MasterViewModel

    private val eventListener = object : LearningModelEventListener {
        override fun updateProgress(loss: Float, accuracy: Float, validationAcc: Float) {
            trainingViewModel.udateProgress(loss, accuracy, validationAcc)
        }

        override fun onLoadingStarted() {
            trainingViewModel.setInfo("Training...")
            trainingViewModel.startLoading()
        }

        override fun onLoadingFinished() {
            trainingViewModel.setInfo("Done")
            trainingViewModel.stopLoading()
            updateSavedSamples()
        }

        override fun onError(message: String) {
            trainingViewModel.setError(message)
            trainingViewModel.stopLoading()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _trainingViewModel = ViewModelProvider(requireActivity())[TrainingViewModel::class.java]
        masterViewModel = ViewModelProvider(requireActivity())[MasterViewModel::class.java]

        masterViewModel.modelController.setEventListener(eventListener)



        initView()
        setupTrainer()
        updateSavedSamples()


        return root
    }

    private fun updateSavedSamples() {
        val directory = File(binding.root.context.filesDir, "saved_samples")

        val fileNames =
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }


        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fileNames)
        binding.sampleReloadSelector.adapter = adapter
    }

    private fun setupTrainer() {
        if (masterViewModel._isTraining.value == true) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_LONG
            ).show()
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
        trainingViewModel.info.observe(viewLifecycleOwner) { info ->
            binding.tvInfoMsg.text = info
            binding.tvInfoMsg.visibility = View.VISIBLE
            binding.tvErrorMsg.visibility = View.INVISIBLE
        }
        trainingViewModel.error.observe(viewLifecycleOwner) { error ->
            binding.tvErrorMsg.text = error
            binding.tvErrorMsg.visibility = View.VISIBLE
            binding.tvInfoMsg.visibility = View.INVISIBLE
        }
        trainingViewModel.lastLoss.observe(viewLifecycleOwner) { loss ->
            binding.tvLossOut.text = loss.toString()
        }
        trainingViewModel.lastAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvAccOut.text = accuracy.toString()
        }
        trainingViewModel.lastValAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvValAccOut.text = accuracy.toString()
        }
        trainingViewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading)
                binding.loadingProgressBar.visibility = View.VISIBLE
            else {
                binding.loadingProgressBar.visibility = View.INVISIBLE
                binding.btnTraining.isChecked = false
                masterViewModel._isTraining.value = false
            }
        }
        trainingViewModel.numThreads.observe(viewLifecycleOwner) { numThreads ->
            if (masterViewModel._isTraining.value == false) {
                masterViewModel.setNumThreads(numThreads)
            }
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
            addSampleClickListener()
        }
        binding.btnClearSample.setOnClickListener {
            binding.fpvSamplesDraw.clear()
        }
        binding.btnTraining.setOnClickListener {
            onTrainingClick()
        }

        binding.sampleReload.setOnClickListener{
            val strTitle = binding.sampleReloadSelector.selectedItem.toString()
            masterViewModel.modelController.loadSavedSamples(strTitle)
            trainingViewModel._loadedSamples.value = masterViewModel.modelController.getSamplesSize()
        }
    }

    private fun onTrainingClick() {
        if (binding.btnTraining.isChecked) {
            if (checkModelAndSamples()) {
                try {
                    masterViewModel.modelController.startTraining()
                } catch (e: Exception) {
                    Log.v(TRAINER_FRAG_LOG_TAG, "Training couldn't start")
                }
                masterViewModel._isTraining.value = true
                Log.v(TRAINER_FRAG_LOG_TAG, "Training started")
            } else {
                binding.btnTraining.isChecked = false
            }

        } else {
            masterViewModel.modelController.pauseTraining()
            Log.v(TRAINER_FRAG_LOG_TAG, "Training paused")
        }

    }

    private fun checkModelAndSamples(): Boolean {
        if (!masterViewModel.modelController.isModelInitialized()) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_no_model,
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val trainer = masterViewModel.modelController

        if (trainer.getSamplesSize() >= 8) {
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
        if (binding.fpvSamplesDraw.empty) {
            Toast.makeText(binding.root.context, R.string.please_write_a_digit, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val image: Bitmap = binding.fpvSamplesDraw.exportToBitmap(
            IMG_SIZE, IMG_SIZE
        )
        val number = binding.npNumber.value

        masterViewModel.modelController.addTrainingSample(image, number)
        trainingViewModel._loadedSamples.value = masterViewModel.modelController.getSamplesSize()

        binding.fpvSamplesDraw.clear()
    }


    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }


}
