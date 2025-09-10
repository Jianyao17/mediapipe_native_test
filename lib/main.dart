import 'dart:io';
import 'dart:async';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image/image.dart' as img;
import 'package:camera/camera.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MediaPipe Pose Detection',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: RealtimePosePage(),
    );
  }
}


class RealtimePosePage extends StatefulWidget {
  const RealtimePosePage({super.key});

  @override
  State<RealtimePosePage> createState() => _RealtimePosePageState();
}

class _RealtimePosePageState extends State<RealtimePosePage> {
  static const _methodChannel = MethodChannel('com.example.mediapipe_native_test/method');
  static const _eventChannel = EventChannel('com.example.mediapipe_native_test/event');

  CameraController? _cameraController;
  bool _isProcessing = false;
  StreamSubscription<dynamic>? _poseSubscription;

  List<List<Map<String, double>>> _landmarks = [];
  int _inferenceTime = 0;
  Size _imageSize = Size.zero;

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  @override
  void dispose() {
    _poseSubscription?.cancel();
    _cameraController?.stopImageStream();
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _initializeCamera() async {
    try {
      final cameras = await availableCameras();
      final backCamera = cameras.firstWhere(
            (camera) => camera.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        backCamera,
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );

      await _cameraController!.initialize();
      if (!mounted) return;

      setState(() {});
      _listenToPoseResults();
      await _cameraController!.startImageStream(_processCameraImage);
      print("Kamera berhasil diinisialisasi dan stream dimulai.");
    } catch (e) {
      print("Gagal inisialisasi kamera: $e");
    }
  }

  void _listenToPoseResults() {
    _poseSubscription = _eventChannel.receiveBroadcastStream().listen((data) {
      if (!mounted) return;

      final resultMap = Map<String, dynamic>.from(data);
      final landmarksResult = resultMap['landmarks'] as List<dynamic>?;

      final List<List<Map<String, double>>> parsedLandmarks = [];
      if (landmarksResult != null && landmarksResult.isNotEmpty) {
        for (final poseData in landmarksResult) {
          final List<Map<String, double>> pose = [];
          for (final landmarkMap in (poseData as List<dynamic>)) {
            final landmark = Map<String, double>.from((landmarkMap as Map<dynamic, dynamic>).cast<String, double>());
            pose.add(landmark);
          }
          parsedLandmarks.add(pose);
        }
      }

      setState(() {
        _landmarks = parsedLandmarks;
        _inferenceTime = resultMap['inferenceTime'] as int;
        _imageSize = Size(
          (resultMap['imageWidth'] as int).toDouble(),
          (resultMap['imageHeight'] as int).toDouble(),
        );
      });

      _isProcessing = false;
    }, onError: (error) {
      print("Error pada EventChannel: ${error.message}");
      _isProcessing = false;
    });
  }

  void _processCameraImage(CameraImage image) {
    if (_isProcessing) return;
    _isProcessing = true;

    // *** INI BAGIAN YANG DIPERBAIKI ***
    // Konversi YUV420 dari CameraImage ke byte array JPEG
    _convertYUV420toJPEG(image).then((jpegBytes) {
      if (jpegBytes.isNotEmpty) {
        try {
          _methodChannel.invokeMethod('detectFromStream', {'imageBytes': jpegBytes});
        } catch (e) {
          print("Gagal memanggil native method: $e");
          _isProcessing = false;
        }
      } else {
        _isProcessing = false;
      }
    });
  }

  // Fungsi helper untuk mengonversi YUV420 ke JPEG
  Future<Uint8List> _convertYUV420toJPEG(CameraImage image) async {
    final yuvImage = img.Image(
        width: image.width,
        height: image.height,
        numChannels: 4 // Anggap sebagai RGBA untuk sementara
    );

    final yPlane = image.planes[0].bytes;
    final uPlane = image.planes[1].bytes;
    final vPlane = image.planes[2].bytes;

    final uRowStride = image.planes[1].bytesPerRow;
    final vRowStride = image.planes[2].bytesPerRow;
    final yRowStride = image.planes[0].bytesPerRow;

    for (int y = 0; y < image.height; y++) {
      for (int x = 0; x < image.width; x++) {
        final yIndex = y * yRowStride + x;
        final uvIndex = (y ~/ 2) * uRowStride + (x ~/ 2);

        final yValue = yPlane[yIndex];
        final uValue = uPlane[uvIndex];
        final vValue = vPlane[uvIndex];

        // Konversi YUV ke RGB (rumus standar)
        final r = (yValue + 1.402 * (vValue - 128)).clamp(0, 255).toInt();
        final g = (yValue - 0.344136 * (uValue - 128) - 0.714136 * (vValue - 128)).clamp(0, 255).toInt();
        final b = (yValue + 1.772 * (uValue - 128)).clamp(0, 255).toInt();

        yuvImage.setPixelRgba(x, y, r, g, b, 255);
      }
    }

    // Encode gambar yang sudah dikonversi ke format JPEG
    return Uint8List.fromList(img.encodeJpg(yuvImage, quality: 75));
  }


  @override
  Widget build(BuildContext context) {
    if (_cameraController == null || !_cameraController!.value.isInitialized) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Real-time Pose Detection')),
      body: Stack(
        fit: StackFit.expand,
        children: [
          CameraPreview(_cameraController!),
          if (_landmarks.isNotEmpty)
            CustomPaint(
              painter: PosePainter(
                landmarks: _landmarks,
                imageSize: _imageSize,
              ),
            ),
          Positioned(
            bottom: 24,
            left: 16,
            right: 16,
            child: Align(
              alignment: Alignment.bottomCenter,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.6),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  'Inference Time: ${_inferenceTime}ms',
                  style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}


class PosePainter extends CustomPainter {
  final List<List<Map<String, double>>> landmarks;
  final Size imageSize;

  PosePainter({required this.landmarks, required this.imageSize});

  @override
  void paint(Canvas canvas, Size size) {
    if (landmarks.isEmpty || imageSize == Size.zero) return;

    final double scaleX = size.width / imageSize.height;
    final double scaleY = size.height / imageSize.width;
    final double scale = scaleX < scaleY ? scaleX : scaleY;

    final double offsetX = (size.width - imageSize.height * scale) / 2;
    final double offsetY = (size.height - imageSize.width * scale) / 2;

    final pointPaint = Paint()
      ..color = Colors.lightGreenAccent
      ..strokeWidth = 8
      ..strokeCap = StrokeCap.round;

    final linePaint = Paint()
      ..color = Colors.redAccent
      ..strokeWidth = 3;

    for (final pose in landmarks) {
      final List<Offset> points = pose.map((landmark) {
        return Offset(
          (1 - landmark['y']!) * imageSize.height * scale + offsetX,
          landmark['x']! * imageSize.width * scale + offsetY,
        );
      }).toList();

      canvas.drawPoints(ui.PointMode.points, points, pointPaint);

      final connections = [
        [0, 1], [1, 2], [2, 3], [3, 7], [0, 4], [4, 5], [5, 6], [6, 8],
        [9, 10], [11, 12], [11, 13], [13, 15], [15, 17], [15, 19], [15, 21],
        [12, 14], [14, 16], [16, 18], [16, 20], [16, 22], [11, 23], [12, 24],
        [23, 24], [23, 25], [24, 26], [25, 27], [26, 28], [27, 29], [27, 31],
        [28, 30], [28, 32], [29, 31], [30, 32]
      ];

      for (final connection in connections) {
        if (points.length > connection[0] && points.length > connection[1]) {
          canvas.drawLine(points[connection[0]], points[connection[1]], linePaint);
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant PosePainter oldDelegate) {
    return oldDelegate.landmarks != landmarks || oldDelegate.imageSize != imageSize;
  }
}