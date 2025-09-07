package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseLandmarkerHelper(
    val context: Context,
    val listener: PoseLandmarkerListener
) {

    private var poseLandmarker: PoseLandmarker? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        val modelName = "pose_landmarker.task" // Nama file model di assets

        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelName)
        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE) // Kita akan proses gambar per gambar
            .setNumPoses(1) // Hanya mendeteksi satu pose

        try {
            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            listener.onError("Gagal menginisialisasi Pose Landmarker: ${e.message}")
        }
    }

    fun detect(image: Bitmap) {
        if (poseLandmarker == null) {
            setupPoseLandmarker()
        }

        executor.execute {
            val totalTime = SystemClock.uptimeMillis()
            var preProcessTime: Long
            var inferenceTime: Long

            try {
                // Tahap 1: Preprocessing (Konversi Bitmap ke MPImage)
                var startTime = SystemClock.uptimeMillis()
                val mpImage = BitmapImageBuilder(image).build()
                preProcessTime = SystemClock.uptimeMillis() - startTime

                // Tahap 2: Inference (Menjalankan deteksi)
                startTime = SystemClock.uptimeMillis()
                val result = poseLandmarker?.detect(mpImage)
                inferenceTime = SystemClock.uptimeMillis() - startTime

                val finalTotalTime = SystemClock.uptimeMillis() - totalTime

                // Format hasil waktu
                val timingDetails = String.format(
                    Locale.US,
                    "Total: %d ms\nPreprocessing: %d ms\nInference: %d ms",
                    finalTotalTime,
                    preProcessTime,
                    inferenceTime
                )

                if (result != null) {
                    listener.onResults(result, image, timingDetails)
                } else {
                    listener.onError("Hasil deteksi null.")
                }

            } catch (e: Exception) {
                listener.onError("Error saat deteksi: ${e.message}")
            }
        }
    }

    interface PoseLandmarkerListener {
        fun onError(error: String)
        fun onResults(
            result: PoseLandmarkerResult,
            bitmap: Bitmap,
            timingDetails: String
        )
    }
}
