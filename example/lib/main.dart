import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:permission_handler/permission_handler.dart';
// import 'package:path_provider/path_provider.dart'; // For saving files, if needed by example

void main() {
  WidgetsFlutterBinding.ensureInitialized();
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
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen>
    with WidgetsBindingObserver {
  late final BeautyCameraController _cameraController;
  bool _permissionsGranted = false;
  bool _isCapturing = false;
  String? _lastCapturedImagePath;
  bool _showFilterSelector = false;
  double _currentFilterIntensity = 0.5; // Default intensity

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _cameraController = BeautyCameraController();
    _cameraController.addListener(_onControllerUpdate);
    _requestPermissionsAndInitialize();
  }

  void _onControllerUpdate() {
    if (mounted) {
      setState(() {
        // Update UI based on controller state changes
        if (_cameraController.lastErrorMessage != null) {
          // Optionally show a snackbar or log errors
          // ScaffoldMessenger.of(context).showSnackBar(
          //   SnackBar(content: Text(_cameraController.lastErrorMessage!)),
          // );
        }
      });
    }
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
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Camera permission is required to use the camera.'),
        ),
      );
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Failed to take picture: $e')));
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
            Positioned.fill(child: _buildCameraPreview()),

            // Error Messages from controller (alternative to snackbar)
            if (_cameraController.lastErrorMessage != null &&
                _cameraController.isCameraInitialized)
              Positioned(
                top: 16,
                left: 16,
                right: 16,
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.7),
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
                      color: Colors.black.withOpacity(0.7),
                      child: ListView.separated(
                        separatorBuilder: (context, index) => const SizedBox(width: 8),
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
                        color: Colors.black.withOpacity(0.7),
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
                color: Colors.black.withOpacity(0.5),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
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
                    GestureDetector(
                      onTap: _isCapturing ? null : _takePicture,
                      child: Container(
                        width: 70,
                        height: 70,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(
                            _isCapturing ? 0.3 : 0.9,
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
                                : const Icon(
                                  Icons.camera_alt,
                                  color: Colors.black,
                                  size: 36,
                                ),
                      ),
                    ),
                    IconButton(
                      icon: Icon(
                        _cameraController.currentFlashMode == FlashMode.off
                            ? Icons.flash_off
                            : _cameraController.currentFlashMode == FlashMode.on
                            ? Icons.flash_on
                            : Icons.flash_auto,
                        color: Colors.white,
                        size: 30,
                      ),
                      onPressed: _toggleFlash,
                      tooltip: 'Toggle Flash',
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
                      color: Colors.black.withOpacity(0.4),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: const Icon(
                        Icons.flip_camera_ios,
                        color: Colors.white,
                        size: 28,
                      ),
                      onPressed: _switchCamera,
                      tooltip: 'Switch Camera',
                    ),
                  ),
                  const SizedBox(height: 16),
                  // Zoom Slider (Vertical)
                  if (_cameraController
                      .isCameraInitialized) // Only show if camera is ready
                    Container(
                      width: 40,
                      height: 150,
                      decoration: BoxDecoration(
                        color: Colors.black.withOpacity(0.4),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 8.0),
                      child: RotatedBox(
                        quarterTurns: 3, // Makes slider vertical, thumb on left
                        child: Slider(
                          value: _cameraController.currentZoomLevel,
                          min: 0.0,
                          max: 1.0,
                          divisions: 20, // Optional: for discrete steps
                          onChanged: _setZoom,
                          activeColor: Colors.white,
                          inactiveColor: Colors.white30,
                        ),
                      ),
                    ),
                ],
              ),
            ),

            // Display last captured image thumbnail (optional)
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

  const FilterButton({
    super.key,
    required this.name,
    required this.filter,
    required this.currentFilter,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final isSelected = filter == currentFilter;
    return Column(
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
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
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
    );
  }
}
