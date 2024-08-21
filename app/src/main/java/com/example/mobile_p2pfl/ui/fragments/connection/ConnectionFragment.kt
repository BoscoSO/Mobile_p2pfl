package com.example.mobile_p2pfl.ui.fragments.connection

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.mobile_p2pfl.R.anim.loading
import com.example.mobile_p2pfl.R.anim.pulse2_button
import com.example.mobile_p2pfl.R.anim.pulse_button
import com.example.mobile_p2pfl.ai.inference.Classifier
import com.example.mobile_p2pfl.databinding.FragmentConnectionBinding
import com.example.mobile_p2pfl.protocol.comms.ServerGRPC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var server: ServerGRPC

    private var statusAnimation = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val connectionViewModel =
            ViewModelProvider(this)[ConnectionViewModel::class.java]

        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        val root: View = binding.root



        initView()

        return root
    }

    private fun initView() {
        binding.btnDisconnect.setOnClickListener {
            server.disconnect()
            binding.lyAnimationOn.visibility = View.INVISIBLE
            binding.lyAnimationOff.visibility = View.VISIBLE
        }

        binding.btnConnect.setOnClickListener {
            startPulse()
            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) {
                    initServer()
                }

                if (result) {
                    binding.lyAnimationOn.visibility = View.VISIBLE
                    binding.lyAnimationOff.visibility = View.INVISIBLE

                    //stopPulse()
                    Toast.makeText(this@ConnectionFragment.context, "Conectado", Toast.LENGTH_SHORT).show()
                } else {
                    //stopPulse()
                    Toast.makeText(context, "Error al conectar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPulse() {
        binding.ivAnimationPulseOff.startAnimation(
            AnimationUtils.loadAnimation(
                context,
                pulse_button
            )
        )
        binding.ivAnimationPulse2Off.startAnimation(
            AnimationUtils.loadAnimation(
                context,
                pulse2_button
            )
        )
        binding.ivAnimationPulse.startAnimation(AnimationUtils.loadAnimation(context, pulse_button))
        binding.ivAnimationPulse2.startAnimation(
            AnimationUtils.loadAnimation(
                context,
                pulse2_button
            )
        )
    }

    private fun stopPulse() {
        binding.ivAnimationPulse2Off.clearAnimation()
        binding.ivAnimationPulseOff.clearAnimation()
        binding.ivAnimationPulse2.clearAnimation()
        binding.ivAnimationPulse.clearAnimation()
    }

    private suspend fun initServer(): Boolean {
        server = ServerGRPC()
        return server.connectToServer(Uri.parse("http://172.30.231.18:50051"))
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}