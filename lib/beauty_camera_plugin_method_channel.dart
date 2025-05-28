import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'beauty_camera_plugin_platform_interface.dart';

/// An implementation of [BeautyCameraPluginPlatform] that uses method channels.
class MethodChannelBeautyCameraPlugin extends BeautyCameraPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('com.example/beauty_camera_plugin');

  /// The event channel for orientation updates
  @visibleForTesting
  final orientationEventChannel = const EventChannel('com.example/beauty_camera_plugin/orientation');

  Stream<OrientationData>? _orientationStream;

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
      final String? result = await methodChannel.invokeMethod<String>('takePicture');
      return result;
    } catch (e) {
      rethrow; // Propagate error to be handled in UI
    }
  }

  @override
  Future<void> setFilter(String filterType) async {
    await methodChannel.invokeMethod<void>('setFilter', {'filterType': filterType});
  }

  @override
  Future<void> setFilterEnum(CameraFilter filter) async {
    return setFilter(filter.id);
  }

  @override
  Future<void> setFilterIntensity(double intensity) async {
    await methodChannel.invokeMethod<void>('setFilterIntensity', {'intensity': intensity});
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

  @override
  Stream<OrientationData> get orientationStream {
    _orientationStream ??= orientationEventChannel
        .receiveBroadcastStream()
        .map((dynamic event) {
          // Log the raw event and its type
          if (kDebugMode) {
            print('[MethodChannelBeautyCameraPlugin] Received event from orientationEventChannel: $event, Type: ${event.runtimeType}');
          }

          if (event is Map) { // Check if it's a Map first
            try {
              // Try to cast to the expected type.
              // EventChannel might send Map<Object?, Object?> or similar.
              final castedEvent = Map<String, dynamic>.from(event);
              if (kDebugMode) {
                print('[MethodChannelBeautyCameraPlugin] Event successfully casted to Map<String, dynamic>.');
              }
              return OrientationData.fromMap(castedEvent);
            } catch (e) {
              if (kDebugMode) {
                print('[MethodChannelBeautyCameraPlugin] Failed to cast event (which is a Map) to Map<String, dynamic>: $e. Event data: $event');
              }
              // Fallback if casting fails, though it shouldn't if Kotlin sends a compatible map.
              return OrientationData(
                deviceOrientation: CameraOrientation.portraitUp, // Default
                uiOrientation: CameraOrientation.portraitUp,   // Default
                timestamp: DateTime.now().millisecondsSinceEpoch,
              );
            }
          } else {
            if (kDebugMode) {
              print('[MethodChannelBeautyCameraPlugin] Error: Received event is NOT a Map. Event: $event, Type: ${event.runtimeType}');
            }
            return OrientationData(
              deviceOrientation: CameraOrientation.portraitUp, // Default
              uiOrientation: CameraOrientation.portraitUp,   // Default
              timestamp: DateTime.now().millisecondsSinceEpoch,
            );
          }
        });
    return _orientationStream!;
  }

  @override
  Future<void> updateCameraRotation(CameraOrientation rotation) async {
    try {
      await methodChannel.invokeMethod<void>('updateCameraRotation', {'rotation': rotation.degrees});
    } catch (e) {
      rethrow;
    }
  }
}
