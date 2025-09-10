package com.example.mediapipe_native_test

import android.graphics.BitmapFactory
import androidx.annotation.NonNull
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log

class MainActivity : FlutterActivity() {
    private val methodChannelName = "com.example.mediapipe_native_test/method"
    private val eventChannelName = "com.example.mediapipe_native_test/event"
    private var eventSink: EventChannel.EventSink? = null

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var backgroundExecutor: ExecutorService

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Setup EventChannel untuk mengirim hasil ke Flutter
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannelName).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    Log.d("MainActivity", "EventChannel onListen: Memulai stream.")
                    eventSink = events
                    // Setup helper HANYA SEKALI saat stream dimulai
                    setupLiveStreamDetector()
                }

                override fun onCancel(arguments: Any?) {
                    Log.d("MainActivity", "EventChannel onCancel: Menghentikan stream.")
                    eventSink = null
                }
            }
        )

        // Setup MethodChannel untuk menerima frame dari Flutter (DI LUAR EventChannel)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "detectFromStream" -> {
                    val imageBytes = call.argument<ByteArray>("imageBytes")
                    if (imageBytes != null && ::poseLandmarkerHelper.isInitialized) {
                        // Jalankan deteksi di background
                        backgroundExecutor.execute {
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            if (bitmap != null) {
                                val mpImage = BitmapImageBuilder(bitmap).build()
                                poseLandmarkerHelper.detectLiveStream(mpImage)
                                bitmap.recycle() // Bebaskan memori bitmap
                            } else {
                                Log.e("MainActivity", "Gagal decode byte array menjadi bitmap.")
                            }
                        }
                    } else {
                        Log.w("MainActivity", "Menerima frame, tetapi PoseLandmarkerHelper belum siap.")
                    }
                }
                // Anda bisa tambahkan method lain seperti "detectPoseFromImage" di sini
                else -> result.notImplemented()
            }
        }
    }

    private fun setupLiveStreamDetector() {
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                listener = object : PoseLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String) {
                        Log.e("MainActivity", "PoseLandmarkerHelper Error: $error")
                        runOnUiThread {
                            eventSink?.error("NATIVE_ERROR", error, null)
                        }
                    }

                    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                        val resultMap = formatResultsToMap(resultBundle)
                        // Kirim hasil ke Flutter melalui EventChannel
                        runOnUiThread {
                            eventSink?.success(resultMap)
                        }
                    }
                }
            )
            Log.d("MainActivity", "PoseLandmarkerHelper untuk LIVE_STREAM berhasil di-setup.")
        }
    }

    private fun formatResultsToMap(bundle: PoseLandmarkerHelper.ResultBundle): Map<String, Any?> {
        val landmarksData = bundle.result.landmarks().map { poseLandmarks ->
            poseLandmarks.map { landmark ->
                mapOf(
                    "x" to landmark.x(),
                    "y" to landmark.y(),
                    "z" to landmark.z(),
                    "visibility" to landmark.visibility().orElse(0.0f)
                )
            }
        }
        return mapOf(
            "landmarks" to landmarksData,
            "inferenceTime" to bundle.inferenceTime,
            "imageHeight" to bundle.inputImageHeight,
            "imageWidth" to bundle.inputImageWidth
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}