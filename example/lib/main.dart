import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:permission_handler/permission_handler.dart';

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
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen>
    with WidgetsBindingObserver {
  // Plugin instance
  final BeautyCameraPlugin _cameraPlugin = BeautyCameraPlugin();

  // Camera state
  bool _isCameraInitialized = false;
  bool _isCapturing = false;
  bool _isViewCreated = false;
  CameraFilter _currentFilter = CameraFilter.none;
  double _filterIntensity = 1.0; // Filter intensity (0.0 to 1.0)
  double _zoomLevel = 0.0;
  String? _lastErrorMessage;
  String _currentFlashMode = 'off';

  // Orientation state
  StreamSubscription<OrientationData>? _orientationSubscription;

  // UI state
  bool _showFilterSelector = false;
  bool _showIntensityControls = false;
  int _selectedFilterCategory = 0; // 0 = Basic, 1 = Advanced
  bool _isProcessingFilter = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _requestCameraPermissionAndInitialize(); // Request permission and initialize
  }

  Future<void> _requestCameraPermissionAndInitialize() async {
    final status = await Permission.camera.request();
    if (status.isGranted) {

      // If view is already created, initialize camera. Otherwise, it will be initialized when view is created.
      if (_isViewCreated && !_isCameraInitialized) {
        _initializeCamera();
      }
    } else if (status.isDenied) {
      setState(() {
        _lastErrorMessage =
            "Camera permission denied. Please grant permission in settings.";
      });
    } else if (status.isPermanentlyDenied) {
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

    if (!_isViewCreated) {
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

      // Initialize the camera
      await _cameraPlugin.initializeCamera();

      setState(() {
        _isCameraInitialized = true;
        _lastErrorMessage = null;
      });
      _startListeningToOrientation(); // Bắt đầu lắng nghe sau khi camera khởi tạo thành công
    } catch (e) {
      setState(() {
        _isCameraInitialized = false;
        _lastErrorMessage = 'Failed to initialize camera: $e';
      });

      // Try to reinitialize after a short delay if there was an error
      if (_isViewCreated) {
        Future.delayed(Duration(seconds: 2), () {
          if (!_isCameraInitialized && _isViewCreated) {
            _initializeCamera();
          }
        });
      }
    }
  }

  Future<void> _takePicture() async {
    // Additional check to prevent duplicate requests
    if (_isCapturing) {
      return;
    }

    if (!_isCameraInitialized) {
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
      // Display visual feedback for capture
      HapticFeedback.mediumImpact();

      final String? imagePath = await _cameraPlugin.takePicture();

      setState(() {
        _isCapturing = false;
      });

      if (imagePath != null) {
        _showPhotoPreviewDialog(imagePath);
      } else {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to save picture')));
      }
    } catch (e) {
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
                        child: OverflowBar(
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

  Future<void> _setFilter(CameraFilter filter) async {
    if (!_isCameraInitialized) return;

    // Show processing indicator for advanced filters
    final isAdvancedFilter = CameraFilter.advancedFilters.contains(filter);
    if (isAdvancedFilter) {
      setState(() {
        _isProcessingFilter = true;
      });
    }

    try {
      await _cameraPlugin.setFilterEnum(filter);
      setState(() {
        _currentFilter = filter;
        _showFilterSelector = false; // Close filter selector after selection
        _showIntensityControls =
            filter.supportsIntensity; // Show intensity controls if supported
        _isProcessingFilter = false;
      });

      // Show feedback for successful filter application
      if (isAdvancedFilter) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${filter.displayName} filter applied successfully'),
            duration: Duration(seconds: 2),
            backgroundColor: Colors.green.withOpacity(0.8),
          ),
        );
      }
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set filter: $e';
        _isProcessingFilter = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to apply ${filter.displayName} filter'),
          duration: Duration(seconds: 3),
          backgroundColor: Colors.red.withOpacity(0.8),
        ),
      );
    }
  }

  Future<void> _setFilterIntensity(double intensity) async {
    if (!_isCameraInitialized) return;

    try {
      await _cameraPlugin.setFilterIntensity(intensity);
      setState(() {
        _filterIntensity = intensity;
      });
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set filter intensity: $e';
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to set filter intensity'),
          duration: Duration(seconds: 2),
          backgroundColor: Colors.red.withOpacity(0.8),
        ),
      );
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

  /// Get filters for the selected category (0 = Basic, 1 = Advanced)
  List<CameraFilter> _getFiltersForCategory(int category) {
    switch (category) {
      case 0:
        return CameraFilter.basicFilters;
      case 1:
        return CameraFilter.advancedFilters;
      default:
        return CameraFilter.basicFilters;
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
        // Error handled by state management
      }
    }
  }

  void _startListeningToOrientation() {
    if (_orientationSubscription != null) {
      _orientationSubscription!.cancel(); // Hủy subscription cũ nếu có
    }
    _orientationSubscription = _cameraPlugin.orientationStream.listen(
      (OrientationData event) {
        if (kDebugMode) {
          print('[Flutter - Orientation] Received: ${event.toString()}');
        }
        // Gọi hàm cập nhật imageAnalysis của bạn dựa trên 'event'
        // Ví dụ: sử dụng event.deviceOrientation hoặc event.uiOrientation
        // để gọi một phương thức trên _cameraPlugin nếu cần thiết để native cập nhật ImageAnalysis.
        // Dựa trên cấu trúc plugin, có vẻ bạn sẽ muốn gọi một phương thức như `updateCameraRotation`.
        if (_isCameraInitialized) {
          // Chỉ gọi nếu camera đã khởi tạo để tránh lỗi
          try {
            // Quyết định xem nên sử dụng deviceOrientation hay uiOrientation.
            // Thông thường deviceOrientation phù hợp hơn cho việc điều chỉnh camera.
            if (kDebugMode) {
              print('[Flutter - Orientation] Preparing to call updateCameraRotation. Event: ${event.toString()}, DeviceOrientation: ${event.deviceOrientation}, Degrees: ${event.deviceOrientation.degrees}');
            }
            _cameraPlugin.updateCameraRotation(event.deviceOrientation);
            if (kDebugMode) {
              print('[Flutter - Orientation] Called updateCameraRotation with ${event.deviceOrientation} (Degrees: ${event.deviceOrientation.degrees})');
            }
          } catch (e) {
            if (kDebugMode) {
              print('[Flutter - Orientation] Error calling updateCameraRotation: $e');
            }
            setState(() {
              _lastErrorMessage = 'Error updating camera for orientation: $e';
            });
          }
        }
      },
      onError: (dynamic error) {
        if (kDebugMode) {
          print('[Flutter - Orientation] Error in orientation stream: ${error.toString()}');
        }
        setState(() {
          _lastErrorMessage = 'Orientation stream error: ${error.toString()}';
        });
      },
      onDone: () {
        if (kDebugMode) {
          print('[Flutter - Orientation] Orientation stream closed.');
        }
      },
      cancelOnError: true,
    );
    if (kDebugMode) {
      print('[Flutter - Orientation] Started listening to orientation events.');
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
    _orientationSubscription?.cancel();
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

            // Filter selector panel - Enhanced with categories
            if (_showFilterSelector)
              Positioned(
                bottom: 130,
                left: 0,
                right: 0,
                child: Container(
                  height: 140,
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.8),
                    borderRadius: BorderRadius.vertical(
                      top: Radius.circular(16),
                    ),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.1),
                      width: 1,
                    ),
                  ),
                  child: Column(
                    children: [
                      // Drag handle
                      Container(
                        margin: const EdgeInsets.only(top: 8, bottom: 4),
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.3),
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),

                      // Category tabs
                      Container(
                        height: 36,
                        margin: const EdgeInsets.symmetric(horizontal: 16),
                        decoration: BoxDecoration(
                          color: Colors.grey[900],
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: GestureDetector(
                                onTap:
                                    () => setState(
                                      () => _selectedFilterCategory = 0,
                                    ),
                                child: Container(
                                  decoration: BoxDecoration(
                                    color:
                                        _selectedFilterCategory == 0
                                            ? Colors.amber
                                            : Colors.transparent,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Center(
                                    child: Text(
                                      'Basic',
                                      style: TextStyle(
                                        color:
                                            _selectedFilterCategory == 0
                                                ? Colors.black
                                                : Colors.white,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 14,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            Expanded(
                              child: GestureDetector(
                                onTap:
                                    () => setState(
                                      () => _selectedFilterCategory = 1,
                                    ),
                                child: Container(
                                  decoration: BoxDecoration(
                                    color:
                                        _selectedFilterCategory == 1
                                            ? Colors.amber
                                            : Colors.transparent,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Center(
                                    child: Text(
                                      'Advanced',
                                      style: TextStyle(
                                        color:
                                            _selectedFilterCategory == 1
                                                ? Colors.black
                                                : Colors.white,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 14,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),

                      const SizedBox(height: 8),

                      // Filter list
                      Expanded(
                        child: ListView(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          children:
                              _getFiltersForCategory(_selectedFilterCategory)
                                  .map(
                                    (filter) => FilterButton(
                                      name: filter.displayName,
                                      filter: filter,
                                      currentFilter: _currentFilter,
                                      onPressed: _setFilter,
                                    ),
                                  )
                                  .toList(),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            // Filter processing indicator
            if (_isProcessingFilter)
              Positioned(
                bottom: 130,
                left: 0,
                right: 0,
                child: Container(
                  height: 140,
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.7),
                    borderRadius: BorderRadius.vertical(
                      top: Radius.circular(16),
                    ),
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircularProgressIndicator(
                          color: Colors.amber,
                          strokeWidth: 3,
                        ),
                        SizedBox(height: 12),
                        Text(
                          'Applying Advanced Filter...',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),

            // Filter intensity controls
            if (_showIntensityControls && _currentFilter.supportsIntensity)
              Positioned(
                bottom: 130,
                left: 16,
                right: 16,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: Colors.amber.withOpacity(0.3),
                      width: 1,
                    ),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        children: [
                          Text(
                            'Intensity',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          Spacer(),
                          Text(
                            '${(_filterIntensity * 100).round()}%',
                            style: TextStyle(
                              color: Colors.amber,
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          SizedBox(width: 8),
                          IconButton(
                            icon: Icon(
                              Icons.close,
                              color: Colors.white,
                              size: 20,
                            ),
                            onPressed:
                                () => setState(
                                  () => _showIntensityControls = false,
                                ),
                            padding: EdgeInsets.zero,
                            constraints: BoxConstraints(),
                          ),
                        ],
                      ),
                      Slider(
                        value: _filterIntensity,
                        min: 0.0,
                        max: 1.0,
                        divisions: 20,
                        onChanged: _setFilterIntensity,
                        activeColor: Colors.amber,
                        inactiveColor: Colors.white30,
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
                    // Intensity controls button - only show for supported filters
                    if (_currentFilter.supportsIntensity)
                      IconButton(
                        icon: Icon(
                          Icons.tune,
                          color:
                              _showIntensityControls
                                  ? Colors.amber
                                  : Colors.white,
                        ),
                        onPressed:
                            () => setState(
                              () =>
                                  _showIntensityControls =
                                      !_showIntensityControls,
                            ),
                        tooltip: 'Filter Intensity',
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

// Enhanced FilterButton widget with better categorization support
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

  bool get _isAdvancedFilter => CameraFilter.advancedFilters.contains(filter);

  @override
  Widget build(BuildContext context) {
    final isSelected = filter == currentFilter;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Button with enhanced visual feedback
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () => onPressed(filter),
              customBorder: const CircleBorder(),
              splashColor: Colors.amber.withOpacity(0.3),
              highlightColor: Colors.amber.withOpacity(0.1),
              child: Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color:
                        isSelected
                            ? Colors.amber
                            : _isAdvancedFilter
                            ? Colors.blue.withOpacity(0.6)
                            : Colors.grey.withOpacity(0.4),
                    width: isSelected ? 3 : 2,
                  ),
                  color:
                      isSelected
                          ? Colors.amber.withOpacity(0.2)
                          : Colors.grey[850],
                  gradient:
                      _isAdvancedFilter && !isSelected
                          ? LinearGradient(
                            colors: [
                              Colors.blue.withOpacity(0.1),
                              Colors.purple.withOpacity(0.1),
                            ],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          )
                          : null,
                ),
                child: Stack(
                  children: [
                    // Main filter icon/text
                    Center(child: _getFilterIcon()),
                    // Advanced filter indicator
                    if (_isAdvancedFilter && !isSelected)
                      Positioned(
                        top: 4,
                        right: 4,
                        child: Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: Colors.blue,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 0.5),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 6),
          // Enhanced label with better styling
          Container(
            constraints: const BoxConstraints(maxWidth: 60),
            child: Text(
              name,
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: isSelected ? Colors.amber : Colors.white,
                fontSize: 10,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                shadows: [
                  Shadow(
                    offset: const Offset(0, 1),
                    blurRadius: 2,
                    color: Colors.black.withOpacity(0.7),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _getFilterIcon() {
    // Custom icons for specific filters
    IconData? iconData;
    switch (filter) {
      case CameraFilter.none:
        iconData = Icons.no_photography_outlined;
        break;
      case CameraFilter.sepia:
        iconData = Icons.palette_outlined;
        break;
      case CameraFilter.mono:
        iconData = Icons.monochrome_photos_outlined;
        break;
      case CameraFilter.negative:
        iconData = Icons.invert_colors_outlined;
        break;
      case CameraFilter.vintage:
        iconData = Icons.camera_outlined;
        break;
      case CameraFilter.cool:
        iconData = Icons.ac_unit_outlined;
        break;
      case CameraFilter.warm:
        iconData = Icons.wb_sunny_outlined;
        break;
      case CameraFilter.blur:
        iconData = Icons.blur_on_outlined;
        break;
      case CameraFilter.sharpen:
        iconData = Icons.auto_fix_high_outlined;
        break;
      case CameraFilter.edge:
        iconData = Icons.crop_outlined;
        break;
      default:
        iconData = Icons.filter_vintage_outlined;
        break;
    }

    final isSelected = filter == currentFilter;

    return Icon(
      iconData,
      size: 20,
      color:
          isSelected
              ? Colors.amber
              : _isAdvancedFilter
              ? Colors.blue.withOpacity(0.9)
              : Colors.white.withOpacity(0.9),
    );
  }
}
