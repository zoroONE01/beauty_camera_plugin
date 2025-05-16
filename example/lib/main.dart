import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const CameraScreen(),
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final _beautyCameraPlugin = BeautyCameraPlugin();

  bool _isCameraInitialized = false;
  int? _textureId;
  FilterType _currentFilter = FilterType.none;
  FlashMode _currentFlashMode = FlashMode.off;
  CameraFacing _currentCameraFacing = CameraFacing.back;
  String? _lastErrorMessage;
  bool _isCapturing = false;
  String? _lastCapturedImagePath;

  @override
  void initState() {
    super.initState();
    _initializeAndRequestPermissions();
  }

  Future<void> _initializeAndRequestPermissions() async {
    // Request camera permission
    final cameraStatus = await Permission.camera.request();

    // On Android 10+ we don't need to request storage permission for app-specific directories
    // For Android 9 and below, request storage permission
    bool storageGranted = true;
    if (Platform.isAndroid) {
      // Get Android SDK version
      final sdkInt = await _getAndroidVersion();
      if (sdkInt < 29) {
        // Android 9 (Pie) or below
        final storageStatus = await Permission.storage.request();
        storageGranted = storageStatus.isGranted;
      }
    }

    if (cameraStatus.isGranted && storageGranted) {
      // Initialize camera if permissions granted
      await _initializeCamera();
    } else {
      setState(() {
        if (!cameraStatus.isGranted) {
          _lastErrorMessage = 'Camera permission denied';
        } else {
          _lastErrorMessage = 'Storage permission denied';
        }
      });
    }
  }

  Future<void> _initializeCamera() async {
    try {
      final result = await _beautyCameraPlugin.initializeCamera(
        facing: _currentCameraFacing,
        resolutionPreset: ResolutionPreset.high,
      );

      setState(() {
        _textureId = result['textureId'] as int;
        _isCameraInitialized = true;
        _lastErrorMessage = null;
      });
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to initialize camera: $e';
        _isCameraInitialized = false;
      });
    }
  }

  Future<void> _onCapturePressed() async {
    if (_isCapturing) return;

    setState(() {
      _isCapturing = true;
    });

    try {
      // Get a temporary directory to save the image
      final tempDir = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final savePath = '${tempDir.path}/beauty_camera_$timestamp.jpg';

      final result = await _beautyCameraPlugin.takePicture(savePath);

      final filePath = result['filePath'] as String?;

      setState(() {
        _lastCapturedImagePath = filePath ?? savePath;
        _isCapturing = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Photo saved to: $_lastCapturedImagePath')),
        );
      }
    } catch (e) {
      setState(() {
        _isCapturing = false;
        _lastErrorMessage = 'Failed to take picture: $e';
      });
    }
  }

  Future<void> _onFilterSelected(FilterType filter) async {
    try {
      final result = await _beautyCameraPlugin.setFilter(filter);
      if (result['success'] == true) {
        setState(() {
          _currentFilter = filter;
        });
      }
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set filter: $e';
      });
    }
  }

  Future<void> _onFlashModeChanged() async {
    // Cycle through flash modes: OFF -> ON -> AUTO -> OFF
    final FlashMode newMode;
    switch (_currentFlashMode) {
      case FlashMode.off:
        newMode = FlashMode.on;
        break;
      case FlashMode.on:
        newMode = FlashMode.auto;
        break;
      case FlashMode.auto:
        newMode = FlashMode.off;
        break;
    }

    try {
      final result = await _beautyCameraPlugin.setFlashMode(newMode);
      if (result['success'] == true) {
        setState(() {
          _currentFlashMode = newMode;
        });
      }
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set flash mode: $e';
      });
    }
  }

  Future<void> _onSwitchCamera() async {
    final newFacing =
        (_currentCameraFacing == CameraFacing.back)
            ? CameraFacing.front
            : CameraFacing.back;

    // Dispose the current camera
    await _beautyCameraPlugin.disposeCamera();

    setState(() {
      _isCameraInitialized = false;
      _textureId = null;
      _currentCameraFacing = newFacing;
    });

    // Re-initialize with new facing direction
    await _initializeCamera();
  }

  @override
  void dispose() {
    _beautyCameraPlugin.disposeCamera();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Beauty Camera Plugin')),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              fit: StackFit.expand,
              children: [
                // Camera preview with tap-to-focus
                if (_isCameraInitialized && _textureId != null)
                  // Use the new CameraPreview widget instead of raw Texture widget
                  CameraPreview(
                    textureId: _textureId!,
                    cameraPlugin: _beautyCameraPlugin,
                    aspectRatio: 3 / 4,
                    filterOverlay: _getFilterOverlayColor(),
                    onFocusComplete: () {
                      // We can add a small visual feedback here if needed
                    },
                  ),

                // Error message or initializing indicator
                if (!_isCameraInitialized || _textureId == null)
                  Center(
                    child:
                        _lastErrorMessage != null
                            ? Text(
                              _lastErrorMessage!,
                              style: const TextStyle(color: Colors.red),
                            )
                            : const CircularProgressIndicator(),
                  ),

                // Loading indicator when capturing
                if (_isCapturing)
                  Container(
                    color: Colors.black26,
                    child: const Center(child: CircularProgressIndicator()),
                  ),
              ],
            ),
          ),

          // Last captured image preview
          if (_lastCapturedImagePath != null)
            Container(
              height: 80,
              width: 80,
              margin: const EdgeInsets.all(8.0),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white),
                borderRadius: BorderRadius.circular(4.0),
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(4.0),
                child: Image.file(
                  File(_lastCapturedImagePath!),
                  fit: BoxFit.cover,
                ),
              ),
            ),

          // Filter selection
          Container(
            height: 60,
            color: Colors.black12,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(
                horizontal: 8.0,
                vertical: 8.0,
              ),
              children: [
                _buildFilterButton(FilterType.none, 'None'),
                _buildFilterButton(FilterType.sepia, 'Sepia'),
                _buildFilterButton(FilterType.grayScale, 'Gray'),
                _buildFilterButton(FilterType.invert, 'Invert'),
                _buildFilterButton(FilterType.brightness, 'Bright'),
                _buildFilterButton(FilterType.contrast, 'Contrast'),
                _buildFilterButton(FilterType.saturation, 'Saturation'),
                _buildFilterButton(FilterType.gamma, 'Gamma'),
                _buildFilterButton(FilterType.monoChrome, 'Mono'),
              ],
            ),
          ),

          // Camera controls
          Container(
            height: 100,
            padding: const EdgeInsets.all(16.0),
            color: Colors.black,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // Flash mode button
                IconButton(
                  onPressed: _onFlashModeChanged,
                  icon: Icon(
                    _getFlashModeIcon(),
                    color: Colors.white,
                    size: 28,
                  ),
                ),

                // Capture button
                GestureDetector(
                  onTap: _onCapturePressed,
                  child: Container(
                    width: 70,
                    height: 70,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(color: Colors.white, width: 3),
                    ),
                    child: Container(
                      margin: const EdgeInsets.all(5),
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),

                // Switch camera button
                IconButton(
                  onPressed: _onSwitchCamera,
                  icon: const Icon(
                    Icons.flip_camera_ios,
                    color: Colors.white,
                    size: 28,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterButton(FilterType filter, String label) {
    final isSelected = filter == _currentFilter;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0),
      child: ElevatedButton(
        onPressed: () => _onFilterSelected(filter),
        style: ElevatedButton.styleFrom(
          backgroundColor: isSelected ? Colors.blue : Colors.grey[300],
          foregroundColor: isSelected ? Colors.white : Colors.black,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: isSelected ? 4 : 1,
        ),
        child: Text(label),
      ),
    );
  }

  IconData _getFlashModeIcon() {
    switch (_currentFlashMode) {
      case FlashMode.off:
        return Icons.flash_off;
      case FlashMode.on:
        return Icons.flash_on;
      case FlashMode.auto:
        return Icons.flash_auto;
    }
  }

  // Get the Android API level
  Future<int> _getAndroidVersion() async {
    if (Platform.isAndroid) {
      try {
        // This is a simple approach - for production apps, consider using device_info_plus package
        final androidInfo =
            await MethodChannel(
              'beauty_camera_plugin',
            ).invokeMethod<int>('getAndroidVersion') ??
            0;
        return androidInfo;
      } catch (e) {
        // Default to requiring permission (Android 9 or below)
        return 28; // API 28 is Android 9 Pie
      }
    }
    return 0;
  }

  // Returns a color overlay based on the current filter
  Color? _getFilterOverlayColor() {
    // In a real implementation, we'd return different colors based on filter
    // For simplicity, we'll just show examples for a few filters
    switch (_currentFilter) {
      case FilterType.sepia:
        return Colors.orange.withValues(alpha: 0.1);
      case FilterType.grayScale:
        return Colors.grey.withValues(alpha: 0.1);
      case FilterType.invert:
        return Colors.purple.withValues(alpha: 0.05);
      default:
        return null; // No overlay for other filters
    }
  }
}
