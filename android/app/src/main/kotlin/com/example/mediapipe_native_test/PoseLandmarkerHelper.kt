package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    val context: Context,
    private var runningMode: RunningMode = RunningMode.IMAGE,
    var listener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)

            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(runningMode)
                .setNumPoses(1) // Hanya mendeteksi satu pose

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener { result, input ->
                        val finishTimeMs = SystemClock.uptimeMillis()
                        val inferenceTime = finishTimeMs - result.timestampMs()
                        listener?.onResults(ResultBundle(result, inferenceTime, input.height, input.width))
                    }
                    .setErrorListener { error ->
                        listener?.onError(error.message ?: "Unknown error")
                    }
            }

            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            val errorMessage = "Gagal setup Pose Landmarker: ${e.message}"
            Log.e(TAG, errorMessage)
            listener?.onError(errorMessage)
        }
    }

    // Untuk deteksi gambar statis (mode IMAGE)
    fun detectImage(bitmap: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE || poseLandmarker == null) {
            Log.e(TAG, "PoseLandmarker belum siap atau tidak dalam mode IMAGE.")
            return null
        }

        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = poseLandmarker?.detect(mpImage)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        return if (result != null) {
            ResultBundle(result, inferenceTime, bitmap.height, bitmap.width)
        } else {
            null
        }
    }

    // Untuk deteksi dari stream kamera (mode LIVE_STREAM)
    fun detectLiveStream(mpImage: MPImage) {
        if (runningMode != RunningMode.LIVE_STREAM || poseLandmarker == null) {
            Log.e(TAG, "PoseLandmarker belum siap atau tidak dalam mode LIVE_STREAM.")
            return
        }
        val frameTime = SystemClock.uptimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    // Interface untuk mengirim hasil kembali ke MainActivity
    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }

    // Data class untuk membungkus hasil deteksi
    data class ResultBundle(
        val result: PoseLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        private const val MODEL_PATH = "pose_landmarker.task"
    }
}