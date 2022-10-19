package com.example.cas

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.cas.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var camera: androidx.camera.core.Camera
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String?>
    private var shortAnimationDuration: Int = 0
    private var slider: Boolean = true
    private lateinit var preview: Preview

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.

            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                val builder = AlertDialog.Builder(this)
                    .setTitle("Alert!")
                    .setMessage("Because you denied camera permission so we degrade feature")
                    .setPositiveButton("ok") { dialog, _ ->
                        dialog.cancel()
                    }

                builder.create().show()
            }
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.

            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected. In this UI,
                // include a "cancel" or "no thanks" button that allows the user to
                // continue using your app without granting the permission.
                Snackbar.make(
                    binding.root.rootView,
                    "For access restricted content we need camera",
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction("ok") {
                        requestPermissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                    .show()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
        binding.previewView.post {
            startCamera()

        }

        binding.apply {
            expo.setOnClickListener {
                when (slider) {
                    true -> {
                        sliderExposure.visibility = View.VISIBLE
                        slider = false
                    }
                    false -> {
                        sliderExposure.visibility = View.GONE
                        slider = true
                    }
                }

            }

        }

        binding.cancel.visibility = View.GONE
        visibleGo()
        shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
        binding.logo.setOnClickListener {
            crossFade()
        }

        binding.cancel.setOnClickListener {
            crossFade2()
        }
        binding.cast.setOnClickListener {
            try {
                val intent = Intent("android.settings.CAST_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                Log.d("cast", "cast error")
            }
        }
        binding.share.setOnClickListener {
            intentShare()
        }

    }

    @SuppressLint("SuspiciousIndentation")
    private fun startCamera() {
        lifecycleScope.launch {
            val cameraProvider: ProcessCameraProvider =
                ProcessCameraProvider.getInstance(application).await()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this@MainActivity, cameraSelector, preview
                )
                flashLight()
                getZoom()
                getExposure()


            } catch (exc: Exception) {
                Log.e("error", "Use case binding failed", exc)
            }
        }
    }

    private fun flashLight() = with(binding) {
        camera.apply {
            if (cameraInfo.hasFlashUnit()) {
                flash.setOnClickListener {
                    cameraControl.enableTorch(cameraInfo.torchState.value == TorchState.OFF)
                }
            }
            cameraInfo.torchState.observe({ lifecycle }) { torchState ->
                if (torchState == TorchState.OFF) {
                    flash.setImageResource(R.drawable.ic_baseline_flash_on_24)
                } else {
                    flash.setImageResource(R.drawable.ic_baseline_flash_off_24)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun getZoom() {
        val ln = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 0F
                val delta = detector?.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta!!)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this@MainActivity, ln)
        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
        camera.cameraInfo.zoomState.observe({ lifecycle }) { zoom ->
            binding.zoom.text = String.format("x%.02f", zoom.zoomRatio)
        }
    }

    private fun getExposure() {
        camera.cameraInfo.exposureState.run {
            val lower = exposureCompensationRange.lower
            val upper = exposureCompensationRange.upper
            binding.sliderExposure.run {
                valueFrom = lower.toFloat()
                valueTo = upper.toFloat()
                stepSize = 1f
                value = exposureCompensationIndex.toFloat()

                addOnChangeListener { _, value, _ ->
                    camera.cameraControl.setExposureCompensationIndex(value.toInt())
                }
            }
        }
    }

    private fun crossFade() {
        binding.cancel.apply {
            alpha = 0f
            visibility = View.VISIBLE
            visible()
            animate()
                .alpha(1f)
                .setDuration(shortAnimationDuration.toLong())
                .setListener(null)
        }

        binding.logo.animate()
            ?.alpha(0f)
            ?.setDuration(shortAnimationDuration.toLong())
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.logo.visibility = View.GONE
                }
            })
    }

    private fun crossFade2() {
        binding.logo.apply {
            alpha = 0f
            visibility = View.VISIBLE
            visibleGo()
            animate()
                .alpha(1f)
                .setDuration(shortAnimationDuration.toLong())
                .setListener(null)
        }

        binding.cancel.animate()
            ?.alpha(0f)
            ?.setDuration(shortAnimationDuration.toLong())
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.cancel.visibility = View.GONE
                }
            })
    }

    private fun visible() {
        binding.apply {
            flash.visibility = View.VISIBLE
            expo.visibility = View.VISIBLE
            cast.visibility = View.VISIBLE
            share.visibility = View.VISIBLE

        }
    }

    private fun visibleGo() {
        binding.apply {
            flash.visibility = View.GONE
            expo.visibility = View.GONE
            cast.visibility = View.GONE
            share.visibility = View.GONE
        }
    }

    private fun intentShare() {
        val msg = "For download Cassata app just click below the link:\n " +
                "https://www.google.com"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, msg)
            type = "text/plain"
        }

        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            Log.d("intent", "failed:$e")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preview.setSurfaceProvider(null)

    }
}