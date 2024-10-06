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
    private val loadingListener = object : GrpcEventListener {
        override fun onLoadingStarted() {
            connectionViewModel.setInfo("Waiting for response...")
            connectionViewModel.startLoading()
        }

        override fun onLoadingFinished() {
            connectionViewModel.setInfo("Done")
            connectionViewModel.stopLoading()
        }

        override fun onError(message: String) {
            connectionViewModel.setError(message)
            connectionViewModel.stopLoading()
            if(message == "END")
                masterViewModel._connectionState.postValue(ConnectionState.DISCONNECTED)
        }
    }


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

        initView()

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
                startPulseOn() // cambiar esto por una animación de carga
            }

            ConnectionState.CONNECTED -> {
                connectionViewModel.setInfo("Connected")
                binding.lyAnimationOn.visibility = View.VISIBLE
                binding.lyAnimationOff.visibility = View.INVISIBLE
            }
        }
    }

    private fun initView() {

        binding.btnFetchModel.setOnClickListener {
            if (connectionViewModel.sendLoading.value == true)
                Toast.makeText(
                    binding.root.context,
                    "Wait for the last request to finish",
                    Toast.LENGTH_SHORT
                ).show()
            else  masterViewModel.initModel()

//                CoroutineScope(Dispatchers.IO).launch {
//                masterViewModel.grpcClient.getModel(binding.root.context)
//                //masterViewModel.grpcClient.fetchModel(binding.root.context)
//                //masterViewModel.initializeModelController(binding.root.context,2)
//                }

        }
        binding.btnSendModel.setOnClickListener {
            if (connectionViewModel.sendLoading.value == true)
                Toast.makeText(
                    binding.root.context,
                    "Wait for the last request to finish",
                    Toast.LENGTH_SHORT
                ).show()
            else masterViewModel.sendWeights()

//                CoroutineScope(Dispatchers.IO).launch {
//                masterViewModel.grpcClient.sendWeights(binding.root.context)
////                masterViewModel.grpcClient.sendModel(
////                    binding.root.context,
////                    object : StreamObserver<ResponseMessage> {
////
////                        override fun onNext(value: ResponseMessage) {
////                            Log.i(GRPC_LOG_TAG, "Async Response: " + value.error)
////                        }
////
////                        override fun onError(t: Throwable) {
////                            Log.e(GRPC_LOG_TAG, "Error in async call: " + t.message)
////                        }
////
////                        override fun onCompleted() {
////                            Log.i(GRPC_LOG_TAG, "Async call completed")
////                        }
////                    })
//                }
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
        binding.btnConnect.setOnClickListener {
            startPulseOff()
            masterViewModel.connect(loadingListener)
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