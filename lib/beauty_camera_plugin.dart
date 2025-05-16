import 'package:flutter/material.dart';

import 'beauty_camera_plugin_platform_interface.dart';
import 'src/enums.dart';

export 'src/enums.dart';
export 'src/camera_preview.dart';

/// BeautyCameraPlugin is a Flutter plugin that provides access to camera functionality
/// using the CameraX API on Android with real-time filter capabilities.
class BeautyCameraPlugin {
  /// Initialize the camera with the given facing direction and resolution preset.
  ///
  /// Returns a Map containing the textureId that can be used with a [Texture] widget.
  Future<Map<String, dynamic>> initializeCamera({
    CameraFacing facing = CameraFacing.back,
    ResolutionPreset resolutionPreset = ResolutionPreset.high,
  }) {
    return BeautyCameraPluginPlatform.instance.initializeCamera(
      facing: facing,
      resolutionPreset: resolutionPreset,
    );
  }

  /// Take a picture and save it to the given path.
  ///
  /// Returns a Map containing 'success' (bool) and 'filePath' (String) if successful.
  Future<Map<String, dynamic>> takePicture(String savePath) {
    return BeautyCameraPluginPlatform.instance.takePicture(savePath);
  }

  /// Set the filter to be applied to the camera preview.
  ///
  /// Returns a Map containing 'success' (bool).
  Future<Map<String, dynamic>> setFilter(FilterType filter) {
    return BeautyCameraPluginPlatform.instance.setFilter(filter);
  }

  /// Set the flash mode for the camera.
  ///
  /// Returns a Map containing 'success' (bool).
  Future<Map<String, dynamic>> setFlashMode(FlashMode mode) {
    return BeautyCameraPluginPlatform.instance.setFlashMode(mode);
  }

  /// Set the focus point for the camera.
  ///
  /// x and y are normalized coordinates (0.0 to 1.0) where (0,0) is the top-left
  /// corner of the screen and (1,1) is the bottom-right corner.
  ///
  /// Returns a Map containing 'success' (bool).
  Future<Map<String, dynamic>> setFocusPoint(double x, double y) {
    return BeautyCameraPluginPlatform.instance.setFocusPoint(x, y);
  }

  /// Dispose the camera resources.
  ///
  /// Returns a Map containing 'success' (bool).
  Future<Map<String, dynamic>> disposeCamera() {
    return BeautyCameraPluginPlatform.instance.disposeCamera();
  }
}
