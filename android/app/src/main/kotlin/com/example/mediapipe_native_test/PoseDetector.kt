package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.*
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

class PoseDetector(private val context: Context) {
    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_NAME = "pose_landmarker.task"

        // Pose connections for drawing skeleton
        private val POSE_CONNECTIONS = listOf(
            // Face
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),
            Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),
            Pair(9, 10),
            // Arms
            Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21), Pair(17, 19),
            Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20), Pair(16, 22), Pair(18, 20),
            // Body
            Pair(11, 23), Pair(12, 24), Pair(23, 24),
            // Legs
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(29, 31), Pair(27, 31),
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(30, 32), Pair(28, 32)
        )
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private var isProcessing = false

    // Colors for visualization
    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    /**
     * Initialize MediaPipe Pose Landmarker
     */
    suspend fun initialize(useGpu: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing PoseDetector with GPU: $useGpu")

            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)

            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
                Log.d(TAG, "Using GPU acceleration")
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                Log.d(TAG, "Using CPU processing")
            }

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE) // For static images
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "PoseDetector initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PoseDetector", e)
            isInitialized = false
            false
        }
    }

    /**
     * Initialize for real-time processing (video mode)
     */
    suspend fun initializeForRealtime(
        useGpu: Boolean = true,
        onResult: (PoseLandmarkerResult, MPImage, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing PoseDetector for realtime with GPU: $useGpu")

            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)

            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image ->
                    onResult(result, image, System.currentTimeMillis())
                }
                .setErrorListener { error ->
                    Log.e(TAG, "Pose detection error: ${error.message}")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "PoseDetector initialized for realtime successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PoseDetector for realtime", e)
            isInitialized = false
            false
        }
    }

    /**
     * Detect pose from static image
     */
    suspend fun detectPose(bitmap: Bitmap): PoseDetectionResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "PoseDetector not initialized")
            return@withContext PoseDetectionResult.error("PoseDetector not initialized")
        }

        if (isProcessing) {
            Log.w(TAG, "Already processing, skipping frame")
            return@withContext PoseDetectionResult.error("Already processing")
        }

        try {
            isProcessing = true
            Log.d(TAG, "Detecting pose from bitmap: ${bitmap.width}x${bitmap.height}")

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detect(mpImage)

            if (result?.landmarks()?.isNotEmpty() == true) {
                Log.d(TAG, "Pose detected with ${result.landmarks().size} poses")
                val landmarks = result.landmarks()[0]
                val visualizedBitmap = drawPoseOnBitmap(bitmap, landmarks as List<NormalizedLandmark>)

                PoseDetectionResult.success(
                    landmarks = landmarks,
                    confidence = calculateConfidence(landmarks),
                    originalBitmap = bitmap,
                    visualizedBitmap = visualizedBitmap
                )
            } else {
                Log.d(TAG, "No pose detected")
                PoseDetectionResult.success(
                    landmarks = emptyList(),
                    confidence = 0f,
                    originalBitmap = bitmap,
                    visualizedBitmap = bitmap
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting pose", e)
            PoseDetectionResult.error("Detection error: ${e.message}")
        } finally {
            isProcessing = false
        }
    }

    /**
     * Process frame for real-time detection
     */
    fun processVideoFrame(bitmap: Bitmap, timestamp: Long) {
        if (!isInitialized) {
            Log.e(TAG, "PoseDetector not initialized for realtime")
            return
        }

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video frame", e)
        }
    }

    /**
     * Draw pose landmarks and connections on bitmap
     */
    private fun drawPoseOnBitmap(originalBitmap: Bitmap, landmarks: List<NormalizedLandmark>): Bitmap {
        val workingBitmap = originalBitmap.copy(originalBitmap.config!!, true)
        val canvas = Canvas(workingBitmap)

        val scaleX = workingBitmap.width.toFloat()
        val scaleY = workingBitmap.height.toFloat()

        // Draw connections first (behind landmarks)
        drawConnections(canvas, landmarks, scaleX, scaleY)

        // Draw landmarks
        drawLandmarks(canvas, landmarks, scaleX, scaleY)

        // Draw debug info
        drawDebugInfo(canvas, landmarks)

        return workingBitmap
    }

    private fun drawConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>, scaleX: Float, scaleY: Float) {
        for (connection in POSE_CONNECTIONS) {
            val startIdx = connection.first
            val endIdx = connection.second

            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                val startLandmark = landmarks[startIdx]
                val endLandmark = landmarks[endIdx]

                // Only draw if both landmarks are visible enough
                if (startLandmark.visibility().orElse(0f) > 0.3f &&
                    endLandmark.visibility().orElse(0f) > 0.3f) {

                    val startX = startLandmark.x() * scaleX
                    val startY = startLandmark.y() * scaleY
                    val endX = endLandmark.x() * scaleX
                    val endY = endLandmark.y() * scaleY

                    canvas.drawLine(startX, startY, endX, endY, connectionPaint)
                }
            }
        }
    }

    private fun drawLandmarks(canvas: Canvas, landmarks: List<NormalizedLandmark>, scaleX: Float, scaleY: Float) {
        landmarks.forEachIndexed { index, landmark ->
            val visibility = landmark.visibility().orElse(0f)
            if (visibility > 0.3f) {
                val x = landmark.x() * scaleX
                val y = landmark.y() * scaleY

                // Adjust circle size based on visibility
                val radius = 6f + (visibility * 4f)

                // Different colors for different body parts
                landmarkPaint.color = when (index) {
                    in 0..10 -> Color.YELLOW  // Face
                    in 11..22 -> Color.BLUE   // Arms
                    in 23..24 -> Color.MAGENTA // Torso
                    else -> Color.RED         // Legs
                }

                canvas.drawCircle(x, y, radius, landmarkPaint)

                // Draw landmark number for debugging
                canvas.drawText(
                    index.toString(),
                    x + 10f,
                    y - 10f,
                    textPaint
                )
            }
        }
    }

    private fun drawDebugInfo(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        val visibleLandmarks = landmarks.count { it.visibility().orElse(0f) > 0.3f }
        val avgConfidence = landmarks.mapNotNull { it.visibility().orElse(null) }.average()

        val debugText = "Landmarks: $visibleLandmarks/33 | Avg Confidence: ${"%.2f".format(avgConfidence)}"
        canvas.drawText(debugText, 20f, 50f, textPaint)
    }

    private fun calculateConfidence(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.isEmpty()) return 0f
        return landmarks.mapNotNull { it.visibility().orElse(null) }.average().toFloat()
    }

    /**
     * Convert bitmap to byte array for Flutter
     */
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            poseLandmarker?.close()
            poseLandmarker = null
            isInitialized = false
            Log.d(TAG, "PoseDetector cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up PoseDetector", e)
        }
    }
}

/**
 * Result wrapper for pose detection
 */
sealed class PoseDetectionResult {
    data class Success(
        val landmarks: List<NormalizedLandmark>,
        val confidence: Float,
        val originalBitmap: Bitmap,
        val visualizedBitmap: Bitmap
    ) : PoseDetectionResult()

    data class Error(val message: String) : PoseDetectionResult()

    companion object {
        fun success(
            landmarks: List<NormalizedLandmark>,
            confidence: Float,
            originalBitmap: Bitmap,
            visualizedBitmap: Bitmap
        ) = Success(landmarks, confidence, originalBitmap, visualizedBitmap)

        fun error(message: String) = Error(message)
    }
}