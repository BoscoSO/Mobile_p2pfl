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
import com.example.mobile_p2pfl.common.LearningModelEventListener
import com.example.mobile_p2pfl.common.Values.TRAINER_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentTrainingBinding
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
            afterTraining()
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
        setListeners()
        updateSavedSamples()


        return root
    }


    @SuppressLint("DefaultLocale")
    private fun initView() {
        val npNumber = binding.npNumber
        npNumber.minValue = 0
        npNumber.maxValue = 9

        binding.sbThreadsSelector.progress = trainingViewModel._numThreads.value!! - 1
        binding.tvThreadsNumbLbl.text = trainingViewModel._numThreads.value.toString()
        binding.btnTraining.isChecked = masterViewModel._isTraining.value!!

        masterViewModel.isTraining.observe(viewLifecycleOwner) { isTraining ->
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

            binding.tvLossOut.text = String.format( "%.5f", loss)
        }
        trainingViewModel.lastAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvAccOut.text =String.format("%.5f", accuracy)
        }
        trainingViewModel.lastValAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvValAccOut.text = String.format("%.5f", accuracy)
        }
        trainingViewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.loadingProgressBar.visibility = View.VISIBLE
                masterViewModel._isTraining.value = true
            } else {
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


    }

    private fun setListeners() {


        binding.btnOpenOptions.setOnClickListener {
            binding.lyOptions.visibility = View.VISIBLE
            binding.btnCloseOptions.visibility = View.VISIBLE
            binding.btnOpenOptions.visibility = View.GONE
        }
        binding.btnCloseOptions.setOnClickListener {
            binding.lyOptions.visibility = View.GONE
            binding.btnCloseOptions.visibility = View.GONE
            binding.btnOpenOptions.visibility = View.VISIBLE
        }
        binding.checkboxClearAll.setOnClickListener {
            trainingViewModel._clearSamples.value = binding.checkboxClearAll.isChecked
        }
        binding.checkboxSave.setOnClickListener {
            trainingViewModel._saveSamples.value = binding.checkboxSave.isChecked

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

        binding.sampleReload.setOnClickListener {
            val strTitle = binding.sampleReloadSelector.selectedItem.toString()
            masterViewModel.modelController.loadSavedSamples(strTitle)
            trainingViewModel._loadedSamples.value =
                masterViewModel.modelController.getSamplesSize()
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
        if (trainingViewModel._loading.value == true) {
            Toast.makeText(binding.root.context, R.string.exception_training_in_progress, Toast.LENGTH_SHORT)
                .show()
            return false
        }
        if (masterViewModel.modelController.getSamplesSize() <= 9) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_too_few_samples,
                Toast.LENGTH_LONG
            ).show()
            return false
        } else {
            return true
        }
    }


    private fun addSampleClickListener() {
        if (binding.fpvSamplesDraw.empty) {
            Toast.makeText(binding.root.context, R.string.please_write_a_digit, Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (trainingViewModel._loading.value == true) {
            Toast.makeText(binding.root.context, R.string.exception_training_in_progress, Toast.LENGTH_SHORT)
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


    private fun afterTraining() {
        if (trainingViewModel._saveSamples.value == true) {
            var ttle = binding.etSaveSamplesFileName.text.toString()
            if (ttle.isEmpty()) {
                ttle = "defaultName"
            }
            masterViewModel.modelController.saveSamplesToInternalStg(ttle)
        }
        if (trainingViewModel._clearSamples.value == true) {
            masterViewModel.modelController.clearAllSamples()
            trainingViewModel._loadedSamples.value =
                masterViewModel.modelController.getSamplesSize()
        }

        updateSavedSamples()
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

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }


}
