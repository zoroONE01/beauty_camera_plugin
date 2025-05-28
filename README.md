# Beauty Camera Plugin

A powerful Flutter camera plugin with advanced filter capabilities, real-time effects processing, and comprehensive orientation tracking.

## 🚀 Features

- **🎥 Real-time Camera Preview** with OpenGL-based filtering
- **🎨 14 Built-in Filters** organized into Basic and Advanced categories  
- **🎛️ Intensity Controls** for adjustable filters (0-100%)
- **📱 Device Orientation Tracking** with real-time updates
- **⚡ High-Performance Processing** using GPU shaders and CameraX
- **💫 Professional UI** with categorized filter selection
- **📷 Photo Capture** with filter effects applied
- **🔄 Camera Switching** (front/back)
- **⚡ Flash Control** (off/on/auto)
- **🔍 Zoom Control** with smooth gestures

## 🎭 Supported Filters

### Basic Filters

- **None**: No filter applied
- **Sepia**: Vintage sepia tone effect
- **Grayscale**: Black and white conversion  
- **Negative**: Color inversion effect
- **Vintage**: Nostalgic film effect with vignette
- **Cool**: Cool temperature adjustment
- **Warm**: Warm temperature adjustment

### Advanced Filters  

- **Blur**: Gaussian blur effect for artistic shots
- **Sharpen**: Edge enhancement for crisp details
- **Edge**: Edge detection for artistic outlines
- **Vignette**: Darkened edges effect (supports intensity)
- **Contrast**: Enhanced contrast adjustment (supports intensity)
- **Brightness**: Brightness level adjustment (supports intensity)

## 📱 Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| **Android** | ✅ **Fully Supported** | CameraX + OpenGL ES 2.0 |
| **iOS** | 🚧 **In Development** | AVFoundation + Metal (Coming Soon) |

## 📦 Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  beauty_camera_plugin: ^1.0.0
```

Then run:

```bash
flutter pub get
```

## 🛠️ Usage

### Basic Setup

```dart
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';

class CameraScreen extends StatefulWidget {
  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final BeautyCameraPlugin _cameraPlugin = BeautyCameraPlugin();
  
  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }
  
  Future<void> _initializeCamera() async {
    await _cameraPlugin.initializeCamera();
  }
}
```

### Camera Preview

```dart
// Add camera preview widget to your build method
AndroidView(
  viewType: 'com.example/camera_preview_view',
  creationParams: const <String, dynamic>{},
  creationParamsCodec: const StandardMessageCodec(),
)
```

### Apply Filters

```dart
// Set filter using enum (recommended)
await _cameraPlugin.setFilterEnum(CameraFilter.sepia);

// Set filter using string  
await _cameraPlugin.setFilter('grayscale');

// For filters that support intensity (0.0 - 1.0)
await _cameraPlugin.setFilterIntensity(0.8); // 80% intensity
```

### Take Photos

```dart
Future<void> _takePicture() async {
  final String? imagePath = await _cameraPlugin.takePicture();
  if (imagePath != null) {
    // Handle captured image
    print('Photo saved to: $imagePath');
  }
}
```

## 🚀 Example App

Run the example app to see all features in action:

```bash
cd example
flutter run
```

The example includes:

- **Comprehensive Camera UI** with modern design
- **Filter Categories** with tabbed interface
- **Real-time Orientation Display**
- **Professional Camera Controls**
- **Filter Intensity Sliders**
- **Photo Gallery Integration**

## 🏗️ Architecture

### Android Implementation

- **CameraX** for camera lifecycle management
- **OpenGL ES 2.0** for real-time filter processing
- **Fragment Shaders** for GPU-accelerated effects

- **Camera2 Interop API** for advanced camera controls
- **Kotlin Coroutines** for asynchronous operations
- **Event Channels** for orientation streaming

### iOS Implementation (Coming Soon)

- **AVFoundation** for camera management
- **Metal** for GPU-accelerated filtering
- **Core Image** for advanced effects processing

## 📝 API Reference

### Main Plugin Class

```dart
class BeautyCameraPlugin {
  // Camera lifecycle
  Future<void> initializeCamera();
  Future<void> dispose();
  
  // Photo capture
  Future<String?> takePicture();
  
  // Filter management
  Future<void> setFilterEnum(CameraFilter filter);
  Future<void> setFilter(String filterType);
  Future<void> setFilterIntensity(double intensity);
  
  // Camera controls
  Future<void> switchCamera();
  Future<String?> toggleFlash();
  Future<void> setZoom(double zoom);
  
  // Orientation tracking
  Stream<OrientationData> get orientationStream;
}
```

### Filter Enums

```dart
enum CameraFilter {
  none, sepia, grayscale, negative, vintage, 
  cool, warm, blur, sharpen, edge, 
  vignette, contrast, brightness
}

enum FlashMode { off, on, auto }

enum CameraOrientation { 
  portrait,      // 0°
  landscape,     // 90°  
  portraitDown,  // 180°
  landscapeDown  // 270°
}
```

### Orientation Data

```dart
class OrientationData {
  final CameraOrientation deviceOrientation;
  final CameraOrientation uiOrientation;
  final int timestamp;
}
```

## 🔧 Requirements

### Android

- **Minimum SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **CameraX**: 1.3.3+
- **OpenGL ES**: 2.0+

### iOS (Coming Soon)

- **Minimum iOS**: 12.0+
- **Swift**: 5.0+
- **Metal**: For GPU acceleration

## 🎨 Customization

### Adding Custom Filters

You can extend the filter system by modifying the OpenGL shaders:

```glsl
// Custom sepia shader example
precision mediump float;
varying vec2 vTextureCoord;
uniform sampler2D sTexture;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(gray * 1.2, gray * 1.0, gray * 0.8, color.a);
}
```

### UI Theming

The plugin supports custom theming through Flutter's Theme system:

```dart
Theme(
  data: ThemeData.dark().copyWith(
    primaryColor: Colors.amber,
    accentColor: Colors.amberAccent,
  ),
  child: CameraScreen(),
)
```

## 🤝 Contributing

Contributions are welcome! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

1. Clone the repository
2. Install dependencies: `flutter pub get`
3. Run tests: `flutter test`
4. Run example: `cd example && flutter run`

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Documentation**: [Full API docs](https://pub.dev/documentation/beauty_camera_plugin/latest/)
- **Issues**: [GitHub Issues](https://github.com/your-repo/beauty_camera_plugin/issues)
- **Discord**: [Flutter Community](https://discord.gg/flutter)

## 🙏 Acknowledgments

- **CameraX Team** for the excellent camera API
- **Flutter Community** for continuous support
- **OpenGL ES** documentation and examples

```

### Advanced Features

```dart
// Switch between cameras
await _cameraPlugin.switchCamera();

// Toggle flash modes
final String? flashMode = await _cameraPlugin.toggleFlash();

// Set zoom level (0.0 to 1.0)
await _cameraPlugin.setZoom(0.5);

// Dispose resources
await _cameraPlugin.dispose();
```

## API Reference

### Core Methods

- `initializeCamera()` - Initialize the camera system
- `takePicture()` - Capture photo with current filter applied
- `setFilterEnum(CameraFilter filter)` - Apply filter using enum
- `setFilterIntensity(double intensity)` - Adjust filter intensity (0.0-1.0)
- `switchCamera()` - Toggle between front/back cameras
- `toggleFlash()` - Cycle through flash modes
- `setZoom(double zoom)` - Set zoom level
- `dispose()` - Release camera resources

### Filter Categories

```dart
// Access filter categories
List<CameraFilter> basicFilters = CameraFilter.basicFilters;
List<CameraFilter> advancedFilters = CameraFilter.advancedFilters;

// Check if filter supports intensity
bool supportsIntensity = CameraFilter.vignette.supportsIntensity;
```

## Platform Support

- ✅ **Android** (API 21+) - CameraX with OpenGL ES 2.0
- ❌ **iOS** - Coming soon

## Requirements

### Android

- Minimum SDK: 21 (Android 5.0)
- CameraX 1.3.3+
- OpenGL ES 2.0 support

## Example App

The plugin includes a comprehensive example app demonstrating all features:

- Categorized filter selection with tabs
- Real-time intensity controls
- Professional camera interface
- Photo capture and preview

Run the example:

```bash
cd example
flutter run
```

## Technical Implementation

The plugin uses:

- **CameraX** for modern Android camera API
- **OpenGL ES 2.0** for real-time filter processing
- **Fragment Shaders** for GPU-accelerated effects
- **Platform Views** for Flutter-native integration

## Contributing

Contributions are welcome! Please read our contributing guidelines and submit pull requests to the main repository.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
