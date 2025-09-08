package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.framework.image.MPImage
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*

class PoseDetectorManager(private val context: Context) : MethodChannel.MethodCallHandler {
    companion object {
        private const val TAG = "PoseDetectorManager"
        private const val CHANNEL_NAME = "pose_detector_channel"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var poseDetector: PoseDetector? = null
    private var methodChannel: MethodChannel? = null
    private var isRealtimeMode = false

    fun setMethodChannel(channel: MethodChannel) {
        methodChannel = channel
        methodChannel?.setMethodCallHandler(this)
        Log.d(TAG, "MethodChannel set up")
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "Method called: ${call.method}")

        when (call.method) {
            "initializePoseDetector" -> {
                val useGpu = call.argument<Boolean>("useGpu") ?: true
                initializePoseDetector(useGpu, result)
            }
            "initializeRealtimePoseDetector" -> {
                val useGpu = call.argument<Boolean>("useGpu") ?: true
                initializeRealtimePoseDetector(useGpu, result)
            }
            "detectPoseFromImage" -> {
                val imageBytes = call.argument<ByteArray>("imageBytes")
                if (imageBytes != null) {
                    detectPoseFromImage(imageBytes, result)
                } else {
                    result.error("INVALID_ARGUMENT", "Image bytes cannot be null", null)
                }
            }
            "processVideoFrame" -> {
                val frameBytes = call.argument<ByteArray>("frameBytes")
                val timestamp = call.argument<Long>("timestamp") ?: System.currentTimeMillis()
                if (frameBytes != null) {
                    processVideoFrame(frameBytes, timestamp, result)
                } else {
                    result.error("INVALID_ARGUMENT", "Frame bytes cannot be null", null)
                }
            }
            "cleanup" -> {
                cleanup(result)
            }
            "isInitialized" -> {
                result.success(poseDetector != null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun initializePoseDetector(useGpu: Boolean, result: MethodChannel.Result) {
        scope.launch {
            try {
                poseDetector = PoseDetector(context)
                val success = poseDetector?.initialize(useGpu) ?: false

                if (success) {
                    isRealtimeMode = false
                    result.success(mapOf(
                        "success" to true,
                        "message" to "PoseDetector initialized successfully",
                        "useGpu" to useGpu
                    ))
                    Log.d(TAG, "PoseDetector initialized for static detection")
                } else {
                    result.error("INITIALIZATION_FAILED", "Failed to initialize PoseDetector", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing PoseDetector", e)
                result.error("INITIALIZATION_ERROR", "Error: ${e.message}", null)
            }
        }
    }

    private fun initializeRealtimePoseDetector(useGpu: Boolean, result: MethodChannel.Result) {
        scope.launch {
            try {
                poseDetector = PoseDetector(context)
                val success = poseDetector?.initializeForRealtime(useGpu) { poseResult, image, timestamp ->
                    handleRealtimeResult(poseResult, image, timestamp)
                } ?: false

                if (success) {
                    isRealtimeMode = true
                    result.success(mapOf(
                        "success" to true,
                        "message" to "PoseDetector initialized for realtime",
                        "useGpu" to useGpu
                    ))
                    Log.d(TAG, "PoseDetector initialized for realtime detection")
                } else {
                    result.error("INITIALIZATION_FAILED", "Failed to initialize realtime PoseDetector", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing realtime PoseDetector", e)
                result.error("INITIALIZATION_ERROR", "Error: ${e.message}", null)
            }
        }
    }

    private fun detectPoseFromImage(imageBytes: ByteArray, result: MethodChannel.Result) {
        if (poseDetector == null) {
            result.error("NOT_INITIALIZED", "PoseDetector not initialized", null)
            return
        }

        scope.launch {
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    result.error("INVALID_IMAGE", "Could not decode image bytes", null)
                    return@launch
                }

                Log.d(TAG, "Processing image: ${bitmap.width}x${bitmap.height}")
                val detectionResult = poseDetector?.detectPose(bitmap)

                when (detectionResult) {
                    is PoseDetectionResult.Success -> {
                        val visualizedImageBytes = poseDetector?.bitmapToByteArray(detectionResult.visualizedBitmap)

                        result.success(mapOf(
                            "success" to true,
                            "landmarks" to convertLandmarksToMap(detectionResult.landmarks),
                            "confidence" to detectionResult.confidence,
                            "visualizedImage" to visualizedImageBytes,
                            "landmarkCount" to detectionResult.landmarks.size
                        ))

                        Log.d(TAG, "Pose detection completed successfully: ${detectionResult.landmarks.size} landmarks")
                    }
                    is PoseDetectionResult.Error -> {
                        result.error("DETECTION_ERROR", detectionResult.message, null)
                    }
                    null -> {
                        result.error("UNKNOWN_ERROR", "Detection result is null", null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in pose detection", e)
                result.error("DETECTION_EXCEPTION", "Error: ${e.message}", null)
            }
        }
    }

    private fun processVideoFrame(frameBytes: ByteArray, timestamp: Long, result: MethodChannel.Result) {
        if (poseDetector == null) {
            result.error("NOT_INITIALIZED", "PoseDetector not initialized", null)
            return
        }

        if (!isRealtimeMode) {
            result.error("WRONG_MODE", "PoseDetector not in realtime mode", null)
            return
        }

        try {
            val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
            if (bitmap == null) {
                result.error("INVALID_FRAME", "Could not decode frame bytes", null)
                return
            }

            poseDetector?.processVideoFrame(bitmap, timestamp)
            result.success(mapOf(
                "success" to true,
                "message" to "Frame processed",
                "timestamp" to timestamp
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video frame", e)
            result.error("FRAME_PROCESSING_ERROR", "Error: ${e.message}", null)
        }
    }

    private fun handleRealtimeResult(poseResult: PoseLandmarkerResult, image: MPImage, timestamp: Long) {
        try {
            val landmarks = if (poseResult.landmarks().isNotEmpty()) {
                poseResult.landmarks()[0]
            } else {
                emptyList()
            }

            val confidence = if (landmarks.isNotEmpty()) {
                landmarks.mapNotNull { it.visibility().orElse(null) }.average().toFloat()
            } else {
                0f
            }

            // Send result back to Flutter
            methodChannel?.invokeMethod("onRealtimePoseResult", mapOf(
                "success" to true,
                "landmarks" to convertLandmarksToMap(landmarks),
                "confidence" to confidence,
                "timestamp" to timestamp,
                "landmarkCount" to landmarks.size
            ))

            Log.d(TAG, "Realtime result sent: ${landmarks.size} landmarks, confidence: $confidence")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling realtime result", e)
            methodChannel?.invokeMethod("onRealtimePoseResult", mapOf(
                "success" to false,
                "error" to e.message,
                "timestamp" to timestamp
            ))
        }
    }

    private fun convertLandmarksToMap(landmarks: List<NormalizedLandmark>): List<Map<String, Any?>> {
        return landmarks.mapIndexed { index, landmark ->
            mapOf(
                "index" to index,
                "x" to landmark.x(),
                "y" to landmark.y(),
                "z" to landmark.z(),
                "visibility" to (landmark.visibility().orElse(0f)),
                "presence" to (landmark.presence().orElse(0f))
            )
        }
    }

    private fun cleanup(result: MethodChannel.Result) {
        try {
            poseDetector?.cleanup()
            poseDetector = null
            isRealtimeMode = false

            result.success(mapOf(
                "success" to true,
                "message" to "PoseDetector cleaned up successfully"
            ))
            Log.d(TAG, "PoseDetector cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up", e)
            result.error("CLEANUP_ERROR", "Error: ${e.message}", null)
        }
    }

    fun destroy() {
        scope.cancel()
        poseDetector?.cleanup()
        poseDetector = null
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        Log.d(TAG, "PoseDetectorManager destroyed")
    }
}