package com.example.mediapipe_native_test

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private companion object {
        const val POSE_DETECTOR_CHANNEL = "pose_detector_channel"
        const val CAMERA_CHANNEL = "camera_channel"
    }

    private var poseDetectorManager: PoseDetectorManager? = null
    private var cameraHelper: CameraHelper? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Setup PoseDetector channel
        val poseDetectorChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, POSE_DETECTOR_CHANNEL)
        poseDetectorManager = PoseDetectorManager(this)
        poseDetectorManager?.setMethodChannel(poseDetectorChannel)

        // Setup Camera channel
        val cameraChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CAMERA_CHANNEL)
        cameraHelper = CameraHelper(this)
        cameraHelper?.setMethodChannel(cameraChannel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        poseDetectorManager?.destroy()
        cameraHelper?.cleanup()
        poseDetectorManager = null
        cameraHelper = null
    }

    override fun onResume() {
        super.onResume()
        cameraHelper?.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraHelper?.onPause()
    }
}

