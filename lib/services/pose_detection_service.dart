import 'package:flutter/services.dart';

class PoseLandmark {
  final int index;
  final double x;
  final double y;
  final double z;
  final double visibility;
  final double presence;

  PoseLandmark({
    required this.index,
    required this.x,
    required this.y,
    required this.z,
    required this.visibility,
    required this.presence,
  });

  factory PoseLandmark.fromMap(Map<String, dynamic> map) {
    return PoseLandmark(
      index: map['index'] ?? 0,
      x: (map['x'] ?? 0.0).toDouble(),
      y: (map['y'] ?? 0.0).toDouble(),
      z: (map['z'] ?? 0.0).toDouble(),
      visibility: (map['visibility'] ?? 0.0).toDouble(),
      presence: (map['presence'] ?? 0.0).toDouble(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'index': index,
      'x': x,
      'y': y,
      'z': z,
      'visibility': visibility,
      'presence': presence,
    };
  }

  @override
  String toString() {
    return 'PoseLandmark(index: $index, x: $x, y: $y, z: $z, visibility: $visibility, presence: $presence)';
  }
}

class PoseDetectionResult {
  final bool success;
  final List<PoseLandmark> landmarks;
  final double confidence;
  final Uint8List? visualizedImage;
  final int landmarkCount;
  final String? error;

  PoseDetectionResult({
    required this.success,
    this.landmarks = const [],
    this.confidence = 0.0,
    this.visualizedImage,
    this.landmarkCount = 0,
    this.error,
  });

  factory PoseDetectionResult.fromMap(Map<String, dynamic> map) {
    List<PoseLandmark> landmarks = [];
    if (map['landmarks'] != null) {
      landmarks = (map['landmarks'] as List<dynamic>)
          .map((landmark) => PoseLandmark.fromMap(landmark))
          .toList();
    }

    return PoseDetectionResult(
      success: map['success'] ?? false,
      landmarks: landmarks,
      confidence: (map['confidence'] ?? 0.0).toDouble(),
      visualizedImage: map['visualizedImage'],
      landmarkCount: map['landmarkCount'] ?? 0,
      error: map['error'],
    );
  }

  @override
  String toString() {
    return 'PoseDetectionResult(success: $success, landmarkCount: $landmarkCount, confidence: $confidence)';
  }
}

class PoseDetectionService {
  static const MethodChannel _poseChannel = MethodChannel('pose_detector_channel');
  static const MethodChannel _cameraChannel = MethodChannel('camera_channel');

  // Callback for realtime results
  Function(PoseDetectionResult)? _onRealtimeResult;

  PoseDetectionService() {
    _setupMethodCallHandler();
  }

  void _setupMethodCallHandler() {
    _poseChannel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onRealtimePoseResult':
          if (_onRealtimeResult != null) {
            final result = PoseDetectionResult.fromMap(Map<String, dynamic>.from(call.arguments));
            _onRealtimeResult!(result);
          }
          break;
      }
    });
  }

  void setCameraMethodCallHandler(Future<dynamic> Function(MethodCall call) handler) {
    _cameraChannel.setMethodCallHandler(handler);
  }

  // Initialize pose detector for static images
  Future<Map<String, dynamic>> initializePoseDetector({bool useGpu = true}) async {
    try {
      final result = await _poseChannel.invokeMethod('initializePoseDetector', {
        'useGpu': useGpu,
      });
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      return {
        'success': false,
        'error': 'Platform error: ${e.message}',
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Unexpected error: $e',
      };
    }
  }

  // Initialize pose detector for realtime processing
  Future<Map<String, dynamic>> initializeRealtimePoseDetector({
    bool useGpu = true,
    Function(PoseDetectionResult)? onResult,
  }) async {
    try {
      _onRealtimeResult = onResult;

      final result = await _poseChannel.invokeMethod('initializeRealtimePoseDetector', {
        'useGpu': useGpu,
      });
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      return {
        'success': false,
        'error': 'Platform error: ${e.message}',
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Unexpected error: $e',
      };
    }
  }

  // Detect pose from image bytes
  Future<PoseDetectionResult> detectPoseFromImage(Uint8List imageBytes) async {
    try {
      final result = await _poseChannel.invokeMethod('detectPoseFromImage', {
        'imageBytes': imageBytes,
      });
      return PoseDetectionResult.fromMap(Map<String, dynamic>.from(result));
    } on PlatformException catch (e) {
      return PoseDetectionResult(
        success: false,
        error: 'Platform error: ${e.message}',
      );
    } catch (e) {
      return PoseDetectionResult(
        success: false,
        error: 'Unexpected error: $e',
      );
    }
  }

  // Process video frame
  Future<Map<String, dynamic>> processVideoFrame(Uint8List frameBytes, int timestamp) async {
    try {
      final result = await _poseChannel.invokeMethod('processVideoFrame', {
        'frameBytes': frameBytes,
        'timestamp': timestamp,
      });
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      return {
        'success': false,
        'error': 'Platform error: ${e.message}',
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Unexpected error: $e',
      };
    }
  }

  // Check if detector is initialized
  Future<bool> isInitialized() async {
    try {
      return await _poseChannel.invokeMethod('isInitialized');
    } catch (e) {
      return false;
    }
  }

  // Cleanup resources
  Future<Map<String, dynamic>> cleanup() async {
    try {
      _onRealtimeResult = null;
      final result = await _poseChannel.invokeMethod('cleanup');
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      return {
        'success': false,
        'error': 'Platform error: ${e.message}',
      };
    } catch (e) {
      return {
        'success': false,
        'error': 'Unexpected error: $e',
      };
    }
  }

  // Camera methods
  Future<Map<String, dynamic>> checkCameraPermission() async {
    try {
      final result = await _cameraChannel.invokeMethod('checkCameraPermission');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'hasPermission': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> requestCameraPermission() async {
    try {
      final result = await _cameraChannel.invokeMethod('requestCameraPermission');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'requested': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> initializeCamera() async {
    try {
      final result = await _cameraChannel.invokeMethod('initializeCamera');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> startCamera() async {
    try {
      final result = await _cameraChannel.invokeMethod('startCamera');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> stopCamera() async {
    try {
      final result = await _cameraChannel.invokeMethod('stopCamera');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> captureImage() async {
    try {
      final result = await _cameraChannel.invokeMethod('captureImage');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> startFrameAnalysis() async {
    try {
      final result = await _cameraChannel.invokeMethod('startFrameAnalysis');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  Future<Map<String, dynamic>> stopFrameAnalysis() async {
    try {
      final result = await _cameraChannel.invokeMethod('stopFrameAnalysis');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      return {'success': false, 'error': e.toString()};
    }
  }

  void dispose() {
    cleanup();
    _onRealtimeResult = null;
  }
}