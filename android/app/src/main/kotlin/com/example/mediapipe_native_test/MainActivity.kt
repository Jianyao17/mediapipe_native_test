package com.example.mediapipe_native_test

import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.NonNull
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.mediapipe_native_test/mediapipe"
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var backgroundExecutor: ExecutorService

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize the helper in the background
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(context)

            // Set up the MethodChannel on the main thread
            runOnUiThread {
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
                    if (call.method == "detectPose") {
                        val imageBytes = call.argument<ByteArray>("imageBytes")
                        if (imageBytes == null) {
                            result.error("INVALID_ARGUMENT", "Image bytes are null", null)
                            return@setMethodCallHandler
                        }

                        // Offload detection to the background thread
                        backgroundExecutor.execute {
                            try {
                                val detectionResult = poseLandmarkerHelper.detect(imageBytes)
                                runOnUiThread {
                                    result.success(detectionResult)
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Log.e("PoseDetection", "Error during pose detection: ${e.message}")
                                    result.error("DETECTION_FAILED", "Error during pose detection: ${e.message}", null)
                                }
                            }
                        }
                    } else {
                        result.notImplemented()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
    }
}