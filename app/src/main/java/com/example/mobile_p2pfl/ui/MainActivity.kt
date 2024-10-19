package com.example.mobile_p2pfl.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.mobile_p2pfl.R
import com.example.mobile_p2pfl.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var masterViewModel: MasterViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_connection, R.id.navigation_inference, R.id.navigation_training
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)



        init()
    }

    private fun init(){

        masterViewModel = ViewModelProvider(this)[MasterViewModel::class.java]

        masterViewModel.initializeConnection(this)//, ViewModelProvider(this)[ConnectionViewModel::class.java])

        masterViewModel.initializeModelController(binding.root.context,2)

        copySamplesSetToInternal()
    }


    private fun copySamplesSetToInternal() {

        val directory = File(filesDir, "saved_samples")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val assetName = "samples_set.dat"
        val file = File(directory, "samples_set.dat")


        if (!file.exists()) {
            try {
                assets.open(assetName).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("FileOperation", "samples_set.dat copiado exitosamente a la memoria interna")
            } catch (e: Exception) {
                Log.e("FileOperation", "Error al copiar samples_set.dat: ${e.message}")
            }
        } else {
            Log.d("FileOperation", "samples_set.dat ya existe en la memoria interna")
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        Log.d("MainActivity", "onDestroy se ha ejecutado")
    }
}