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
import com.example.mobile_p2pfl.ai.controller.TensorFlowLearnerController.Companion.IMG_SIZE
import com.example.mobile_p2pfl.common.LearningModelEventListener
import com.example.mobile_p2pfl.common.Values.TRAINER_FRAG_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentTrainingBinding
import com.example.mobile_p2pfl.ui.MasterViewModel

class TrainingFragment : Fragment() {

    /******************VARIABLES*********************/

    private var _binding: FragmentTrainingBinding? = null
    private val binding get() = _binding!!

    private var _trainingViewModel: TrainingViewModel? = null
    private val trainingViewModel get() = _trainingViewModel!!

    private lateinit var masterViewModel: MasterViewModel

    /***************MODEL LISTENER*******************/
    private val eventListener = object : LearningModelEventListener {
        override fun updateProgress(
            loss: Float,
            accuracy: Float,
            validationAcc: Float,
            progress: Float
        ) {
            trainingViewModel.udateProgress(loss, accuracy, validationAcc, progress)
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

    /*****************ON CREATE**********************/
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
        /***************CLASS SELECTOR*******************/
        val npNumber = binding.npNumber
        npNumber.minValue = 0
        npNumber.maxValue = 9

        /*****************MESSAGE DISPLAY*******************/
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

        /**************TRAINING PROGRESS DISPLAY***************/
        trainingViewModel.lastLoss.observe(viewLifecycleOwner) { loss ->

            binding.tvLossOut.text = String.format("%.5f", loss)
        }
        trainingViewModel.lastAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvAccOut.text = String.format("%.5f", accuracy)
        }
        trainingViewModel.lastValAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvValAccOut.text = String.format("%.5f", accuracy)
        }

        masterViewModel.isTraining.observe(viewLifecycleOwner) { isTraining ->
            binding.sbThreadsSelector.isEnabled = !isTraining
            binding.btnTraining.isChecked = isTraining
        }
        trainingViewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.loadingProgressBar.visibility = View.VISIBLE
                binding.loadingHorizontalBar.visibility = View.VISIBLE
                masterViewModel._isTraining.value = true
            } else {
                binding.loadingProgressBar.visibility = View.INVISIBLE
                binding.loadingHorizontalBar.visibility = View.INVISIBLE
                binding.btnTraining.isChecked = false
                masterViewModel._isTraining.value = false
                trainingViewModel.clearProgress()
            }
        }
        trainingViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.loadingHorizontalBar.progress = progress!!.toInt()
        }

        /****************TRAINING DATA DISPLAY*****************/
        trainingViewModel.loadedSamples.observe(viewLifecycleOwner) { samples ->
            Log.v(TRAINER_FRAG_LOG_TAG, "loaded samples: $samples")
            binding.tvLoadedSamplesNumbLbl.text = samples.toString()
        }
        trainingViewModel.numThreads.observe(viewLifecycleOwner) { numThreads ->
            binding.sbThreadsSelector.progress = numThreads - 1
            binding.tvThreadsNumbLbl.text = numThreads.toString()
            if (masterViewModel._isTraining.value == false) {
                masterViewModel.setNumThreads(numThreads)
            }
        }
        trainingViewModel.numEpochs.observe(viewLifecycleOwner) { epochs ->
            binding.etNumber.setText(epochs.toString())
        }

        /****************SAVED SAMPLES DISPLAY*****************/
        trainingViewModel.samplesAdapter.observe(viewLifecycleOwner) { adapter ->
            if (adapter != null) {
                binding.sampleReloadSelector.adapter = adapter
            }
        }
    }

    private fun setListeners() {
        /***********EPOCH SELECTOR*******************/
        binding.btnLess.setOnClickListener {
            val epochs = binding.etNumber.text.toString().toInt()
            if (epochs > 1) {
                trainingViewModel._numEpochs.value = epochs - 1
            }
        }
        binding.btnMore.setOnClickListener {
            val epochs = binding.etNumber.text.toString().toInt()
            trainingViewModel._numEpochs.value = epochs + 1
        }
        /***********DROP DOWN OPTIONS****************/
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

        /***********CHECKBOX OPTIONS*******************/
        binding.checkboxClearAll.setOnClickListener {
            trainingViewModel._clearSamples.value = binding.checkboxClearAll.isChecked
        }
        binding.checkboxSave.setOnClickListener {
            trainingViewModel._saveSamples.value = binding.checkboxSave.isChecked

        }

        /***********THREADS SELECTOR*******************/
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

        /***********ADD/CLEAR SAMPLES******************/
        binding.btnAddSample.setOnClickListener {
            addSampleClickListener()
        }
        binding.btnClearSample.setOnClickListener {
            binding.fpvSamplesDraw.clear()
        }

        /***********LOAD SAMPLES***********************/
        binding.sampleReload.setOnClickListener {
            val strTitle = binding.sampleReloadSelector.selectedItem.toString()
            masterViewModel.modelController.loadSavedSamples(strTitle)
            trainingViewModel._loadedSamples.value =
                masterViewModel.modelController.getSamplesSize()
        }

        /***********TRAINING***************************/
        binding.btnTraining.setOnClickListener {
            onTrainingClick()
        }
    }

    /****************ON CLICK CALLS******************/
    private fun addSampleClickListener() {
        if (binding.fpvSamplesDraw.empty) {
            Toast.makeText(binding.root.context, R.string.please_write_a_digit, Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (trainingViewModel._loading.value == true) {
            Toast.makeText(
                binding.root.context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_SHORT
            )
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

    private fun onTrainingClick() {
        if (binding.btnTraining.isChecked) {
            if (checkModelAndSamples()) {
                try {
                    val epochs = binding.etNumber.text.toString().toInt()
                    masterViewModel.modelController.train(epochs)
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
            Toast.makeText(
                binding.root.context,
                R.string.exception_training_in_progress,
                Toast.LENGTH_SHORT
            )
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

    /*******************OTHER************************/

    private fun afterTraining() {
        if (trainingViewModel._saveSamples.value == true) {
            var fileName = binding.etSaveSamplesFileName.text.toString()
            if (fileName.isEmpty()) {
                fileName = "defaultName"
            }
            masterViewModel.modelController.saveSamplesToInternalStg(fileName)
        }
        if (trainingViewModel._clearSamples.value == true) {
            masterViewModel.modelController.clearAllSamples()
            trainingViewModel._loadedSamples.value =
                masterViewModel.modelController.getSamplesSize()
        }

        updateSavedSamples()
    }

    private fun updateSavedSamples() {
        trainingViewModel.setSavedSamplesAdapter(masterViewModel.modelController.listSavedSamplesAdapter())

    }

    /*****************ON DESTROY*********************/
    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }


}
