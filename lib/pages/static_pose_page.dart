import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../services/pose_detection_service.dart';

class StaticPosePage extends StatefulWidget {
  const StaticPosePage({Key? key}) : super(key: key);

  @override
  State<StaticPosePage> createState() => _StaticPosePageState();
}

class _StaticPosePageState extends State<StaticPosePage> {
  final PoseDetectionService _poseService = PoseDetectionService();
  final ImagePicker _picker = ImagePicker();

  File? _selectedImage;
  Uint8List? _visualizedImage;
  PoseDetectionResult? _detectionResult;
  bool _isProcessing = false;
  bool _isInitialized = false;
  String _statusMessage = 'Not initialized';

  @override
  void initState() {
    super.initState();
    _initializePoseDetector();
  }

  @override
  void dispose() {
    _poseService.dispose();
    super.dispose();
  }

  Future<void> _initializePoseDetector() async {
    setState(() {
      _statusMessage = 'Initializing pose detector...';
    });

    final result = await _poseService.initializePoseDetector(useGpu: true);

    setState(() {
      _isInitialized = result['success'] ?? false;
      _statusMessage = _isInitialized
          ? 'Pose detector initialized (GPU: ${result['useGpu'] ?? false})'
          : 'Failed to initialize: ${result['error'] ?? 'Unknown error'}';
    });
  }

  Future<void> _pickImageFromGallery() async {
    try {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );

      if (image != null) {
        setState(() {
          _selectedImage = File(image.path);
          _visualizedImage = null;
          _detectionResult = null;
        });

        await _detectPose();
      }
    } catch (e) {
      _showSnackBar('Error picking image: $e');
    }
  }

  Future<void> _pickImageFromCamera() async {
    try {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 85,
      );

      if (image != null) {
        setState(() {
          _selectedImage = File(image.path);
          _visualizedImage = null;
          _detectionResult = null;
        });

        await _detectPose();
      }
    } catch (e) {
      _showSnackBar('Error taking photo: $e');
    }
  }

  Future<void> _detectPose() async {
    if (_selectedImage == null || !_isInitialized) return;

    setState(() {
      _isProcessing = true;
      _statusMessage = 'Detecting pose...';
    });

    try {
      final imageBytes = await _selectedImage!.readAsBytes();
      final result = await _poseService.detectPoseFromImage(imageBytes);

      setState(() {
        _detectionResult = result;
        _visualizedImage = result.visualizedImage;
        _isProcessing = false;
        _statusMessage = result.success
            ? 'Pose detected: ${result.landmarkCount} landmarks (${(result.confidence * 100).toStringAsFixed(1)}% confidence)'
            : 'Detection failed: ${result.error}';
      });
    } catch (e) {
      setState(() {
        _isProcessing = false;
        _statusMessage = 'Error during detection: $e';
      });
      _showSnackBar('Detection error: $e');
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  Widget _buildImageDisplay() {
    if (_selectedImage == null) {
      return Container(
        height: 300,
        width: double.infinity,
        decoration: BoxDecoration(
          border: Border.all(color: Colors.grey),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.image, size: 64, color: Colors.grey),
              SizedBox(height: 16),
              Text('No image selected', style: TextStyle(color: Colors.grey)),
            ],
          ),
        ),
      );
    }

    return Container(
      height: 300,
      width: double.infinity,
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey),
        borderRadius: BorderRadius.circular(8),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: _visualizedImage != null
            ? Image.memory(_visualizedImage!, fit: BoxFit.contain)
            : Image.file(_selectedImage!, fit: BoxFit.contain),
      ),
    );
  }

  Widget _buildLandmarksInfo() {
    if (_detectionResult == null || !_detectionResult!.success) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Detection Results',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text('Total Landmarks: ${_detectionResult!.landmarkCount}'),
            Text('Confidence: ${(_detectionResult!.confidence * 100).toStringAsFixed(1)}%'),
            const SizedBox(height: 8),
            Text('Visible Landmarks:'),
            const SizedBox(height: 4),
            SizedBox(
              height: 150,
              child: ListView.builder(
                itemCount: _detectionResult!.landmarks.length,
                itemBuilder: (context, index) {
                  final landmark = _detectionResult!.landmarks[index];
                  if (landmark.visibility > 0.3) {
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Text(
                        '${landmark.index}: (${landmark.x.toStringAsFixed(3)}, ${landmark.y.toStringAsFixed(3)}) - ${(landmark.visibility * 100).toStringAsFixed(1)}%',
                        style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
                      ),
                    );
                  }
                  return const SizedBox.shrink();
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
        title: const Text('Static Pose Detection'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status indicator
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: _isInitialized ? Colors.green.shade100 : Colors.red.shade100,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: _isInitialized ? Colors.green : Colors.red,
                ),
              ),
              child: Row(
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
                        color: _isInitialized ? Colors.green.shade800 : Colors.red.shade800,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // Action buttons
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isInitialized && !_isProcessing ? _pickImageFromGallery : null,
                    icon: const Icon(Icons.photo_library),
                    label: const Text('Gallery'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isInitialized && !_isProcessing ? _pickImageFromCamera : null,
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Camera'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _selectedImage != null && _isInitialized && !_isProcessing ? _detectPose : null,
                    icon: _isProcessing
                        ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                        : const Icon(Icons.search),
                    label: Text(_isProcessing ? 'Processing...' : 'Detect'),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Image display
            _buildImageDisplay(),

            const SizedBox(height: 16),

            // Landmarks information
            Expanded(
              child: SingleChildScrollView(
                child: _buildLandmarksInfo(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}