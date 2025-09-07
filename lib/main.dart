import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

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
      home: const PoseDetectorPage(),
    );
  }
}

class PoseDetectorPage extends StatefulWidget {
  const PoseDetectorPage({super.key});

  @override
  State<PoseDetectorPage> createState() => _PoseDetectorPageState();
}

class _PoseDetectorPageState extends State<PoseDetectorPage> {
  static const platform = MethodChannel('com.example.mediapipe_native_test/mediapipe');

  final ImagePicker _picker = ImagePicker();
  File? _imageFile;
  ui.Image? _image;
  List<List<Map<String, double>>> _landmarks = [];
  String _timingDetails = '';
  String _landmarkData = '';
  bool _isLoading = false;

  Future<void> _pickImage() async {
    final XFile? image = await _picker.pickImage(source: ImageSource.gallery);
    if (image == null) return;

    final imageBytes = await image.readAsBytes();
    final decodedImage = await decodeImageFromList(imageBytes);

    setState(() {
      _imageFile = File(image.path);
      _image = decodedImage;
      _isLoading = true;
      _timingDetails = '';
      _landmarkData = '';
      _landmarks = [];
    });

    try {
      final result = await platform.invokeMethod('detectPose', {'imageBytes': imageBytes});
      _handleDetectionResult(result);
    } on PlatformException catch (e) {
      setState(() {
        _timingDetails = "Gagal memanggil native method: '${e.message}'.";
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _handleDetectionResult(dynamic result) {
    if (result == null) {
      setState(() {
        _timingDetails = "Gagal mendapatkan hasil dari native code (hasil null).";
        _landmarkData = '';
        _landmarks = [];
      });
      return;
    }

    final Map<Object?, Object?> resultMap = result;

    // Check for an error from the native side first
    if (resultMap.containsKey('error')) {
      setState(() {
        _timingDetails = "Error dari native code: ${resultMap['error']}";
        _landmarkData = '';
        _landmarks = [];
      });
      return;
    }

    // If no error, proceed with parsing
    final timing = resultMap['timing'] as Map<Object?, Object?>;
    final total = timing['total'];
    final preprocessing = timing['preprocessing'];
    final inference = timing['inference'];
    final timingStr = "Total: ${total}ms\nPreprocessing: ${preprocessing}ms\nInference: ${inference}ms";

    // Parse landmarks
    final landmarksResult = resultMap['landmarks'] as List<Object?>?;
    final List<List<Map<String, double>>> parsedLandmarks = [];
    final StringBuffer landmarkBuffer = StringBuffer();

    if (landmarksResult != null && landmarksResult.isNotEmpty) {
      int poseIndex = 0;
      for (final poseData in landmarksResult) {
        final List<Map<String, double>> pose = [];
        landmarkBuffer.writeln('Pose #${poseIndex++}');
        int landmarkIndex = 0;
        for (final landmarkMap in (poseData as List<Object?>)) {
          final landmark = (landmarkMap as Map<Object?, Object?>).cast<String, double>();
          pose.add(landmark);
          final x = landmark['x']!.toStringAsFixed(2);
          final y = landmark['y']!.toStringAsFixed(2);
          final z = landmark['z']!.toStringAsFixed(2);
          final vis = landmark['visibility']!.toStringAsFixed(2);
          landmarkBuffer.writeln('  L${landmarkIndex++}: (x:$x, y:$y, z:$z, vis:$vis)');
        }
        parsedLandmarks.add(pose);
      }
    } else {
      landmarkBuffer.writeln("Tidak ada pose yang terdeteksi.");
    }

    setState(() {
      _timingDetails = timingStr;
      _landmarkData = landmarkBuffer.toString();
      _landmarks = parsedLandmarks;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MediaPipe Pose Detection'),
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              if (_image == null)
                Container(
                  height: 300,
                  color: Colors.grey[300],
                  child: const Center(child: Text('Pilih gambar untuk memulai')),
                )
              else
                SizedBox(
                  height: 300,
                  child: FittedBox(
                    fit: BoxFit.contain,
                    child: SizedBox(
                      width: _image!.width.toDouble(),
                      height: _image!.height.toDouble(),
                      child: CustomPaint(
                        painter: PosePainter(_image, _landmarks),
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: _isLoading ? null : _pickImage,
                icon: const Icon(Icons.image),
                label: const Text('Pilih dari Galeri'),
              ),
              const SizedBox(height: 20),
              if (_isLoading) const Center(child: CircularProgressIndicator()),
              _buildResultCard("Waktu Pemrosesan", _timingDetails),
              _buildResultCard("Data Landmark", _landmarkData),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildResultCard(String title, String content) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8.0),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            SelectableText(
              content.isEmpty ? '...' : content,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }
}

class PosePainter extends CustomPainter {
  final ui.Image? image;
  final List<List<Map<String, double>>> landmarks;

  PosePainter(this.image, this.landmarks);

  @override
  void paint(Canvas canvas, Size size) {
    if (image != null) {
      canvas.drawImage(image!, Offset.zero, Paint());
    }

    final pointPaint = Paint()
      ..color = Colors.green
      ..strokeWidth = 8
      ..strokeCap = StrokeCap.round;

    final linePaint = Paint()
      ..color = Colors.red
      ..strokeWidth = 3;

    for (final pose in landmarks) {
      for (final landmark in pose) {
        canvas.drawPoints(
            ui.PointMode.points,
            [
              Offset(
                landmark['x']! * size.width,
                landmark['y']! * size.height,
              ),
            ],
            pointPaint);
      }

      // These are the connections defined by MediaPipe for the BlazePose model.
      final connections = [
        [0, 1], [1, 2], [2, 3], [3, 7], [0, 4], [4, 5], [5, 6], [6, 8],
        [9, 10], [11, 12], [11, 13], [13, 15], [15, 17], [15, 19], [15, 21],
        [12, 14], [14, 16], [16, 18], [16, 20], [16, 22], [11, 23], [12, 24],
        [23, 24], [23, 25], [24, 26], [25, 27], [26, 28], [27, 29], [27, 31],
        [28, 30], [28, 32], [29, 31], [30, 32]
      ];

      for (final connection in connections) {
          if (pose.length > connection[0] && pose.length > connection[1]) {
              final start = pose[connection[0]];
              final end = pose[connection[1]];
              canvas.drawLine(
                  Offset(start['x']! * size.width, start['y']! * size.height),
                  Offset(end['x']! * size.width, end['y']! * size.height),
                  linePaint,
              );
          }
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }
}