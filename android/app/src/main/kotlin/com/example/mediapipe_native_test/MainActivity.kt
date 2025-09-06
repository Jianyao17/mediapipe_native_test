package com.example.mediapipe_native_test

import android.graphics.BitmapFactory
import android.net.Uri
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
import java.util.concurrent.Executors

class MainActivity : FlutterActivity()
{
    private val CHANNEL = "com.example.mediapipe_native_test/mediapipe"
    private var faceDetector: FaceDetector? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Setup MediaPipe Face Detector
        setupFaceDetector()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "detectFaces") {
                val imagePath = call.argument<String>("imagePath")
                if (imagePath == null) {
                    result.error("INVALID_ARGUMENT", "Image path is null", null)
                    return@setMethodCallHandler
                }

                // Panggil fungsi deteksi
                val detectionResult = detectFaces(imagePath)
                if (detectionResult != null) {
                    val faceCount = detectionResult.detections().size
                    result.success(faceCount)
                } else {
                    result.error("DETECTION_FAILED", "MediaPipe Face Detector failed.", null)
                }
            } else {
                result.notImplemented()
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
        faceDetector = FaceDetector.createFromOptions(context, options)
    }

    private fun detectFaces(imagePath: String): FaceDetectorResult? {
        val file = File(imagePath)
        if (!file.exists()) {
            return null
        }

        // Buat bitmap dari path file
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        // Konversi bitmap ke MPImage
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Jalankan deteksi
        return faceDetector?.detect(mpImage)
    }
}