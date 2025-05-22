import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'beauty_camera_plugin_platform_interface.dart';

/// An implementation of [BeautyCameraPluginPlatform] that uses method channels.
class MethodChannelBeautyCameraPlugin extends BeautyCameraPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('com.example/beauty_camera_plugin'); // Updated channel name

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<void> initializeCamera() async {
    await methodChannel.invokeMethod<void>('initializeCamera');
  }

  @override
  Future<String?> takePicture() async {
    try {
      debugPrint('Sending takePicture request to native code');
      final String? filePath = await methodChannel.invokeMethod<String>('takePicture');
      debugPrint('Received response from takePicture: $filePath');
      return filePath;
    } catch (e) {
      debugPrint('Error during takePicture: $e');
      rethrow; // Propagate error to be handled in UI
    }
  }

  @override
  Future<void> setFilter(String filterType) async {
    await methodChannel.invokeMethod<void>('setFilter', {'filterType': filterType});
  }

  @override
  Future<void> switchCamera() async {
    await methodChannel.invokeMethod<void>('switchCamera');
  }

  @override
  Future<String?> toggleFlash() async {
    final String? newFlashMode = await methodChannel.invokeMethod<String>('toggleFlash');
    return newFlashMode;
  }

  @override
  Future<void> setZoom(double zoom) async {
    await methodChannel.invokeMethod<void>('setZoom', {'zoom': zoom});
  }

  @override
  Future<void> dispose() async {
    await methodChannel.invokeMethod<void>('dispose');
  }
}
