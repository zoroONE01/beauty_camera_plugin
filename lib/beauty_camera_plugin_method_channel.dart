import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'beauty_camera_plugin_platform_interface.dart';
import 'src/enums.dart';

/// An implementation of [BeautyCameraPluginPlatform] that uses method channels.
class MethodChannelBeautyCameraPlugin extends BeautyCameraPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('beauty_camera_plugin');

  @override
  Future<Map<String, dynamic>> initializeCamera({
    CameraFacing facing = CameraFacing.back,
    ResolutionPreset resolutionPreset = ResolutionPreset.high,
  }) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'initializeCamera',
      {
        'facing': facing.toString().split('.').last,
        'resolutionPreset': resolutionPreset.toString().split('.').last,
      },
    );
    return _convertDynamicToStringDynamic(result!);
  }

  @override
  Future<Map<String, dynamic>> takePicture(String savePath) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'takePicture',
      {'savePath': savePath},
    );
    return _convertDynamicToStringDynamic(result!);
  }

  @override
  Future<Map<String, dynamic>> setFilter(FilterType filter) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'setFilter',
      {'filter': filter.toString().split('.').last},
    );
    return _convertDynamicToStringDynamic(result!);
  }

  @override
  Future<Map<String, dynamic>> setFlashMode(FlashMode mode) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'setFlashMode',
      {'mode': mode.toString().split('.').last},
    );
    return _convertDynamicToStringDynamic(result!);
  }
  
  @override
  Future<Map<String, dynamic>> setFocusPoint(double x, double y) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'setFocusPoint',
      {'x': x, 'y': y},
    );
    return _convertDynamicToStringDynamic(result!);
  }

  @override
  Future<Map<String, dynamic>> disposeCamera() async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>('disposeCamera');
    return _convertDynamicToStringDynamic(result!);
  }
  
  /// Convert a map with dynamic keys to a map with string keys
  Map<String, dynamic> _convertDynamicToStringDynamic(Map<dynamic, dynamic> map) {
    return map.map((key, value) => MapEntry(key.toString(), value));
  }
}
