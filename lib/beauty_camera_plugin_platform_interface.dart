import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'beauty_camera_plugin_method_channel.dart';

/// Enum for flash modes, mirroring potential native enums or states.
enum FlashMode { off, on, auto }

/// Enhanced CameraFilter enum with new advanced filters
enum CameraFilter {
  // Basic filters
  none('none', 'None', 'No filter applied'),
  sepia('sepia', 'Sepia', 'Vintage sepia tone effect'),
  mono('mono', 'Mono', 'Black and white conversion'),
  negative('negative', 'Negative', 'Color inversion effect'),
  vintage('vintage', 'Vintage', 'Nostalgic film effect'),
  cool('cool', 'Cool', 'Cool temperature adjustment'),
  warm('warm', 'Warm', 'Warm temperature adjustment'),
  
  // Advanced filters
  blur('blur', 'Blur', 'Gaussian blur effect'),
  sharpen('sharpen', 'Sharpen', 'Edge enhancement for crisp details'),
  edge('edge', 'Edge', 'Edge detection for artistic outlines'),
  
  // New advanced filters
  vignette('vignette', 'Vignette', 'Dark border fade effect'),
  contrast('contrast', 'Contrast', 'Enhance image contrast'),
  brightness('brightness', 'Brightness', 'Adjust image brightness');

  const CameraFilter(this.id, this.displayName, this.description);
  
  /// The filter identifier used by the native platform
  final String id;
  
  /// The human-readable name for display in UI
  final String displayName;
  
  /// Description of the filter effect
  final String description;
  
  /// Convert from string ID to enum value
  static CameraFilter fromId(String id) {
    return CameraFilter.values.firstWhere(
      (filter) => filter.id == id,
      orElse: () => CameraFilter.none,
    );
  }
  
  /// Get all available filters except aliases
  static List<CameraFilter> get availableFilters => [
    CameraFilter.none,
    CameraFilter.sepia,
    CameraFilter.mono,
    CameraFilter.negative,
    CameraFilter.vintage,
    CameraFilter.cool,
    CameraFilter.warm,
    CameraFilter.blur,
    CameraFilter.sharpen,
    CameraFilter.edge,
    CameraFilter.vignette,
    CameraFilter.contrast,
    CameraFilter.brightness,
  ];

  /// Get basic color filters only
  static List<CameraFilter> get basicFilters => [
    CameraFilter.none,
    CameraFilter.sepia,
    CameraFilter.mono,
    CameraFilter.negative,
    CameraFilter.vintage,
    CameraFilter.cool,
    CameraFilter.warm,
  ];

  /// Get advanced processing filters only
  static List<CameraFilter> get advancedFilters => [
    CameraFilter.blur,
    CameraFilter.sharpen,
    CameraFilter.edge,
    CameraFilter.vignette,
    CameraFilter.contrast,
    CameraFilter.brightness,
  ];
  
  /// Check if this filter supports intensity adjustment
  bool get supportsIntensity => this != CameraFilter.none;
}

/// Enum for device orientation values
enum CameraOrientation {
  portraitUp(0),
  landscapeLeft(90),
  portraitDown(180),
  landscapeRight(270);

  const CameraOrientation(this.degrees);
  final int degrees;

  static CameraOrientation fromDegrees(int degrees) {
    switch (degrees) {
      case 0:
        return CameraOrientation.portraitUp;
      case 90:
        return CameraOrientation.landscapeLeft;
      case 180:
        return CameraOrientation.portraitDown;
      case 270:
        return CameraOrientation.landscapeRight;
      default:
        return CameraOrientation.portraitUp;
    }
  }
}

/// Data class representing orientation information
class OrientationData {
  final CameraOrientation deviceOrientation;
  final CameraOrientation uiOrientation;
  final int timestamp;

  const OrientationData({
    required this.deviceOrientation,
    required this.uiOrientation,
    required this.timestamp,
  });

  factory OrientationData.fromMap(Map<String, dynamic> map) {
    return OrientationData(
      deviceOrientation: CameraOrientation.fromDegrees(map['deviceOrientation'] ?? 0),
      uiOrientation: CameraOrientation.fromDegrees(map['uiOrientation'] ?? 0),
      timestamp: map['timestamp'] ?? 0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'deviceOrientation': deviceOrientation.degrees,
      'uiOrientation': uiOrientation.degrees,
      'timestamp': timestamp,
    };
  }

  @override
  String toString() {
    return 'OrientationData(device: $deviceOrientation, ui: $uiOrientation, timestamp: $timestamp)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is OrientationData &&
        other.deviceOrientation == deviceOrientation &&
        other.uiOrientation == uiOrientation &&
        other.timestamp == timestamp;
  }

  @override
  int get hashCode {
    return deviceOrientation.hashCode ^ uiOrientation.hashCode ^ timestamp.hashCode;
  }
}

abstract class BeautyCameraPluginPlatform extends PlatformInterface {
  /// Constructs a BeautyCameraPluginPlatform.
  BeautyCameraPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static BeautyCameraPluginPlatform _instance = MethodChannelBeautyCameraPlugin();

  /// The default instance of [BeautyCameraPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelBeautyCameraPlugin].
  static BeautyCameraPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [BeautyCameraPluginPlatform] when
  /// they register themselves.
  static set instance(BeautyCameraPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<void> initializeCamera() {
    throw UnimplementedError('initializeCamera() has not been implemented.');
  }

  Future<String?> takePicture() {
    throw UnimplementedError('takePicture() has not been implemented.');
  }

  Future<void> setFilter(String filterType) {
    throw UnimplementedError('setFilter() has not been implemented.');
  }

  Future<void> setFilterEnum(CameraFilter filter) {
    return setFilter(filter.id);
  }

  /// Set the intensity of the current filter.
  /// [intensity] should be between 0.0 (no effect) and 1.0 (full effect).
  Future<void> setFilterIntensity(double intensity) {
    throw UnimplementedError('setFilterIntensity() has not been implemented.');
  }

  Future<void> switchCamera() {
    throw UnimplementedError('switchCamera() has not been implemented.');
  }

  /// Toggles the flash mode.
  /// Returns the new flash mode as a String (e.g., "on", "off", "auto").
  Future<String?> toggleFlash() {
    throw UnimplementedError('toggleFlash() has not been implemented.');
  }

  Future<void> setZoom(double zoom) {
    throw UnimplementedError('setZoom() has not been implemented.');
  }

  Future<void> dispose() {
    throw UnimplementedError('dispose() has not been implemented.');
  }

  /// Stream of orientation changes
  Stream<OrientationData> get orientationStream {
    throw UnimplementedError('orientationStream has not been implemented.');
  }

  Future<void> updateCameraRotation(CameraOrientation rotation) {
    throw UnimplementedError('updateCameraRotation() has not been implemented.');
  }
}
