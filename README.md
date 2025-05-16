# Beauty Camera Plugin

A high-performance Flutter camera plugin for Android using CameraX API with real-time filters powered by GPUImage.

## Features

- **High-Quality Camera Preview**: Using CameraX for modern camera implementation
- **Real-time Filters**: Apply filters to camera preview in real-time
- **Photo Capture**: Take high-quality photos with adjustable resolution
- **Flash Control**: Toggle between flash modes (On/Off/Auto)
- **Camera Switching**: Easily switch between front and back cameras
- **Customizable Resolution**: Select from various resolution presets

## Installation

Add this to your package's pubspec.yaml file:

```yaml
dependencies:
  beauty_camera_plugin:
    git:
      url: https://github.com/yourusername/beauty_camera_plugin.git
```

## Android Setup

Ensure your app's minSdkVersion is at least 21 (required by CameraX):

```gradle
android {
    defaultConfig {
        minSdkVersion 21
        // other settings...
    }
}
```

Add required permissions to your AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
                 android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

## Usage

### Initializing the Camera

```dart
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class CameraScreen extends StatefulWidget {
  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final _beautyCameraPlugin = BeautyCameraPlugin();
  int? _textureId;
  
  @override
  void initState() {
    super.initState();
    _initCamera();
  }
  
  Future<void> _initCamera() async {
    // Request permissions
    await Permission.camera.request();
    await Permission.storage.request();
    
    // Initialize the camera
    final result = await _beautyCameraPlugin.initializeCamera(
      facing: CameraFacing.BACK,
      resolutionPreset: ResolutionPreset.HIGH,
    );
    
    setState(() {
      _textureId = result['textureId'];
    });
  }
  
  @override
  Widget build(BuildContext context) {
    if (_textureId == null) {
      return const Center(child: CircularProgressIndicator());
    }
    
    return Texture(textureId: _textureId!);
  }
  
  @override
  void dispose() {
    _beautyCameraPlugin.disposeCamera();
    super.dispose();
  }
}
```

### Taking a Picture

```dart
Future<void> takePicture() async {
  final path = '/path/to/save/image.jpg';
  final result = await _beautyCameraPlugin.takePicture(path);
  
  if (result['success'] == true) {
    print('Photo saved to: ${result['filePath']}');
  }
}
```

### Applying Filters

```dart
Future<void> applyFilter(FilterType filter) async {
  await _beautyCameraPlugin.setFilter(filter);
}
```

### Controlling Flash

```dart
Future<void> setFlashMode(FlashMode mode) async {
  await _beautyCameraPlugin.setFlashMode(mode);
}
```

### Switching Camera

```dart
Future<void> switchCamera(CameraFacing facing) async {
  await _beautyCameraPlugin.disposeCamera();
  final result = await _beautyCameraPlugin.initializeCamera(facing: facing);
  setState(() {
    _textureId = result['textureId'];
  });
}
```

## Available Filters

- `FilterType.NONE` - No filter
- `FilterType.SEPIA` - Sepia filter
- `FilterType.GRAYSCALE` - Black and white filter
- `FilterType.INVERT` - Color inversion
- `FilterType.BRIGHTNESS` - Increased brightness
- `FilterType.CONTRAST` - Enhanced contrast
- `FilterType.SATURATION` - Boosted color saturation
- `FilterType.GAMMA` - Gamma correction
- `FilterType.MONOCHROME` - Monochrome effect

## Technical Implementation

This plugin uses:

- Android CameraX API for camera operations
- GPUImage library for real-time filtering
- Custom YUV to RGB conversion for image processing
- Flutter Texture widget for displaying camera preview

## Example App

Check the `example` folder for a complete implementation example.
