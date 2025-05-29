import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io';
import 'dart:math' as math;

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock orientation to portrait only - prevent rotation
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Beauty Camera Plugin Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
        brightness: Brightness.dark,
        appBarTheme: const AppBarTheme(
          elevation: 0,
          backgroundColor: Colors.black,
        ),
        bottomNavigationBarTheme: const BottomNavigationBarThemeData(
          backgroundColor: Colors.black,
          selectedItemColor: Colors.white,
          unselectedItemColor: Colors.grey,
        ),
      ),
      darkTheme: ThemeData.dark(),
      themeMode: ThemeMode.dark, // Force dark theme for camera app
      home: const CameraScreen(),
      debugShowCheckedModeBanner: false,
      // Lock orientation to prevent auto-rotation
      builder: (context, child) {
        return MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(1.0)),
          child: child!,
        );
      },
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen>
    with WidgetsBindingObserver {
  late final BeautyCameraController _cameraController;
  bool _permissionsGranted = false;
  bool _isCapturing = false;
  String? _lastCapturedImagePath;
  bool _showFilterSelector = false;
  double _currentFilterIntensity = 1.0;
  bool _showManualControls = false;
  double _currentExposure = 0.0;

  // Add orientation tracking for UI rotation while keeping screen locked
  CameraOrientation _deviceOrientation = CameraOrientation.portraitUp;

  @override
  void initState() {
    super.initState();

    // Enforce portrait orientation lock for screen
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

    WidgetsBinding.instance.addObserver(this);
    _cameraController = BeautyCameraController();
    _cameraController.addListener(_onControllerUpdate);
    _requestPermissionsAndInitialize();
  }

  void _onControllerUpdate() {
    if (mounted) {
      setState(() {
        // Update device orientation tracking for UI rotation
        if (_cameraController.currentOrientationData != null) {
          _deviceOrientation =
              _cameraController.currentOrientationData!.deviceOrientation;
        }
      });
    }
  }

  // Calculate rotation angle for UI elements based on device orientation
  double _getUIRotationAngle() {
    switch (_deviceOrientation) {
      case CameraOrientation.portraitUp:
        return 0.0;
      case CameraOrientation.landscapeLeft:
        return math.pi / 2; // 90 degrees
      case CameraOrientation.portraitDown:
        return math.pi; // 180 degrees
      case CameraOrientation.landscapeRight:
        return -math.pi / 2; // -90 degrees
    }
  }

  // Helper to build UI elements that rotate with device orientation
  Widget _buildRotatedUIElement(Widget child) {
    return Transform.rotate(angle: _getUIRotationAngle(), child: child);
  }

  Future<void> _requestPermissionsAndInitialize() async {
    final cameraStatus = await Permission.camera.request();
    // final storageStatus = await Permission.storage.request(); // For older Android versions or specific needs

    if (cameraStatus.isGranted) {
      setState(() {
        _permissionsGranted = true;
      });
      await _cameraController.initializeCamera();
    } else {
      setState(() {
        _permissionsGranted = false;
      });
      // Show a message or handle permission denial
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Camera permission is required to use the camera.'),
          ),
        );
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (!_permissionsGranted || !_cameraController.isCameraInitialized) return;

    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      // Consider disposing or pausing the camera controller if it supports it
      // For now, the controller handles its own lifecycle via its dispose method
      // which should be called when the CameraScreen is disposed.
    } else if (state == AppLifecycleState.resumed) {
      // Re-initialize if it was paused/stopped and not initialized
      if (!_cameraController.isCameraInitialized) {
        _cameraController.initializeCamera();
      }
    }
  }

  Future<void> _takePicture() async {
    if (!_cameraController.isCameraInitialized || _isCapturing) return;

    setState(() {
      _isCapturing = true;
    });

    try {
      HapticFeedback.mediumImpact();
      final String? imagePath = await _cameraController.takePicture();
      setState(() {
        _lastCapturedImagePath = imagePath;
      });
      if (imagePath != null) {
        _showPhotoPreviewDialog(imagePath);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to take picture: $e')));
      }
    } finally {
      setState(() {
        _isCapturing = false;
      });
    }
  }

  void _showPhotoPreviewDialog(String imagePath) {
    showDialog(
      context: context,
      builder:
          (context) => Dialog(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.file(
                      File(imagePath),
                      fit: BoxFit.contain,
                      errorBuilder:
                          (context, error, stackTrace) =>
                              const Center(child: Text('Error loading image')),
                    ),
                  ),
                ),
                OverflowBar(
                  children: [
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(),
                      child: const Text('CLOSE'),
                    ),
                    // Add save/share functionality if needed
                  ],
                ),
              ],
            ),
          ),
    );
  }

  Future<void> _setFilter(CameraFilter filter) async {
    await _cameraController.setFilter(filter);
  }

  Future<void> _setFilterIntensity(double intensity) async {
    setState(() {
      _currentFilterIntensity = intensity;
    });
    await _cameraController.setFilterIntensity(intensity);
  }

  Future<void> _toggleFlash() async {
    await _cameraController.toggleFlash();
  }

  Future<void> _switchCamera() async {
    await _cameraController.switchCamera();
  }

  Future<void> _setZoom(double zoom) async {
    await _cameraController.setZoom(zoom);
  }

  Future<void> _setExposure(double exposure) async {
    setState(() {
      _currentExposure = exposure;
    });
    await _cameraController.setExposure(exposure);
  }

  Future<void> _onTapToFocus(TapUpDetails details) async {
    final RenderBox? renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null) return;

    final size = renderBox.size;
    final x = details.localPosition.dx / size.width;
    final y = details.localPosition.dy / size.height;

    // Clamp values between 0.0 and 1.0
    final clampedX = x.clamp(0.0, 1.0);
    final clampedY = y.clamp(0.0, 1.0);

    await _cameraController.setFocusPoint(clampedX, clampedY);
  }

  void _toggleManualControls() {
    setState(() {
      _showManualControls = !_showManualControls;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cameraController.removeListener(_onControllerUpdate);
    _cameraController.dispose();
    super.dispose();
  }

  Widget _buildCameraPreview() {
    if (!_permissionsGranted) {
      return const Center(
        child: Text(
          'Camera permission not granted. Please enable it in settings.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white),
        ),
      );
    }
    return BeautyCameraView(
      controller: _cameraController,
      loadingWidget: const Center(
        child: CircularProgressIndicator(color: Colors.white),
      ),
      errorWidget: Center(
        child: Text(
          _cameraController.lastErrorMessage ?? 'An unknown error occurred.',
          style: const TextStyle(color: Colors.red),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Camera preview with tap-to-focus
            Positioned.fill(
              child: GestureDetector(
                onTapUp: _onTapToFocus,
                child: _buildCameraPreview(),
              ),
            ),

            // Error Messages
            if (_cameraController.lastErrorMessage != null &&
                _cameraController.isCameraInitialized)
              Positioned(
                top: 16,
                left: 16,
                right: 16,
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.red.withValues(alpha: 0.7),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    _cameraController.lastErrorMessage!,
                    style: const TextStyle(color: Colors.white),
                    textAlign: TextAlign.center,
                  ),
                ),
              ),

            // Filter selector panel
            if (_showFilterSelector)
              Positioned(
                bottom: 130,
                left: 0,
                right: 0,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      height: 100,
                      color: Colors.black.withValues(alpha: 0.7),
                      child: ListView.separated(
                        separatorBuilder:
                            (context, index) => const SizedBox(width: 8),
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        itemCount: CameraFilter.availableFilters.length,
                        itemBuilder: (context, index) {
                          final filter = CameraFilter.availableFilters[index];
                          return FilterButton(
                            name: filter.displayName,
                            filter: filter,
                            currentFilter: _cameraController.currentFilter,
                            rotation: _getUIRotationAngle(),
                            onPressed: (selectedFilter) {
                              _setFilter(selectedFilter);
                            },
                          );
                        },
                      ),
                    ),
                    if (_cameraController.currentFilter.supportsIntensity)
                      Container(
                        height: 50,
                        color: Colors.black.withValues(alpha: 0.7),
                        padding: const EdgeInsets.symmetric(horizontal: 16.0),
                        child: Row(
                          children: [
                            const Text(
                              'Intensity',
                              style: TextStyle(color: Colors.white),
                            ),
                            Expanded(
                              child: Slider(
                                value: _currentFilterIntensity,
                                min: 0.0,
                                max: 1.0,
                                divisions: 20,
                                onChanged: _setFilterIntensity,
                                activeColor: Colors.amber,
                                inactiveColor: Colors.white30,
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),

            // Manual controls panel
            if (_showManualControls)
              Positioned(
                bottom: 260,
                left: 0,
                right: 0,
                child: Container(
                  height: 120,
                  color: Colors.black.withValues(alpha: 0.8),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16.0,
                    vertical: 8.0,
                  ),
                  child: Column(
                    children: [
                      // Exposure control
                      Row(
                        children: [
                          _buildRotatedUIElement(
                            const Icon(
                              Icons.exposure,
                              color: Colors.white,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            'Exposure',
                            style: TextStyle(color: Colors.white, fontSize: 12),
                          ),
                          Expanded(
                            child: Slider(
                              value: _currentExposure,
                              min: -2.0,
                              max: 2.0,
                              divisions: 40,
                              onChanged: _setExposure,
                              activeColor: Colors.blue,
                              inactiveColor: Colors.white30,
                              label: _currentExposure.toStringAsFixed(1),
                            ),
                          ),
                          Text(
                            _currentExposure.toStringAsFixed(1),
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                      // Auto focus toggle
                      Row(
                        children: [
                          _buildRotatedUIElement(
                            const Icon(
                              Icons.center_focus_strong,
                              color: Colors.white,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            'Auto Focus',
                            style: TextStyle(color: Colors.white, fontSize: 12),
                          ),
                          const Spacer(),
                          Switch(
                            value: _cameraController.isAutoFocusEnabled,
                            onChanged: (value) async {
                              await _cameraController.setAutoFocus(value);
                            },
                            activeColor: Colors.blue,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),

            // Camera controls UI
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                height: 120,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                color: Colors.black.withValues(alpha: 0.5),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    _buildRotatedUIElement(
                      IconButton(
                        icon: Icon(
                          Icons.filter_vintage,
                          color:
                              _showFilterSelector ? Colors.amber : Colors.white,
                          size: 30,
                        ),
                        onPressed: () {
                          setState(() {
                            _showFilterSelector = !_showFilterSelector;
                          });
                        },
                        tooltip: 'Filters',
                      ),
                    ),
                    GestureDetector(
                      onTap: _isCapturing ? null : _takePicture,
                      child: Container(
                        width: 70,
                        height: 70,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(
                            alpha: _isCapturing ? 0.3 : 0.9,
                          ),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 3),
                        ),
                        child:
                            _isCapturing
                                ? const Padding(
                                  padding: EdgeInsets.all(16.0),
                                  child: CircularProgressIndicator(
                                    color: Colors.black,
                                    strokeWidth: 3,
                                  ),
                                )
                                : _buildRotatedUIElement(
                                  const Icon(
                                    Icons.camera_alt,
                                    color: Colors.black,
                                    size: 36,
                                  ),
                                ),
                      ),
                    ),
                    _buildRotatedUIElement(
                      IconButton(
                        icon: Icon(
                          Icons.tune,
                          color:
                              _showManualControls ? Colors.blue : Colors.white,
                          size: 30,
                        ),
                        onPressed: _toggleManualControls,
                        tooltip: 'Manual Controls',
                      ),
                    ),
                    _buildRotatedUIElement(
                      IconButton(
                        icon: Icon(
                          _cameraController.currentFlashMode == FlashMode.off
                              ? Icons.flash_off
                              : _cameraController.currentFlashMode ==
                                  FlashMode.on
                              ? Icons.flash_on
                              : Icons.flash_auto,
                          color: Colors.white,
                          size: 30,
                        ),
                        onPressed: _toggleFlash,
                        tooltip: 'Toggle Flash',
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Top controls (Switch camera, Zoom)
            Positioned(
              top: 16,
              right: 16,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.4),
                      shape: BoxShape.circle,
                    ),
                    child: _buildRotatedUIElement(
                      IconButton(
                        icon: const Icon(
                          Icons.flip_camera_ios,
                          color: Colors.white,
                          size: 28,
                        ),
                        onPressed: _switchCamera,
                        tooltip: 'Switch Camera',
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  // Zoom Slider (Vertical)
                  if (_cameraController.isCameraInitialized)
                    Container(
                      width: 40,
                      height: 150,
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.4),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 8.0),
                      child: RotatedBox(
                        quarterTurns: 3,
                        child: Slider(
                          value: _cameraController.currentZoomLevel,
                          min: 0.0,
                          max: 1.0,
                          divisions: 20,
                          onChanged: _setZoom,
                          activeColor: Colors.white,
                          inactiveColor: Colors.white30,
                        ),
                      ),
                    ),
                ],
              ),
            ),

            // Display last captured image thumbnail
            if (_lastCapturedImagePath != null)
              Positioned(
                bottom: 140,
                left: 16,
                child: GestureDetector(
                  onTap: () => _showPhotoPreviewDialog(_lastCapturedImagePath!),
                  child: Container(
                    width: 60,
                    height: 80,
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.white, width: 2),
                      borderRadius: BorderRadius.circular(8),
                      image: DecorationImage(
                        image: FileImage(File(_lastCapturedImagePath!)),
                        fit: BoxFit.cover,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

// Helper widget for filter buttons
class FilterButton extends StatelessWidget {
  final String name;
  final CameraFilter filter;
  final CameraFilter currentFilter;
  final Function(CameraFilter) onPressed;
  final double rotation;

  const FilterButton({
    super.key,
    required this.name,
    required this.filter,
    required this.currentFilter,
    required this.onPressed,
    this.rotation = 0.0,
  });

  @override
  Widget build(BuildContext context) {
    final isSelected = filter == currentFilter;
    return Transform.rotate(
      angle: rotation,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GestureDetector(
            onTap: () => onPressed(filter),
            child: Container(
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: isSelected ? Colors.amber : Colors.grey[700]!,
                  width: 2,
                ),
                color: Colors.grey[850],
              ),
              child: Center(
                child: Text(
                  name.isNotEmpty ? name.substring(0, 1).toUpperCase() : 'F',
                  style: TextStyle(
                    color: isSelected ? Colors.amber : Colors.white,
                    fontWeight:
                        isSelected ? FontWeight.bold : FontWeight.normal,
                    fontSize: 16,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            name,
            style: TextStyle(
              color: isSelected ? Colors.amber : Colors.white70,
              fontSize: 10,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
