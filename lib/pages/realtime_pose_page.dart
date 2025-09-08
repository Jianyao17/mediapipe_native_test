import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/pose_detection_service.dart';

class RealtimePosePage extends StatefulWidget {
  const RealtimePosePage({Key? key}) : super(key: key);

  @override
  State<RealtimePosePage> createState() => _RealtimePosePageState();
}

class _RealtimePosePageState extends State<RealtimePosePage> {
  final PoseDetectionService _poseService = PoseDetectionService();

  bool _isInitialized = false;
  bool _isCameraStarted = false;
  bool _isAnalyzing = false;
  bool _hasPermission = false;
  String _statusMessage = 'Not initialized';

  PoseDetectionResult? _latestResult;
  int _frameCount = 0;
  int _detectionCount = 0;
  DateTime? _lastFrameTime;
  double _fps = 0.0;

  Timer? _fpsTimer;

  @override
  void initState() {
    super.initState();
    _checkPermissionAndInitialize();
    _startFpsCalculation();
  }

  @override
  void dispose() {
    _fpsTimer?.cancel();
    _stopCamera();
    _poseService.dispose();
    super.dispose();
  }

  void _startFpsCalculation() {
    _fpsTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      setState(() {
        _fps = _frameCount.toDouble();
        _frameCount = 0;
      });
    });
  }

  Future<void> _checkPermissionAndInitialize() async {
    setState(() {
      _statusMessage = 'Checking camera permission...';
    });

    final permissionResult = await _poseService.checkCameraPermission();
    final hasPermission = permissionResult['hasPermission'] ?? false;

    if (!hasPermission) {
      setState(() {
        _statusMessage = 'Requesting camera permission...';
      });

      await _poseService.requestCameraPermission();

      // Check again after requesting
      final newPermissionResult = await _poseService.checkCameraPermission();
      _hasPermission = newPermissionResult['hasPermission'] ?? false;
    } else {
      _hasPermission = true;
    }

    if (_hasPermission) {
      await _initializeServices();
    } else {
      setState(() {
        _statusMessage = 'Camera permission denied';
      });
    }
  }

  Future<void> _initializeServices() async {
    setState(() {
      _statusMessage = 'Initializing services...';
    });

    // Initialize pose detector for realtime
    final poseResult = await _poseService.initializeRealtimePoseDetector(
      useGpu: true,
      onResult: _onPoseResult,
    );

    // Initialize camera
    final cameraResult = await _poseService.initializeCamera();

    setState(() {
      _isInitialized = (poseResult['success'] ?? false) && (cameraResult['success'] ?? false);
      _statusMessage = _isInitialized
          ? 'Services initialized successfully'
          : 'Failed to initialize services';
    });
  }

  void _onPoseResult(PoseDetectionResult result) {
    setState(() {
      _latestResult = result;
      if (result.success && result.landmarkCount > 0) {
        _detectionCount++;
      }
      _frameCount++;
    });
  }

  Future<void> _startCamera() async {
    if (!_isInitialized || _isCameraStarted) return;

    setState(() {
      _statusMessage = 'Starting camera...';
    });

    final result = await _poseService.startCamera();

    if (result['success'] ?? false) {
      await _poseService.startFrameAnalysis();

      setState(() {
        _isCameraStarted = true;
        _isAnalyzing = true;
        _statusMessage = 'Camera started - analyzing frames';
      });

      // Setup camera frame handler
      _poseService.setCameraMethodCallHandler((call) async {
        if (call.method == 'onCameraFrame' && _isAnalyzing) {
          final frameBytes = call.arguments['frameBytes'] as Uint8List;
          final timestamp = call.arguments['timestamp'] as int;
          await _poseService.processVideoFrame(frameBytes, timestamp);
        }
      });
    } else {
      setState(() {
        _statusMessage = 'Failed to start camera: ${result['error']}';
      });
    }
  }

  Future<void> _stopCamera() async {
    if (!_isCameraStarted) return;

    setState(() {
      _statusMessage = 'Stopping camera...';
      _isAnalyzing = false;
    });

    await _poseService.stopFrameAnalysis();
    await _poseService.stopCamera();

    setState(() {
      _isCameraStarted = false;
      _statusMessage = 'Camera stopped';
      _latestResult = null;
    });
  }

  Future<void> _toggleCamera() async {
    if (_isCameraStarted) {
      await _stopCamera();
    } else {
      await _startCamera();
    }
  }

  Widget _buildStatusPanel() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _isInitialized ? Icons.check_circle : Icons.error,
                  color: _isInitialized ? Colors.green : Colors.red,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _statusMessage,
                    style: TextStyle(
                      fontWeight: FontWeight.w500,
                      color: _isInitialized ? Colors.green.shade800 : Colors.red.shade800,
                    ),
                  ),
                ),
              ],
            ),
            if (_isCameraStarted) ...[
              const SizedBox(height: 12),
              const Divider(),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Column(
                    children: [
                      Text(
                        '${_fps.toStringAsFixed(1)}',
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      const Text('FPS', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                  Column(
                    children: [
                      Text(
                        '$_detectionCount',
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      const Text('Detections', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                  Column(
                    children: [
                      Text(
                        _latestResult?.landmarkCount.toString() ?? '0',
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      const Text('Landmarks', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                  Column(
                    children: [
                      Text(
                        _latestResult?.success == true
                            ? '${(_latestResult!.confidence * 100).toStringAsFixed(1)}%'
                            : '0%',
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      const Text('Confidence', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildLandmarksList() {
    if (_latestResult == null || !_latestResult!.success || _latestResult!.landmarks.isEmpty) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Center(
            child: Text('No landmarks detected'),
          ),
        ),
      );
    }

    final visibleLandmarks = _latestResult!.landmarks
        .where((landmark) => landmark.visibility > 0.3)
        .toList();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Live Landmarks (${visibleLandmarks.length})',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 200,
              child: ListView.builder(
                itemCount: visibleLandmarks.length,
                itemBuilder: (context, index) {
                  final landmark = visibleLandmarks[index];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 1),
                    child: Text(
                      '${landmark.index}: (${landmark.x.toStringAsFixed(3)}, ${landmark.y.toStringAsFixed(3)}) - ${(landmark.visibility * 100).toStringAsFixed(1)}%',
                      style: const TextStyle(
                        fontSize: 11,
                        fontFamily: 'monospace',
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Realtime Pose Detection'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          if (_isInitialized)
            IconButton(
              onPressed: _toggleCamera,
              icon: Icon(_isCameraStarted ? Icons.stop : Icons.play_arrow),
              tooltip: _isCameraStarted ? 'Stop Camera' : 'Start Camera',
            ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status panel
            _buildStatusPanel(),

            const SizedBox(height: 16),

            // Control button
            if (_hasPermission)
              ElevatedButton.icon(
                onPressed: _isInitialized ? _toggleCamera : null,
                icon: Icon(_isCameraStarted ? Icons.stop : Icons.play_arrow),
                label: Text(_isCameraStarted ? 'Stop Detection' : 'Start Detection'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: _isCameraStarted ? Colors.red : Colors.green,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
              )
            else
              ElevatedButton.icon(
                onPressed: _checkPermissionAndInitialize,
                icon: const Icon(Icons.refresh),
                label: const Text('Request Permission'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.orange,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
              ),

            const SizedBox(height: 16),

            // Live landmarks
            Expanded(
              child: SingleChildScrollView(
                child: _buildLandmarksList(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}