import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'beauty_camera_plugin_method_channel.dart';

/// Enum for flash modes, mirroring potential native enums or states.
enum FlashMode { off, on, auto }

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
}
