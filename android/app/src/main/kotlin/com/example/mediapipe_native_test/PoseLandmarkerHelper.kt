package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.scale
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PoseLandmarkerHelper(val context: Context) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            // Log the error or handle it as needed
            Log.e("PoseLandmarkerHelper", "Error setting up Pose Landmarker: ${e.message}")
        }
    }

    fun detect(imageBytes: ByteArray): Map<String, Any?> {
        if (poseLandmarker == null) {
            return mapOf("error" to "Pose Landmarker is not initialized.")
        }

        val totalTime = SystemClock.uptimeMillis()

        // Convert byte array to Bitmap
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap == null) {
            return mapOf("error" to "Failed to decode image.")
        }

        // Resize the bitmap if its width is greater than 400px
        val imageWidth = bitmap.width
        if (imageWidth > 400) {
            val scaleFactor = 400f / imageWidth
            val newHeight = (bitmap.height * scaleFactor).toInt()
            bitmap = bitmap.scale(400, newHeight, false)
        }

        // Tahap 1: Preprocessing
        val preProcessStartTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val preProcessTime = SystemClock.uptimeMillis() - preProcessStartTime

        // Tahap 2: Inference
        val inferenceStartTime = SystemClock.uptimeMillis()
        val result = poseLandmarker?.detect(mpImage)
        val inferenceTime = SystemClock.uptimeMillis() - inferenceStartTime

        val finalTotalTime = SystemClock.uptimeMillis() - totalTime

        // Format results to be sent back to Flutter
        val landmarksData = result?.landmarks()?.map { poseLandmarks ->
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
            "timing" to mapOf(
                "total" to finalTotalTime,
                "preprocessing" to preProcessTime,
                "inference" to inferenceTime
            ),
            "image" to mapOf(
                "width" to bitmap.width,
                "height" to bitmap.height
            )
        )
    }
}
