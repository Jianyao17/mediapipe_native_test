package com.example.mediapipe_native_test

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) : MethodChannel.MethodCallHandler {
    companion object {
        private const val TAG = "CameraHelper"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private var methodChannel: MethodChannel? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isCapturing = false
    private var isAnalyzing = false

    fun setMethodChannel(channel: MethodChannel) {
        methodChannel = channel
        methodChannel?.setMethodCallHandler(this)
        Log.d(TAG, "Camera MethodChannel set up")
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "Camera method called: ${call.method}")

        when (call.method) {
            "checkCameraPermission" -> {
                checkCameraPermission(result)
            }
            "requestCameraPermission" -> {
                requestCameraPermission(result)
            }
            "initializeCamera" -> {
                initializeCamera(result)
            }
            "startCamera" -> {
                startCamera(result)
            }
            "stopCamera" -> {
                stopCamera(result)
            }
            "captureImage" -> {
                captureImage(result)
            }
            "startFrameAnalysis" -> {
                startFrameAnalysis(result)
            }
            "stopFrameAnalysis" -> {
                stopFrameAnalysis(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun checkCameraPermission(result: MethodChannel.Result) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        result.success(mapOf(
            "hasPermission" to hasPermission
        ))
    }

    private fun requestCameraPermission(result: MethodChannel.Result) {
        if (context is MainActivity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            result.success(mapOf("requested" to true))
        } else {
            result.error("CONTEXT_ERROR", "Cannot request permission", null)
        }
    }

    private fun initializeCamera(result: MethodChannel.Result) {
        scope.launch {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProvider = cameraProviderFuture.get()

                result.success(mapOf(
                    "success" to true,
                    "message" to "Camera initialized successfully"
                ))
                Log.d(TAG, "Camera initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
                result.error("CAMERA_INIT_ERROR", "Error: ${e.message}", null)
            }
        }
    }

    private fun startCamera(result: MethodChannel.Result) {
        if (cameraProvider == null) {
            result.error("CAMERA_NOT_INITIALIZED", "Camera not initialized", null)
            return
        }

        try {
            // Stop any existing camera first
            cameraProvider?.unbindAll()

            // Set up image capture use case
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            // Set up image analysis use case
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to camera
            camera = cameraProvider?.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                imageCapture,
                imageAnalyzer
            )

            result.success(mapOf(
                "success" to true,
                "message" to "Camera started successfully"
            ))
            Log.d(TAG, "Camera started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera", e)
            result.error("CAMERA_START_ERROR", "Error: ${e.message}", null)
        }
    }

    private fun stopCamera(result: MethodChannel.Result) {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageCapture = null
            imageAnalyzer = null
            isAnalyzing = false

            result.success(mapOf(
                "success" to true,
                "message" to "Camera stopped successfully"
            ))
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
            result.error("CAMERA_STOP_ERROR", "Error: ${e.message}", null)
        }
    }

    private fun captureImage(result: MethodChannel.Result) {
        if (imageCapture == null) {
            result.error("CAMERA_NOT_READY", "Camera not ready for capture", null)
            return
        }

        if (isCapturing) {
            result.error("ALREADY_CAPTURING", "Already capturing image", null)
            return
        }

        isCapturing = true

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            createImageFile()
        ).build()

        imageCapture?.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    val savedUri = output.savedUri

                    // Convert to bitmap and then to byte array
                    scope.launch {
                        try {
                            val bitmap = BitmapFactory.decodeFile(savedUri?.path)
                            val byteArray = bitmapToByteArray(bitmap)

                            result.success(mapOf(
                                "success" to true,
                                "imageBytes" to byteArray,
                                "path" to savedUri?.path
                            ))
                            Log.d(TAG, "Image captured successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing captured image", e)
                            result.error("IMAGE_PROCESSING_ERROR", "Error: ${e.message}", null)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e(TAG, "Image capture error", exception)
                    result.error("CAPTURE_ERROR", "Error: ${exception.message}", null)
                }
            }
        )
    }

    private fun startFrameAnalysis(result: MethodChannel.Result) {
        if (imageAnalyzer == null) {
            result.error("CAMERA_NOT_READY", "Camera not ready for analysis", null)
            return
        }

        isAnalyzing = true

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (isAnalyzing) {
                processImageProxy(imageProxy)
            }
            imageProxy.close()
        }

        result.success(mapOf(
            "success" to true,
            "message" to "Frame analysis started"
        ))
        Log.d(TAG, "Frame analysis started")
    }

    private fun stopFrameAnalysis(result: MethodChannel.Result) {
        isAnalyzing = false
        imageAnalyzer?.clearAnalyzer()

        result.success(mapOf(
            "success" to true,
            "message" to "Frame analysis stopped"
        ))
        Log.d(TAG, "Frame analysis stopped")
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val byteArray = bitmapToByteArray(bitmap)
            val timestamp = System.currentTimeMillis()

            // Send frame to Flutter
            methodChannel?.invokeMethod("onCameraFrame", mapOf(
                "frameBytes" to byteArray,
                "timestamp" to timestamp,
                "width" to bitmap.width,
                "height" to bitmap.height
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image proxy", e)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    private fun createImageFile(): java.io.File {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        return java.io.File(storageDir, "IMG_${timeStamp}.jpg")
    }

    fun onResume() {
        // Resume camera if needed
    }

    fun onPause() {
        // Pause camera operations
        isAnalyzing = false
    }

    fun cleanup() {
        scope.cancel()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        Log.d(TAG, "Camera cleaned up")
    }
}