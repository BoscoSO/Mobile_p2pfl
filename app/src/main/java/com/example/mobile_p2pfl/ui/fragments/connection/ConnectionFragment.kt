package com.example.mobile_p2pfl.ui.fragments.connection


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R.anim.pulse2_button
import com.example.mobile_p2pfl.R.anim.pulse_button
import com.example.mobile_p2pfl.common.Values.GRPC_LOG_TAG
import com.example.mobile_p2pfl.databinding.FragmentConnectionBinding

import com.example.mobile_p2pfl.protocol.proto.Node.ResponseMessage
import com.example.mobile_p2pfl.ui.ConnectionState
import com.example.mobile_p2pfl.ui.MasterViewModel

import io.grpc.stub.StreamObserver


class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!


    private lateinit var masterViewModel: MasterViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //= ViewModelProvider(this)[ConnectionViewModel::class.java]
        masterViewModel = ViewModelProvider(requireActivity())[MasterViewModel::class.java]

        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // grpc start
        masterViewModel.connectionState.observe(viewLifecycleOwner) { state ->
            updateConnectionIcon(state)
        }
        // grpc end

        initView()

        return root
    }


    private fun updateConnectionIcon(state: ConnectionState) {
        when (state) {
            ConnectionState.DISCONNECTED -> {
                binding.lyAnimationOn.visibility = View.INVISIBLE
                binding.lyAnimationOff.visibility = View.VISIBLE
            }

            ConnectionState.CONNECTING -> {
                startPulse() // cambiar esto por una animación de carga
            }

            ConnectionState.CONNECTED -> {
                binding.lyAnimationOn.visibility = View.VISIBLE
                binding.lyAnimationOff.visibility = View.INVISIBLE
            }
        }
    }

    private fun initView() {

        binding.btnFetchModel.setOnClickListener {

        }
        binding.btnSendModel.setOnClickListener {

            masterViewModel.grpcClient.sendModel(
                binding.root.context,
                object : StreamObserver<ResponseMessage> {

                    override fun onNext(value: ResponseMessage) {
                        Log.i(GRPC_LOG_TAG, "Async Response: " + value.error)
                    }

                    override fun onError(t: Throwable) {
                        Log.e(GRPC_LOG_TAG, "Error in async call: " + t.message)
                    }

                    override fun onCompleted() {
                        Log.i(GRPC_LOG_TAG, "Async call completed")
                    }
                })
        }

        binding.btnDisconnect.setOnClickListener {
            masterViewModel.disconnect()
        }
        binding.btnConnect.setOnClickListener {
            masterViewModel.initializeConnection()
        }

    }

    private fun startPulse() {
        binding.ivAnimationPulseOff.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse_button)
        )
        binding.ivAnimationPulse2Off.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse2_button)
        )
        binding.ivAnimationPulse.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse_button)
        )
        binding.ivAnimationPulse2.startAnimation(
            AnimationUtils.loadAnimation(binding.root.context, pulse2_button)
        )
    }


    private suspend fun initServer(): Boolean {

        return masterViewModel.grpcClient.connectToServer()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        //masterViewModel.grpcClient.disconnect()
        _binding = null
    }
}