import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:permission_handler/permission_handler.dart'; // Import permission_handler

void main() {
  // Ensure Flutter binding is initialized
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
      themeMode: ThemeMode.dark,
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
  // Plugin instance
  final BeautyCameraPlugin _cameraPlugin = BeautyCameraPlugin();

  // Camera state
  bool _isCameraInitialized = false;
  bool _isCapturing = false;
  bool _isViewCreated = false;
  String _currentFilter = 'none';
  double _zoomLevel = 0.0;
  String? _lastErrorMessage;
  String _currentFlashMode = 'off';

  // UI state
  bool _showFilterSelector = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _requestCameraPermissionAndInitialize(); // Request permission and initialize
  }

  Future<void> _requestCameraPermissionAndInitialize() async {
    final status = await Permission.camera.request();
    if (status.isGranted) {
      print("Camera permission granted.");
      // If view is already created, initialize camera. Otherwise, it will be initialized when view is created.
      if (_isViewCreated && !_isCameraInitialized) {
        _initializeCamera();
      } else if (!_isViewCreated) {
        print(
          "View not created yet, camera will be initialized once view is ready.",
        );
      }
    } else if (status.isDenied) {
      print("Camera permission denied.");
      setState(() {
        _lastErrorMessage =
            "Camera permission denied. Please grant permission in settings.";
      });
    } else if (status.isPermanentlyDenied) {
      print("Camera permission permanently denied.");
      setState(() {
        _lastErrorMessage =
            "Camera permission permanently denied. Please open settings to grant permission.";
      });
      await openAppSettings();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Handle app lifecycle changes
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      // App is in background
      _disposeCamera();
    } else if (state == AppLifecycleState.resumed) {
      // App is in foreground
      if (_isViewCreated) {
        // Check if view is created before attempting to reinitialize
        _requestCameraPermissionAndInitialize(); // Re-check permission and initialize if needed
      }
    }
  }

  Future<void> _initializeCamera() async {
    print("Initializing camera with view created = $_isViewCreated");

    if (!_isViewCreated) {
      print("View not created yet, delaying camera initialization");
      setState(() {
        _lastErrorMessage = "Waiting for camera view to initialize...";
      });
      return;
    }

    try {
      // Clear any previous error message
      setState(() {
        _lastErrorMessage = null;
      });

      print("Calling plugin.initializeCamera()");
      // Initialize the camera
      await _cameraPlugin.initializeCamera();

      print("Camera initialized successfully");
      setState(() {
        _isCameraInitialized = true;
        _lastErrorMessage = null;
      });
    } catch (e) {
      print('Error initializing camera: $e');
      setState(() {
        _isCameraInitialized = false;
        _lastErrorMessage = 'Failed to initialize camera: $e';
      });

      // Try to reinitialize after a short delay if there was an error
      if (_isViewCreated) {
        Future.delayed(Duration(seconds: 2), () {
          if (!_isCameraInitialized && _isViewCreated) {
            print("Attempting to reinitialize camera after error");
            _initializeCamera();
          }
        });
      }
    }
  }

  Future<void> _takePicture() async {
    // Additional check to prevent duplicate requests
    if (_isCapturing) {
      print("Already capturing, ignoring duplicate request");
      return;
    }

    if (!_isCameraInitialized) {
      print("Camera not initialized, cannot take picture");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Camera not initialized yet. Please wait.')),
      );
      setState(() {
        _isCapturing = false;
      });
      return;
    }

    // No need to set _isCapturing = true here as it's set by the button handler

    try {
      print("Taking picture request initiated at ${DateTime.now()}");
      // Display visual feedback for capture
      HapticFeedback.mediumImpact();

      final String? imagePath = await _cameraPlugin.takePicture();

      print(
        "Picture taken successfully at ${DateTime.now()}, path: $imagePath",
      );

      setState(() {
        _isCapturing = false;
      });

      if (imagePath != null) {
        _showPhotoPreviewDialog(imagePath);
      } else {
        print("Image path is null, picture might not have been saved");
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to save picture')));
      }
    } catch (e) {
      print("Error taking picture at ${DateTime.now()}: $e");

      // Reset capturing state
      setState(() {
        _isCapturing = false;
        _lastErrorMessage = 'Failed to take picture: $e';
      });

      // Show error to user
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to take picture: $e'),
          backgroundColor: Colors.red,
          duration: Duration(seconds: 3),
        ),
      );
    }
  }

  void _showPhotoPreviewDialog(String imagePath) {
    showDialog(
      context: context,
      builder:
          (context) => Dialog(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final screenHeight = MediaQuery.of(context).size.height;
                final isLandscape =
                    MediaQuery.of(context).orientation == Orientation.landscape;

                // Giới hạn chiều cao cho landscape mode
                final maxHeight =
                    isLandscape ? screenHeight * 0.8 : screenHeight * 0.9;

                return ConstrainedBox(
                  constraints: BoxConstraints(maxHeight: maxHeight),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Image với Flexible để tránh overflow
                      // Native đã xử lý tất cả rotation - image đã có orientation đúng
                      Flexible(
                        child: Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.file(
                              File(imagePath),
                              fit: BoxFit.contain,
                            ),
                          ),
                        ),
                      ),
                      // ButtonBar với height cố định
                      SizedBox(
                        height: 48, // Height cố định cho ButtonBar
                        child: ButtonBar(
                          children: [
                            TextButton(
                              onPressed: () => Navigator.of(context).pop(),
                              child: const Text('CLOSE'),
                            ),
                            TextButton(
                              onPressed: () {
                                // Share or save to gallery logic would go here
                                Navigator.of(context).pop();
                              },
                              child: const Text('SAVE'),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
    );
  }

  Future<void> _setFilter(String filter) async {
    if (!_isCameraInitialized) return;

    try {
      await _cameraPlugin.setFilter(filter);
      setState(() {
        _currentFilter = filter;
        _showFilterSelector = false; // Close filter selector after selection
      });
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set filter: $e';
      });
    }
  }

  Future<void> _toggleFlash() async {
    if (!_isCameraInitialized) return;

    try {
      final String? newFlashMode = await _cameraPlugin.toggleFlash();

      // Update UI to reflect the new flash mode
      if (newFlashMode != null) {
        setState(() {
          _currentFlashMode = newFlashMode;
        });
      }
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Failed to toggle flash: $e')));
    }
  }

  Future<void> _switchCamera() async {
    if (!_isCameraInitialized) return;

    try {
      await _cameraPlugin.switchCamera();
      // Reset zoom when switching camera
      setState(() {
        _zoomLevel = 0.0;
      });
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Failed to switch camera: $e')));
    }
  }

  Future<void> _setZoom(double zoom) async {
    if (!_isCameraInitialized) return;

    try {
      await _cameraPlugin.setZoom(zoom);
      setState(() {
        _zoomLevel = zoom;
      });
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Failed to set zoom: $e')));
    }
  }

  Future<void> _disposeCamera() async {
    if (_isCameraInitialized) {
      try {
        await _cameraPlugin.dispose();
        setState(() {
          _isCameraInitialized = false;
        });
      } catch (e) {
        debugPrint('Error disposing camera: $e');
      }
    }
  }

  Widget _buildCaptureButton() {
    return GestureDetector(
      onTap:
          _isCapturing
              ? null
              : () {
                // Debounce to prevent rapid sequential taps
                if (_isCapturing) return;

                // Take the picture
                _takePicture();

                // Additional protection against rapid taps
                setState(() {
                  _isCapturing = true;
                });

                // Re-enable the button after a short delay even if
                // the capture operation hasn't completed yet
                // This helps in case the callback is never invoked due to a silent failure
                Future.delayed(Duration(seconds: 10), () {
                  if (mounted && _isCapturing) {
                    print("Capture timeout - resetting capture state");
                    setState(() {
                      _isCapturing = false;
                    });
                  }
                });
              },
      child: AnimatedContainer(
        duration: Duration(milliseconds: 200),
        width: _isCapturing ? 60 : 70,
        height: _isCapturing ? 60 : 70,
        decoration: BoxDecoration(
          color: Colors.white.withOpacity(_isCapturing ? 0.5 : 0.8),
          shape: BoxShape.circle,
          border: Border.all(
            color: _isCapturing ? Colors.red : Colors.white,
            width: 3,
          ),
        ),
        child: Center(
          child: AnimatedSwitcher(
            duration: Duration(milliseconds: 300),
            child:
                _isCapturing
                    ? Stack(
                      key: ValueKey('capturing'),
                      alignment: Alignment.center,
                      children: [
                        const CircularProgressIndicator(
                          color: Colors.red,
                          strokeWidth: 3,
                        ),
                        const Icon(
                          Icons.camera_alt,
                          color: Colors.black45,
                          size: 24,
                        ),
                      ],
                    )
                    : const Icon(
                      key: ValueKey('ready'),
                      Icons.camera_alt,
                      color: Colors.black,
                      size: 32,
                    ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _disposeCamera();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Camera Preview - Full screen, let native handle orientation
            Positioned.fill(
              child: AndroidView(
                viewType: 'com.example/camera_preview_view',
                layoutDirection: TextDirection.ltr,
                creationParams: const <String, dynamic>{},
                creationParamsCodec: const StandardMessageCodec(),
                onPlatformViewCreated: (int id) {
                  print("AndroidView (Platform view) created with id: $id");
                  setState(() {
                    _isViewCreated = true;
                  });
                  _requestCameraPermissionAndInitialize();
                },
                gestureRecognizers:
                    const <Factory<OneSequenceGestureRecognizer>>{},
              ),
            ),

            // Loading indicator shown before camera is initialized
            if (!_isCameraInitialized)
              const Positioned.fill(
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: Colors.white),
                      SizedBox(height: 16),
                      Text(
                        "Initializing camera...",
                        style: TextStyle(color: Colors.white),
                      ),
                    ],
                  ),
                ),
              ),

            // Error Messages
            if (_lastErrorMessage != null)
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
                    _lastErrorMessage!,
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
              ),

            // Filter selector panel
            if (_showFilterSelector)
              Positioned(
                bottom: 130,
                left: 0,
                right: 0,
                child: Container(
                  height: 100,
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.7),
                    borderRadius: BorderRadius.vertical(
                      top: Radius.circular(12),
                    ),
                  ),
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: 4.0, bottom: 2.0),
                        child: Text(
                          'Select Filter',
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      Expanded(
                        child: ListView(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 4,
                          ),
                          children: [
                            FilterButton(
                              name: 'None',
                              filterType: 'none',
                              currentFilter: _currentFilter,
                              onPressed: _setFilter,
                            ),
                            FilterButton(
                              name: 'Sepia',
                              filterType: 'sepia',
                              currentFilter: _currentFilter,
                              onPressed: _setFilter,
                            ),
                            FilterButton(
                              name: 'Mono',
                              filterType: 'mono',
                              currentFilter: _currentFilter,
                              onPressed: _setFilter,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            // Camera controls
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                height: 120,
                padding: const EdgeInsets.all(16),
                color: Colors.black.withOpacity(0.5),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    IconButton(
                      icon: Icon(
                        Icons.filter_vintage,
                        color:
                            _showFilterSelector ? Colors.amber : Colors.white,
                      ),
                      onPressed:
                          () => setState(
                            () => _showFilterSelector = !_showFilterSelector,
                          ),
                      tooltip: 'Filters',
                    ),
                    _buildCaptureButton(),
                    IconButton(
                      icon: Icon(
                        _currentFlashMode == 'off'
                            ? Icons.flash_off
                            : _currentFlashMode == 'on'
                            ? Icons.flash_on
                            : Icons.flash_auto,
                        color: Colors.white,
                      ),
                      onPressed: _toggleFlash,
                      tooltip: 'Toggle Flash',
                    ),
                  ],
                ),
              ),
            ),

            // Camera switch and zoom controls
            Positioned(
              top: 16,
              right: 16,
              child: Column(
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
                      ),
                      onPressed: _switchCamera,
                      tooltip: 'Switch Camera',
                    ),
                  ),
                  const SizedBox(height: 16),
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.4),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    padding: const EdgeInsets.symmetric(
                      vertical: 8,
                      horizontal: 4,
                    ),
                    width: 40,
                    height: 150,
                    child: RotatedBox(
                      quarterTurns: 3,
                      child: Slider(
                        value: _zoomLevel,
                        min: 0.0,
                        max: 1.0,
                        onChanged: _setZoom,
                        activeColor: Colors.white,
                        inactiveColor: Colors.white30,
                      ),
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

// Helper widget for filter buttons
class FilterButton extends StatelessWidget {
  final String name;
  final String filterType;
  final String currentFilter;
  final Function(String) onPressed;

  const FilterButton({
    Key? key,
    required this.name,
    required this.filterType,
    required this.currentFilter,
    required this.onPressed,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final isSelected = filterType == currentFilter;

    // More compact layout with better touch targets
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Button with visual feedback
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () => onPressed(filterType),
              customBorder: CircleBorder(),
              child: Container(
                width: 46, // Slightly smaller
                height: 46, // Slightly smaller
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: isSelected ? Colors.amber : Colors.transparent,
                    width: 2,
                  ),
                  color: Colors.grey[800],
                ),
                child: Center(
                  child: Text(
                    name.substring(0, 1),
                    style: TextStyle(
                      color: isSelected ? Colors.amber : Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16, // Larger text for better visibility
                    ),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          // Label
          Text(
            name,
            style: TextStyle(
              color: isSelected ? Colors.amber : Colors.white,
              fontSize: 10,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ],
      ),
    );
  }
}
