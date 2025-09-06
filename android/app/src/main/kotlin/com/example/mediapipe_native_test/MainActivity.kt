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

class MainActivity : FlutterActivity()
{
    private val CHANNEL = "com.example.mediapipe_native_test/mediapipe"
    private var faceDetector: FaceDetector? = null
    private lateinit var backgroundExecutor: ExecutorService

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize a background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Offload the setup to a background thread
        backgroundExecutor.execute {
            try {
                setupFaceDetector()

                // Setup MethodChannel only after the detector is initialized
                runOnUiThread {
                    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
                        if (call.method == "detectFaces") {
                            val imagePath = call.argument<String>("imagePath")
                            if (imagePath == null) {
                                result.error("INVALID_ARGUMENT", "Image path is null", null)
                                return@setMethodCallHandler
                            }

                            // The detection itself should also be offloaded
                            backgroundExecutor.execute {
                                val detectionResult = detectFaces(imagePath)
                                runOnUiThread {
                                    if (detectionResult != null) {
                                        val faceCount = detectionResult.detections().size
                                        result.success(faceCount)
                                    } else {
                                        result.error("DETECTION_FAILED", "MediaPipe Face Detector failed to detect faces.", null)
                                    }
                                }
                            }
                        } else {
                            result.notImplemented()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaPipe", "Error setting up Face Detector: ${e.message}")
                // Optionally, you can communicate this error back to Flutter
            }
        }
    }

    private fun setupFaceDetector()
    {
        val modelName = "blaze_face.tflite"
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelName)

        val optionsBuilder =
            FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinDetectionConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)

        val options = optionsBuilder.build()
        // This can throw an exception if the model file is not found
        faceDetector = FaceDetector.createFromOptions(context, options)
    }

    private fun detectFaces(imagePath: String): FaceDetectorResult? {
        if (faceDetector == null) {
            Log.e("MediaPipe", "Face detector is not initialized.")
            return null
        }

        val file = File(imagePath)
        if (!file.exists()) {
            Log.e("MediaPipe", "Image file not found at path: $imagePath")
            return null
        }

        return try {
            // Create bitmap from path file
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            // Convert bitmap to MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Run detection
            faceDetector?.detect(mpImage)
        } catch (e: Exception) {
            Log.e("MediaPipe", "Error during face detection: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down the executor service
        backgroundExecutor.shutdown()
    }
}