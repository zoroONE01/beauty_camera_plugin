import 'beauty_camera_plugin_platform_interface.dart';
export 'beauty_camera_plugin_platform_interface.dart' show FlashMode, CameraOrientation, OrientationData, CameraFilter;

class BeautyCameraPlugin {
  Future<String?> getPlatformVersion() {
    return BeautyCameraPluginPlatform.instance.getPlatformVersion();
  }

  Future<void> initializeCamera() {
    return BeautyCameraPluginPlatform.instance.initializeCamera();
  }

  Future<String?> takePicture() async {
    try {
      return await BeautyCameraPluginPlatform.instance.takePicture();
    } catch (e) {
      // Log error and rethrow for UI handling
      rethrow;
    }
  }

  Future<void> setFilter(String filterType) {
    return BeautyCameraPluginPlatform.instance.setFilter(filterType);
  }

  Future<void> setFilterEnum(CameraFilter filter) {
    return BeautyCameraPluginPlatform.instance.setFilterEnum(filter);
  }

  /// Set the intensity of the current filter.
  /// [intensity] should be between 0.0 (no effect) and 1.0 (full effect).
  Future<void> setFilterIntensity(double intensity) {
    return BeautyCameraPluginPlatform.instance.setFilterIntensity(intensity);
  }

  Future<void> switchCamera() {
    return BeautyCameraPluginPlatform.instance.switchCamera();
  }

  Future<String?> toggleFlash() {
    return BeautyCameraPluginPlatform.instance.toggleFlash();
  }

  Future<void> setZoom(double zoom) {
    return BeautyCameraPluginPlatform.instance.setZoom(zoom);
  }

  Future<void> dispose() {
    return BeautyCameraPluginPlatform.instance.dispose();
  }

  /// Stream of orientation changes from the device
  Stream<OrientationData> get orientationStream {
    return BeautyCameraPluginPlatform.instance.orientationStream;
  }

  Future<void> updateCameraRotation(CameraOrientation rotation) {
    return BeautyCameraPluginPlatform.instance.updateCameraRotation(rotation);
  }
}
