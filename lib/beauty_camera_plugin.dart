import 'beauty_camera_plugin_platform_interface.dart';
export 'beauty_camera_plugin_platform_interface.dart' show FlashMode;

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
      print('Error in takePicture(): $e');
      rethrow;
    }
  }

  Future<void> setFilter(String filterType) {
    return BeautyCameraPluginPlatform.instance.setFilter(filterType);
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
}
