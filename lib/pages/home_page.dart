import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'static_pose_page.dart';
import 'realtime_pose_page.dart';
import '../services/pose_detection_service.dart';

class HomePage extends StatefulWidget {
  const HomePage({Key? key}) : super(key: key);

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final PoseDetectionService _poseService = PoseDetectionService();
  bool _isCheckingSystem = false;
  Map<String, dynamic> _systemInfo = {};

  @override
  void initState() {
    super.initState();
    _checkSystemCapabilities();
  }

  @override
  void dispose() {
    _poseService.dispose();
    super.dispose();
  }

  Future<void> _checkSystemCapabilities() async {
    setState(() {
      _isCheckingSystem = true;
    });

    try {
      // Test GPU initialization
      final gpuResult = await _poseService.initializePoseDetector(useGpu: true);
      await _poseService.cleanup();

      // Test CPU initialization
      final cpuResult = await _poseService.initializePoseDetector(useGpu: false);
      await _poseService.cleanup();

      // Check camera permission
      final cameraResult = await _poseService.checkCameraPermission();

      setState(() {
        _systemInfo = {
          'gpuSupported': gpuResult['success'] ?? false,
          'cpuSupported': cpuResult['success'] ?? false,
          'cameraPermission': cameraResult['hasPermission'] ?? false,
          'lastChecked': DateTime.now(),
        };
        _isCheckingSystem = false;
      });
    } catch (e) {
      setState(() {
        _systemInfo = {
          'error': e.toString(),
          'lastChecked': DateTime.now(),
        };
        _isCheckingSystem = false;
      });
    }
  }

  void _navigateToStaticPose() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const StaticPosePage(),
      ),
    );
  }

  void _navigateToRealtimePose() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const RealtimePosePage(),
      ),
    );
  }

  void _showSystemInfoDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('System Information'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildInfoRow('GPU Support', _systemInfo['gpuSupported'] == true),
            _buildInfoRow('CPU Support', _systemInfo['cpuSupported'] == true),
            _buildInfoRow('Camera Permission', _systemInfo['cameraPermission'] == true),
            if (_systemInfo['error'] != null) ...[
              const SizedBox(height: 8),
              Text(
                'Error: ${_systemInfo['error']}',
                style: const TextStyle(color: Colors.red, fontSize: 12),
              ),
            ],
            if (_systemInfo['lastChecked'] != null) ...[
              const SizedBox(height: 8),
              Text(
                'Last checked: ${_formatTime(_systemInfo['lastChecked'])}',
                style: const TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              _checkSystemCapabilities();
            },
            child: const Text('Refresh'),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, bool status) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            status ? Icons.check_circle : Icons.cancel,
            color: status ? Colors.green : Colors.red,
            size: 20,
          ),
          const SizedBox(width: 8),
          Text(label),
        ],
      ),
    );
  }

  String _formatTime(DateTime dateTime) {
    return '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}:${dateTime.second.toString().padLeft(2, '0')}';
  }

  Widget _buildFeatureCard({
    required String title,
    required String description,
    required IconData icon,
    required VoidCallback onTap,
    required Color color,
    bool enabled = true,
  }) {
    return Card(
      elevation: 4,
      child: InkWell(
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            gradient: enabled
                ? LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                color.withOpacity(0.1),
                color.withOpacity(0.05),
              ],
            )
                : null,
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: enabled ? color.withOpacity(0.2) : Colors.grey.withOpacity(0.2),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  icon,
                  size: 32,
                  color: enabled ? color : Colors.grey,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                title,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: enabled ? color : Colors.grey,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                description,
                style: TextStyle(
                  fontSize: 14,
                  color: enabled ? Colors.black87 : Colors.grey,
                ),
                textAlign: TextAlign.center,
              ),
              if (!enabled) ...[
                const SizedBox(height: 8),
                const Text(
                  'Check system requirements',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.red,
                    fontStyle: FontStyle.italic,
                  ),
                  textAlign: TextAlign.center,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bool systemReady = _systemInfo['gpuSupported'] == true || _systemInfo['cpuSupported'] == true;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fitness Pose Detection'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            onPressed: _showSystemInfoDialog,
            icon: const Icon(Icons.info_outline),
            tooltip: 'System Info',
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Welcome section
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: [
                    const Icon(
                      Icons.fitness_center,
                      size: 48,
                      color: Colors.deepPurple,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Welcome to Fitness Pose Detection',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Advanced pose detection using MediaPipe with native Android integration.',
                      style: TextStyle(fontSize: 16),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    if (_isCheckingSystem)
                      const Column(
                        children: [
                          CircularProgressIndicator(),
                          SizedBox(height: 8),
                          Text('Checking system capabilities...'),
                        ],
                      )
                    else
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            systemReady ? Icons.check_circle : Icons.warning,
                            color: systemReady ? Colors.green : Colors.orange,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            systemReady ? 'System Ready' : 'Check Requirements',
                            style: TextStyle(
                              color: systemReady ? Colors.green : Colors.orange,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 24),

            // Features grid
            Expanded(
              child: GridView.count(
                crossAxisCount: MediaQuery.of(context).size.width > 600 ? 2 : 1,
                childAspectRatio: MediaQuery.of(context).size.width > 600 ? 1.2 : 2.0,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
                children: [
                  _buildFeatureCard(
                    title: 'Static Pose Detection',
                    description: 'Analyze pose from photos with detailed landmark visualization and confidence scores.',
                    icon: Icons.photo_camera,
                    onTap: _navigateToStaticPose,
                    color: Colors.blue,
                    enabled: systemReady,
                  ),
                  _buildFeatureCard(
                    title: 'Realtime Pose Detection',
                    description: 'Live pose tracking from camera feed with real-time performance metrics.',
                    icon: Icons.videocam,
                    onTap: _navigateToRealtimePose,
                    color: Colors.green,
                    enabled: systemReady && (_systemInfo['cameraPermission'] == true),
                  ),
                ],
              ),
            ),

            // Footer info
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Column(
                children: [
                  Text(
                    'Powered by MediaPipe & Flutter',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: Colors.grey,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Native Kotlin implementation with GPU acceleration',
                    style: TextStyle(
                      fontSize: 10,
                      color: Colors.grey,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}