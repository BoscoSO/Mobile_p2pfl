package com.example.mobile_p2pfl.ui.fragments.connection

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.mobile_p2pfl.R.anim.loading
import com.example.mobile_p2pfl.databinding.FragmentConnectionBinding


class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val connectionViewModel =
            ViewModelProvider(this).get(ConnectionViewModel::class.java)

        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.icnStatusLoading.startAnimation(AnimationUtils.loadAnimation(context, loading))




        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}