import 'dart:async';
import 'package:flutter/foundation.dart';
import 'beauty_camera_plugin_platform_interface.dart';

class BeautyCameraController extends ChangeNotifier {
  BeautyCameraController() {
    _init();
  }

  bool _isDisposed = false;
  bool _isCameraInitialized = false;
  String? _lastErrorMessage;
  FlashMode _currentFlashMode = FlashMode.off; // Default flash mode
  CameraFilter _currentFilter = CameraFilter.none;
  double _currentZoomLevel = 0.0;
  OrientationData? _currentOrientationData;

  double _currentFilterIntensity = 1.0;
  bool _showFilterIntensity = false;
  double _currentExposure = 0.0;
  bool _isAutoFocusEnabled = true;

  // Method channel and event channel from platform interface
  final BeautyCameraPluginPlatform _platform =
      BeautyCameraPluginPlatform.instance;

  StreamSubscription<OrientationData>? _orientationSubscription;

  bool get isCameraInitialized => _isCameraInitialized;
  String? get lastErrorMessage => _lastErrorMessage;
  FlashMode get currentFlashMode => _currentFlashMode;
  CameraFilter get currentFilter => _currentFilter;
  double get currentZoomLevel => _currentZoomLevel;
  OrientationData? get currentOrientationData => _currentOrientationData;
  double get currentFilterIntensity => _currentFilterIntensity;
  bool get showFilterIntensity => _showFilterIntensity;
  double get currentExposure => _currentExposure;
  bool get isAutoFocusEnabled => _isAutoFocusEnabled;

  Future<void> _init() async {
    // Listen to orientation changes
    _orientationSubscription = _platform.orientationStream.listen(
      (orientationData) {
        _currentOrientationData = orientationData;
        // Notify listeners if you want the UI to react to orientation data changes directly
        // notifyListeners();
        // Or, you might want to pass this data to the native side if needed for preview/capture
        // For example, if the native side needs UI orientation for rotations
        // _platform.updateCameraRotation(orientationData.uiOrientation);
      },
      onError: (error) {
        if (kDebugMode) {
          print(
            '[BeautyCameraController] Error listening to orientation stream: $error',
          );
        }
        _lastErrorMessage = 'Error with orientation stream: $error';
        notifyListeners();
      },
    );
  }

  Future<void> initializeCamera() async {
    if (_isDisposed || _isCameraInitialized) return;
    try {
      await _platform.initializeCamera();
      _isCameraInitialized = true;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to initialize camera: $e';
      _isCameraInitialized = false;
    }
    notifyListeners();
  }

  Future<String?> takePicture() async {
    if (!_isCameraInitialized || _isDisposed) {
      _lastErrorMessage = 'Camera not initialized or controller disposed.';
      notifyListeners();
      return null;
    }
    try {
      final String? imagePath = await _platform.takePicture();
      _lastErrorMessage = null;
      notifyListeners(); // Notify even if no direct state change, for event logging
      return imagePath;
    } catch (e) {
      _lastErrorMessage = 'Failed to take picture: $e';
      notifyListeners();
      return null;
    }
  }

  Future<void> setFilter(CameraFilter filter) async {
    if (!_isCameraInitialized || _isDisposed) return;
    try {
      await _platform.setFilterEnum(filter);
      _currentFilter = filter;
      _showFilterIntensity = filter != CameraFilter.none && filter.supportsIntensity;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set filter: $e';
    }
    notifyListeners();
  }

  Future<void> setFilterIntensity(double intensity) async {
    if (!_isCameraInitialized || _isDisposed) return;
    if (intensity < 0.0 || intensity > 1.0) {
      _lastErrorMessage = 'Intensity must be between 0.0 and 1.0';
      notifyListeners();
      return;
    }
    try {
      await _platform.setFilterIntensity(intensity);
      _currentFilterIntensity = intensity;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set filter intensity: $e';
    }
    notifyListeners();
  }

  Future<void> setExposure(double exposure) async {
    if (!_isCameraInitialized || _isDisposed) return;
    if (exposure < -2.0 || exposure > 2.0) {
      _lastErrorMessage = 'Exposure must be between -2.0 and 2.0';
      notifyListeners();
      return;
    }
    try {
      await _platform.setExposure(exposure);
      _currentExposure = exposure;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set exposure: $e';
    }
    notifyListeners();
  }

  Future<void> setFocusPoint(double x, double y) async {
    if (!_isCameraInitialized || _isDisposed) return;
    try {
      await _platform.setFocusPoint(x, y);
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set focus point: $e';
    }
    notifyListeners();
  }

  Future<void> setAutoFocus(bool enabled) async {
    if (!_isCameraInitialized || _isDisposed) return;
    try {
      await _platform.setAutoFocus(enabled);
      _isAutoFocusEnabled = enabled;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set auto focus: $e';
    }
    notifyListeners();
  }

  Future<void> switchCamera() async {
    if (!_isCameraInitialized || _isDisposed) return;
    try {
      await _platform.switchCamera();
      // Reset zoom and potentially other camera-specific states
      _currentZoomLevel = 0.0;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to switch camera: $e';
    }
    notifyListeners();
  }

  Future<void> toggleFlash() async {
    if (!_isCameraInitialized || _isDisposed) return;
    try {
      final String? newFlashModeString = await _platform.toggleFlash();
      if (newFlashModeString != null) {
        // Assuming the native side returns one of 'off', 'on', 'auto'
        if (newFlashModeString == 'on') {
          _currentFlashMode = FlashMode.on;
        } else if (newFlashModeString == 'auto') {
          _currentFlashMode = FlashMode.auto;
        } else {
          _currentFlashMode = FlashMode.off;
        }
      }
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to toggle flash: $e';
    }
    notifyListeners();
  }

  // Explicitly set flash mode
  Future<void> setFlashMode(FlashMode mode) async {
    if (!_isCameraInitialized || _isDisposed) return;
    // This method would need to be added to the platform interface and native side
    // For now, we'll simulate by cycling through toggleFlash or assume toggleFlash handles it.
    // The current `toggleFlash` returns the new state, so we can use that.
    // To implement this properly, you'd add:
    // await _platform.setFlashMode(mode);
    // For now, let's adjust _currentFlashMode and rely on toggleFlash or a new native method.
    // This is a placeholder if you want direct setFlashMode.
    // For the example, we'll use the existing toggleFlash logic.
    // If you need direct set, the native side must support it.
    _currentFlashMode = mode; // Optimistically update
    // Potentially call a native method here if it exists:
    // await _platform.setFlashMode(mode.toString().split('.').last);
    notifyListeners();
  }

  Future<void> setZoom(double zoom) async {
    if (!_isCameraInitialized || _isDisposed) return;
    // Clamp zoom between 0.0 and 1.0 as per instructions
    final clampedZoom = zoom.clamp(0.0, 1.0);
    try {
      await _platform.setZoom(clampedZoom);
      _currentZoomLevel = clampedZoom;
      _lastErrorMessage = null;
    } catch (e) {
      _lastErrorMessage = 'Failed to set zoom: $e';
    }
    notifyListeners();
  }

  @override
  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _orientationSubscription?.cancel();
    _platform.dispose().catchError((e) {
      if (kDebugMode) {
        print("Error disposing platform resources: $e");
      }
    });
    super.dispose();
  }
}
