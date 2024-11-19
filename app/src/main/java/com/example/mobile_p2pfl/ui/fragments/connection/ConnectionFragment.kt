package com.example.mobile_p2pfl.ui.fragments.connection


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R.anim.pulse2_button
import com.example.mobile_p2pfl.R.anim.pulse_button
import com.example.mobile_p2pfl.common.GrpcEventListener
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentConnectionBinding

import com.example.mobile_p2pfl.ui.ConnectionState
import com.example.mobile_p2pfl.ui.MasterViewModel
import com.google.protobuf.ByteString

import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!


    private lateinit var masterViewModel: MasterViewModel
    private lateinit var connectionViewModel: ConnectionViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)

        connectionViewModel = ViewModelProvider(requireActivity())[ConnectionViewModel::class.java]
        masterViewModel = ViewModelProvider(requireActivity())[MasterViewModel::class.java]


        masterViewModel.connectionState.observe(viewLifecycleOwner) { state ->
            updateConnectionIcon(state)
        }


        initView()
        initInfoView()
        return binding.root
    }


    private fun updateConnectionIcon(state: ConnectionState) {
        when (state) {
            ConnectionState.DISCONNECTED -> {
                connectionViewModel.setError("Disconected")
                binding.lyAnimationOn.visibility = View.INVISIBLE
                binding.lyAnimationOff.visibility = View.VISIBLE
                startPulseOff()
            }

            ConnectionState.CONNECTING -> {
                connectionViewModel.setInfo("Connecting...")
                startPulseOn()
            }

            ConnectionState.CONNECTED -> {
                connectionViewModel.setInfo("Connected")
                binding.lyAnimationOn.visibility = View.VISIBLE
                binding.lyAnimationOff.visibility = View.INVISIBLE
            }
        }
    }

    private fun initView() {

        connectionViewModel.info.observe(viewLifecycleOwner) { info ->
            binding.tvInfoMsg.text = info

            binding.tvInfoMsg.visibility = View.VISIBLE
            binding.icnStatusGreen.visibility = View.VISIBLE

            binding.tvErrorMsg.visibility = View.INVISIBLE
            binding.icnStatusRed.visibility = View.INVISIBLE
        }
        connectionViewModel.error.observe(viewLifecycleOwner) { error ->
            binding.tvErrorMsg.text = error

            binding.tvErrorMsg.visibility = View.VISIBLE
            binding.icnStatusRed.visibility = View.VISIBLE

            binding.tvInfoMsg.visibility = View.INVISIBLE
            binding.icnStatusGreen.visibility = View.INVISIBLE
        }
        connectionViewModel.sendLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading)
                binding.sendProgressBar.visibility = View.VISIBLE
            else
                binding.sendProgressBar.visibility = View.INVISIBLE
        }
        binding.btnConnect.setOnClickListener {
            startPulseOff()
            masterViewModel.connect(binding.root.context, connectionViewModel.grpcListener)
        }
        binding.btnDisconnect.setOnClickListener {
            if (connectionViewModel.sendLoading.value == true)
                Toast.makeText(
                    binding.root.context,
                    "Wait for the last request to finish",
                    Toast.LENGTH_SHORT
                ).show()
            else masterViewModel.disconnect()
        }
    }


    private fun initInfoView() {

        connectionViewModel.streamError.observe(viewLifecycleOwner){error->
            binding.tvStreamError.text = error
        }
        connectionViewModel.infoInstruction.observe(viewLifecycleOwner) { instruction ->
            binding.tvInfoInstruction.text = instruction
            binding.lyInfoSteps.visibility = View.GONE
            binding.lyInfoResults.visibility = View.GONE
        }
        connectionViewModel.infoStep.observe(viewLifecycleOwner) { step ->
            binding.infoTextProgress.text = step
            binding.lyInfoSteps.visibility = View.VISIBLE
        }
        connectionViewModel.infoResultLbl.observe(viewLifecycleOwner) { resultLbl ->
            binding.tvInfoResultsLbl.text = resultLbl
            binding.lyInfoResults.visibility = View.VISIBLE
        }
        connectionViewModel.infoLoss.observe(viewLifecycleOwner) { loss ->
            binding.tvTrainLossOut.text = loss.toString()
        }
        connectionViewModel.infoAccuracy.observe(viewLifecycleOwner) { accuracy ->
            binding.tvTrainAccOut.text = accuracy.toString()
        }
        connectionViewModel.infoProgress.observe(viewLifecycleOwner) { progress ->
            binding.loadingTraining.progress = progress!!.toInt()
        }
        connectionViewModel.checkIcon.observe(viewLifecycleOwner) { check ->
            if (check) {
                binding.serverProgress.visibility = View.INVISIBLE
                binding.ivInfoCheck.visibility = View.VISIBLE
            } else {
                binding.serverProgress.visibility = View.VISIBLE
                binding.ivInfoCheck.visibility = View.INVISIBLE
            }
        }
        connectionViewModel.waitingInstructions.observe(viewLifecycleOwner) { waiting ->
            if (waiting) {
                binding.tvInfoProcessing.visibility = View.GONE
                binding.tvInfoWaiting.visibility = View.VISIBLE
                binding.tvInfoNone.visibility = View.GONE
            } else {
                binding.tvInfoProcessing.visibility = View.VISIBLE
                binding.tvInfoWaiting.visibility = View.GONE
                binding.tvInfoNone.visibility = View.GONE
            }
        }

        connectionViewModel.startFL.observe(viewLifecycleOwner) { start ->
            if (start) {
                binding.serverProgress.visibility = View.VISIBLE
            }else{
                binding.serverProgress.visibility = View.INVISIBLE
                binding.tvInfoNone.visibility = View.VISIBLE
                binding.tvInfoWaiting.visibility = View.GONE
                binding.tvInfoProcessing.visibility = View.GONE
                binding.lyInfoSteps.visibility = View.GONE
                binding.lyInfoResults.visibility = View.GONE
            }
        }

    }



    private fun startPulseOn() {
        binding.ivAnimationPulse.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse_button)
        )
        binding.ivAnimationPulse2.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse2_button)
        )
    }

    private fun startPulseOff() {
        binding.ivAnimationPulseOff.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse_button)
        )
        binding.ivAnimationPulse2Off.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse2_button)
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}