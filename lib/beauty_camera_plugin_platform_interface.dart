import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'beauty_camera_plugin_method_channel.dart';
import 'src/enums.dart';

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

  /// Initialize the camera with the given facing direction and resolution preset.
  Future<Map<String, dynamic>> initializeCamera({
    CameraFacing facing = CameraFacing.back,
    ResolutionPreset resolutionPreset = ResolutionPreset.high,
  }) {
    throw UnimplementedError('initializeCamera() has not been implemented.');
  }

  /// Take a picture and save it to the given path.
  Future<Map<String, dynamic>> takePicture(String savePath) {
    throw UnimplementedError('takePicture() has not been implemented.');
  }

  /// Set the filter to be applied to the camera preview.
  Future<Map<String, dynamic>> setFilter(FilterType filter) {
    throw UnimplementedError('setFilter() has not been implemented.');
  }

  /// Set the flash mode for the camera.
  Future<Map<String, dynamic>> setFlashMode(FlashMode mode) {
    throw UnimplementedError('setFlashMode() has not been implemented.');
  }

  /// Set the focus point for the camera.
  ///
  /// x and y are normalized coordinates (0.0 to 1.0) where (0,0) is the top-left
  /// corner of the screen and (1,1) is the bottom-right corner.
  Future<Map<String, dynamic>> setFocusPoint(double x, double y) {
    throw UnimplementedError('setFocusPoint() has not been implemented.');
  }

  /// Dispose the camera resources.
  Future<Map<String, dynamic>> disposeCamera() {
    throw UnimplementedError('disposeCamera() has not been implemented.');
  }
}
