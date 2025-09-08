/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.mediapipe_native_test.posedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mediapipe_native_test.R
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    lateinit var backgroundExecutor: ExecutorService
    private lateinit var overlayView: OverlayView
    private lateinit var fpsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        overlayView = findViewById(R.id.overlay)
        fpsText = findViewById(R.id.fps_text)

        if (allPermissionsGranted()) {
            // Permissions are granted, proceed with camera setup
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Initialize the PoseLandmarkerHelper and background executor once the permissions are granted
        if (allPermissionsGranted()) {
            backgroundExecutor = Executors.newSingleThreadExecutor()
            startPoseLandmarker()
        }
    }

    private fun startPoseLandmarker() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            minPoseDetectionConfidence = 0.5f,
            minPoseTrackingConfidence = 0.5f,
            minPosePresenceConfidence = 0.5f,
            currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
            poseLandmarkerListener = this
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startPoseLandmarker()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            fpsText.text = "FPS: ${resultBundle.inferenceTime}"
            overlayView.setResults(
                resultBundle.results.first(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )
        }
    }

    override fun onError(error: String) {
        // Handle error
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE,
            TimeUnit.NANOSECONDS
        )
    }
}
