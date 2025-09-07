package com.example.mediapipe_native_test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mediapipe_native_test.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseLandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val bitmap = uriToBitmap(uri)
                    if (bitmap != null) {
                        binding.imageView.setImageBitmap(bitmap)
                        binding.overlayView.clear()
                        poseLandmarkerHelper.detect(bitmap)
                    }
                }
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                binding.imageView.setImageBitmap(bitmap)
                binding.overlayView.clear()
                poseLandmarkerHelper.detect(bitmap)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        poseLandmarkerHelper = PoseLandmarkerHelper(this, this)

        binding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        binding.btnCamera.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openCamera()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    private fun openCamera() {
        cameraLauncher.launch(null)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            binding.textViewTiming.text = error
        }
    }

    override fun onResults(result: PoseLandmarkerResult, bitmap: Bitmap, timingDetails: String) {
        runOnUiThread {
            // Tampilkan hasil waktu
            binding.textViewTiming.text = timingDetails

            // Tampilkan data landmark mentah
            binding.textViewResults.text = formatLandmarkResults(result)

            // Gambar overlay
            binding.overlayView.setResults(result, bitmap.height, bitmap.width)
        }
    }

    private fun formatLandmarkResults(result: PoseLandmarkerResult): String {
        val stringBuilder = StringBuilder()
        var poseIndex = 0
        for (poseLandmarks in result.landmarks()) {
            stringBuilder.append("Pose #${poseIndex}\n")
            var landmarkIndex = 0
            for (landmark in poseLandmarks) {
                stringBuilder.append(
                    String.format(
                        Locale.US,
                        "  Landmark %d: (x=%.2f, y=%.2f, z=%.2f, vis=%.2f)\n",
                        landmarkIndex,
                        landmark.x(),
                        landmark.y(),
                        landmark.z(),
                        landmark.visibility().orElse(0.0f)
                    )
                )
                landmarkIndex++
            }
            poseIndex++
        }
        if (stringBuilder.isEmpty()) {
            return "Tidak ada pose yang terdeteksi."
        }
        return stringBuilder.toString()
    }
}
