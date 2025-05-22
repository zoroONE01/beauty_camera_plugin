# GitHub Copilot Instructions for Flutter Camera Plugin (Android - CameraX with Camera2 Interop Filters)

## 1. Project Overview and Goals

This project aims to develop a high-performance Flutter camera plugin for Android, leveraging the modern CameraX API. Filters are implemented using Camera2 interop API for live preview and bitmap processing for captured images.

The primary goals are:

- Provide robust photo capture capabilities using CameraX.
- Implement and optimize real-time camera preview filters using CameraX with Camera2 interop API.
- Support post-processing filters on captured images using Bitmap and ColorMatrix operations.
- Prioritize native Android implementation in Kotlin for performance and direct access to CameraX features.
- Ensure the plugin uses current and stable versions of all dependent AndroidX and CameraX libraries.

## 2. Technical Stack & Key Libraries

- **Primary Language (Plugin):** Dart (Flutter)
- **Primary Language (Native Android Implementation):** Kotlin.
- **Android Camera API:** **CameraX (AndroidX)** exclusively.
  - Target CameraX versions around `1.3.3` or latest stable (verify current latest). Key components include: `ProcessCameraProvider`, `Preview` use case, `ImageCapture` use case.
- **Image Processing (for Filters on Android):**
  - **Live Preview Filters:**
    - Camera2 Interop API via CameraX to apply real-time effects (mono, negative, solarize, posterize)
    - Direct integration with CameraX Preview use case
  - **Captured Image Filters:**
    - Bitmap processing with ColorMatrix and Canvas for filter effects
    - Post-processing approach for filters not available in Camera2 API (e.g., sepia)
- **Flutter Version:** Latest stable.
- **Android Gradle Plugin & Kotlin Version:** Latest stable.
- **Permissions:** Utilize CameraX's inherent permission handling capabilities or standard Android permission request patterns.
- **Threading:** Use `Executors.newSingleThreadExecutor()` for CameraX background tasks and coroutines for asynchronous operations.

## 3. Architectural Preferences

- **CameraX Centric:** All camera operations must be managed through CameraX use cases.
  - Bind `Preview` and `ImageCapture` use cases to the camera lifecycle correctly.
- **PlatformView for Camera Preview:**
  - Use Flutter's PlatformView to integrate with CameraX `PreviewView` for camera preview display.
  - Register a custom `PlatformViewFactory` and implement the `PlatformView` interface for seamless integration.
- **Camera2 Interop for Filtering:**
  - Use Camera2 Interop API to apply built-in camera effects for real-time preview filters:
    - Mono (grayscale): `CONTROL_EFFECT_MODE_MONO`
    - Negative: `CONTROL_EFFECT_MODE_NEGATIVE`
    - Solarize: `CONTROL_EFFECT_MODE_SOLARIZE`
    - Posterize: `CONTROL_EFFECT_MODE_POSTERIZE`
  - Apply custom filters (like sepia) through post-processing on captured images using `ColorMatrix` and `Canvas`.
- **Flutter Communication:** Use platform channels (MethodChannel) for clear and robust communication between Dart and the native Android (Kotlin) module.
  - The native side manages CameraX operations, with Flutter providing control through method calls and receiving the camera preview through a PlatformView.

## 4. Core Native Plugin Features & Implementation Guides

### 4.1. Photo Capture

- Implement using CameraX `ImageCapture` use case.
- Provide functionalities for taking a picture and saving it to a file.
- Handle metadata (e.g., orientation) correctly.
- Implement flash mode control (methods to set flash: on, off, auto).

### 4.2. Real-time Camera Preview Filters with Camera2 Interop API

This section outlines the implementation of real-time filters using Camera2 Interop API with CameraX. The approach is based on the actual implementation in `CameraManager.kt`.

#### 4.2.1. Implementing Real-time Filters with CameraX and Camera2 Interop API

- **A. CameraX Preview with Camera2 Interop**

  1. **Create and Configure Preview Use Case:**

     ```kotlin
     private fun createPreviewUseCase(): Preview {
         return Preview.Builder()
             .apply {
                 setTargetRotation(
                     previewView?.display?.rotation ?: android.view.Surface.ROTATION_0
                 )
                 if (currentFilterType != "none") {
                     // Apply camera2 specific options for live preview filter
                     val camera2Interop = androidx.camera.camera2.interop.Camera2Interop.Extender(this)
                     applyEffectToPreview(camera2Interop, currentFilterType)
                 }
             }
             .build()
             .also { preview ->
                 if (previewView != null) {
                     preview.setSurfaceProvider(previewView!!.surfaceProvider)
                 }
             }
     }
     ```

  2. **Apply Built-in Camera Effects Using Camera2 Interop:**

     ```kotlin
     private fun applyEffectToPreview(
         interop: androidx.camera.camera2.interop.Camera2Interop.Extender<*>,
         filterType: String
     ) {
         when (filterType.lowercase()) {
             "sepia" -> {
                 // Sepia isn't directly available in CameraMetadata, we'll use this in post-processing
             }
             "grayscale", "mono" -> {
                 interop.setCaptureRequestOption(
                     CaptureRequest.CONTROL_EFFECT_MODE,
                     CameraMetadata.CONTROL_EFFECT_MODE_MONO
                 )
             }
             "negative" -> {
                 interop.setCaptureRequestOption(
                     CaptureRequest.CONTROL_EFFECT_MODE,
                     CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE
                 )
             }
             "solarize" -> {
                 interop.setCaptureRequestOption(
                     CaptureRequest.CONTROL_EFFECT_MODE,
                     CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE
                 )
             }
             "posterize" -> {
                 interop.setCaptureRequestOption(
                     CaptureRequest.CONTROL_EFFECT_MODE,
                     CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE
                 )
             }
             else -> {
                 interop.setCaptureRequestOption(
                     CaptureRequest.CONTROL_EFFECT_MODE,
                     CameraMetadata.CONTROL_EFFECT_MODE_OFF
                 )
             }
         }
     }
     ```

  3. **Setup PlatformView Integration:**
     - Create a `CameraPlatformViewFactory` that registers a view with type `"com.example/camera_preview_view"`.
     - Implement `PlatformView` that returns a `PreviewView` for the Flutter-native bridge.

     ```kotlin
     // In your Flutter plugin class
     override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
         // ...
         flutterPluginBinding
             .platformViewRegistry
             .registerViewFactory(
                 "com.example/camera_preview_view", 
                 CameraPlatformViewFactory(StandardMessageCodec.INSTANCE, this)
             )
         // ...
     }
     
     // PlatformView implementation
     class CameraPlatformView(
         private val context: Context,
         private val id: Int,
         private val creationParams: Map<String, Any>?,
         private val plugin: BeautyCameraPlugin
     ) : PlatformView {
         
         private val previewView: PreviewView
         private val cameraManager = plugin.getCameraManager()
         
         init {
             previewView = PreviewView(context).apply {
                 implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                 scaleType = PreviewView.ScaleType.FILL_CENTER
             }
             
             cameraManager.setPreviewView(previewView)
         }
         
         override fun getView(): View {
             return previewView
         }
         
         override fun dispose() {
             // The camera resources are managed by the CameraManager
         }
     }
     ```

- **B. Filter Setting and Management**

  1. **Set Filter Method:**

     ```kotlin
     fun setFilter(filterType: String) {
         this.currentFilterType = filterType
         applyFilterToCamera()
     }
     
     private fun applyFilterToCamera() {
         val lifecycleOwner = activity as? LifecycleOwner ?: return
         
         // Unbind existing preview use case
         cameraProvider?.unbind(preview)
         
         // Create new preview with filter applied
         preview = createPreviewUseCase()
         
         // Rebind all use cases
         try {
             camera = cameraProvider?.bindToLifecycle(
                 lifecycleOwner,
                 cameraSelector,
                 preview,
                 imageCapture
             )
         } catch (e: Exception) {
             Log.e(TAG, "Use case binding failed during filter application", e)
         }
     }
     ```

  2. **Handling Filters During Capture:**
     - When taking a photo, check if post-processing is needed based on the current filter type.
     - Apply Bitmap-based filters for filters not available in Camera2 API.

     ```kotlin
     fun takePicture(callback: (String?, String?) -> Unit) {
         // ... capture logic ...
         
         imgCapture.takePicture(
             outputOptions,
             ContextCompat.getMainExecutor(context),
             object : ImageCapture.OnImageSavedCallback {
                 override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                     val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                     val filePath = savedUri.path
                     
                     if (currentFilterType != "none" && currentFilterType != "auto") {
                         // Apply filter to the captured image if it's not done in real-time
                         scope.launch(Dispatchers.IO) {
                             try {
                                 applyFilterToSavedImage(photoFile.absolutePath, currentFilterType)
                                 launch(Dispatchers.Main) {
                                     callback(photoFile.absolutePath, null)
                                 }
                             } catch (e: Exception) {
                                 launch(Dispatchers.Main) {
                                     callback(null, "Failed to apply filter: ${e.message}")
                                 }
                             }
                         }
                     } else {
                         // No post-processing needed
                         callback(filePath, null)
                     }
                 }
                 
                 override fun onError(exception: ImageCaptureException) {
                     callback(null, "Photo capture failed: ${exception.message}")
                 }
             }
         )
     }
     ```

- **C. ColorMatrix-based Bitmap Filters Implementation**
  - Implement custom filter effects using ColorMatrix and Canvas for post-processing.
  - Create utility methods for each filter type (e.g., sepia, grayscale).
  
  ```kotlin
  private fun applySepia(bitmap: Bitmap): Bitmap {
      val width = bitmap.width
      val height = bitmap.height
      val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(result)
      
      val sepiaMatrix = ColorMatrix().apply {
          set(floatArrayOf(
              0.393f, 0.769f, 0.189f, 0f, 0f,
              0.349f, 0.686f, 0.168f, 0f, 0f,
              0.272f, 0.534f, 0.131f, 0f, 0f,
              0f, 0f, 0f, 1f, 0f
          ))
      }
      
      val paint = Paint().apply {
          colorFilter = ColorMatrixColorFilter(sepiaMatrix)
      }
      
      canvas.drawBitmap(bitmap, 0f, 0f, paint)
      return result
  }
  
  private fun applyGrayscale(bitmap: Bitmap): Bitmap {
      val width = bitmap.width
      val height = bitmap.height
      val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(result)
      
      val grayscaleMatrix = ColorMatrix().apply {
          setSaturation(0f)
      }
      
      val paint = Paint().apply {
          colorFilter = ColorMatrixColorFilter(grayscaleMatrix)
      }
      
      canvas.drawBitmap(bitmap, 0f, 0f, paint)
      return result
  }
  ```

- **D. Handling Orientation and Device Changes**
  - Update `targetRotation` in Preview and ImageCapture use cases when device orientation changes.
  - Set a proper `scaleType` on the PreviewView to handle aspect ratio differences.
  - Consider implementing an OrientationEventListener to detect device rotation changes.

- **E. Resource Cleanup**
  - Unbind all CameraX use cases: `cameraProvider?.unbindAll()`
  - Shut down the camera executor: `cameraExecutor.shutdown()`
  - Cancel any coroutine scopes: `scope.cancel()`
  - Release the PreviewView reference: `previewView = null`
  - Recycle any bitmaps: `bitmap.recycle()`

## 5. General CameraX Implementation Principles

- **`ProcessCameraProvider` Management:** Obtain asynchronously, store, reuse, and release properly.
- **Use Case Binding and Lifecycle:** Bind `Preview`, `ImageCapture` to a `LifecycleOwner`. Use `unbindAll()` before re-binding or on destroy.
- **Permissions Handling:** Explicitly request and handle camera permissions.
- **Threading Model for CameraX:** CameraX operations are backgrounded. Ensure any UI interactions from CameraX callbacks are on the correct thread.
- **Error Handling:** Implement robust error handling for CameraX operations.

## 6. Coding Conventions and Style

- **Dart/Flutter:** Official Flutter linting rules, Effective Dart.
- **Kotlin:** Official Kotlin coding conventions. Android Studio's default formatting.
- **Comments:** Clear comments for complex logic, CameraX configurations, filter implementation, and platform channel communication.

## 7. Patterns to Prefer

- **CameraX with Camera2 Interop API:** For built-in filter effects where possible.
- **PlatformView for Camera Preview:** For efficient integration of CameraX's PreviewView with Flutter.
- **ColorMatrix for Custom Filters:** For filters not available in Camera2 API.
- **Asynchronous Operations & Threading:** Kotlin coroutines for asynchronous operations, CameraX executor for its tasks.
- **Comprehensive Resource Management:** Diligently release all CameraX and Bitmap resources.
- **Proper Lifecycle Handling:** Binding camera use cases to the activity lifecycle.

## 8. Patterns to Avoid

- **Blocking Main Thread:** Long operations on the main thread will cause ANRs or UI jank.
- **Mismanaging Lifecycles:** Failure to release CameraX resources leads to leaks and crashes.
- **Creating Large Bitmaps Without Recycling:** Always recycle bitmaps after use to prevent memory leaks.
- **Ignoring Activity Lifecycle:** CameraX operations must be properly bound to the activity lifecycle.
- **Slow Filter Processing on Main Thread:** Handle bitmap processing on background threads.

## 9. Flutter Plugin Specifics (Native Side API for Dart)

- **`initializeCamera(): Future<void>`**: Initialize the camera system.
- **`takePicture(): Future<String?>`**: Take a picture and return the file path to the saved image.
- **`setFilter(String filterType): Future<void>`**: Set the filter type (none, sepia, grayscale, negative, solarize, posterize).
- **`switchCamera(): Future<void>`**: Toggle between front and back cameras.
- **`toggleFlash(): Future<void>`**: Cycle flash modes (off, on, auto).
- **`setZoom(double zoom): Future<void>`**: Set zoom level from 0.0 (no zoom) to 1.0 (max zoom).
- **`dispose(): Future<void>`**: Release all camera resources.

## 10. Example App Implementation (`/example` directory - Flutter/Dart)

### 10.1. Purpose

- Demonstrate all features of the developed Flutter camera plugin.
- Provide a user-friendly interface to interact with photo capture and real-time filters.
- Serve as a practical testbed and usage example for plugin consumers.

### 10.2. Core UI Components (Flutter)

- **Camera Preview:** A `PlatformView` widget using `AndroidView` with the `viewType` set to `"com.example/camera_preview_view"`.
- **Capture Button:** An `IconButton` or `ElevatedButton` to trigger photo capture.
  - Consider visual feedback (e.g., icon change, animation) during capture.
- **Filter Selection UI:**
  - A `Row` of `TextButton` or `ElevatedButton` widgets, each representing a filter (e.g., "None", "Sepia", "Grayscale", "Negative", "Solarize", "Posterize").
  - Alternatively, a `DropdownButton` or a horizontal `ListView` for filter selection.
  - Visually indicate the currently active filter.
- **Flash Mode Toggle:** `IconButton` to toggle flash modes. Icon should reflect current flash mode.
- **Camera Facing Toggle:** `IconButton` to switch between front and back cameras.
- **Zoom Control:** A `Slider` widget to control zoom level from 0.0 to 1.0.
- **Captured Image Display (Optional):** A small `Image.file` widget to show the last taken photo, or a button to open it in the gallery.
- **Status/Error Messages:** A `Text` widget to display messages from the plugin (e.g., "Photo saved to...", "Error initializing camera").
- **Permission Handling UI:** Buttons/prompts to request camera and storage permissions if not granted.

### 10.3. Key Functionalities and Logic (Flutter - Dart)

#### 10.3.1. Camera Initialization and Preview

- On widget initialization (`initState`), call the plugin's `initializeCamera()` method.
- Render the camera preview using `AndroidView` with viewType `"com.example/camera_preview_view"`.
- Handle potential errors during initialization and display appropriate messages.

#### 10.3.2. Photo Capture

- When the capture button is pressed, call the plugin's `takePicture()` method.
- Provide a suitable save path (e.g., using `path_provider` to get a temporary or documents directory).
- Disable capture button during processing.
- On result, show success (with path) or error message.

#### 10.3.3. Real-time Filter Selection and Application

- When a filter button/option is selected:
  - Call the plugin's `setFilter(FilterType.YOUR_CHOSEN_FILTER)` method.
  - Update the UI to reflect the active filter.
  - The live preview should update to show the selected filter applied.

#### 10.3.4. Flash Mode Control

- When the flash toggle button is pressed:
  - Cycle through available flash modes (e.g., `FlashMode.OFF` -> `FlashMode.ON` -> `FlashMode.AUTO` -> `FlashMode.OFF`).
  - Call the plugin's `setFlashMode(selectedMode)` method.
  - Update the flash button's icon.

#### 10.3.5. Camera Facing Toggle

- When the camera facing toggle is pressed:
  - Call the plugin's `switchCamera()` method.
  - The PlatformView will automatically update with the new camera.

#### 10.3.6. Permissions Handling

- Before initializing the camera, check for camera and storage (if saving to gallery) permissions using a Flutter permissions plugin (e.g., `permission_handler`).
- If permissions are not granted, request them.
- Provide UI feedback on permission status. Deny camera access if permissions are permanently denied.

#### 10.3.7. State Management (Basic)

- Use `StatefulWidget` and `setState` for managing:
  - `_isCameraInitialized` (boolean)
  - `_currentFilter` (String)
  - `_zoomLevel` (double)
  - `_lastCapturedImagePath` (String?)
  - `_lastErrorMessage` (String?)
  - `_isCapturing` (boolean)
- For more complex state, consider `Provider` or `Riverpod`.

#### 10.3.8. Lifecycle and Resource Management

- In the `dispose()` method of the Flutter widget, call the plugin's `dispose()` method to release native resources (CameraX, etc.).

### 10.4. Referencing Plugin API

- The example app code should directly call the methods defined for the plugin in **Section 9 (Flutter Plugin Specifics)**.
- Ensure data types for parameters and return values match the plugin's API.
- Copilot should help create a wrapper class or directly use the plugin's instance to call these methods.

### 10.5. Example App Structure (Conceptual - `/example/lib/main.dart`)

```dart
// main.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:beauty_camera_plugin/beauty_camera_plugin.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  // Ensure Flutter binding is initialized
  WidgetsFlutterBinding.ensureInitialized();
  
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Beauty Camera Plugin Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
        appBarTheme: const AppBarTheme(
          elevation: 0,
          backgroundColor: Colors.black,
        ),
        bottomNavigationBarTheme: const BottomNavigationBarThemeData(
          backgroundColor: Colors.black,
          selectedItemColor: Colors.white,
          unselectedItemColor: Colors.grey,
        ),
      ),
      darkTheme: ThemeData.dark(),
      themeMode: ThemeMode.dark,
      home: const CameraScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({Key? key}) : super(key: key);

  @override
  _CameraScreenState createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> with WidgetsBindingObserver {
  static const MethodChannel _channel = MethodChannel('com.example/beauty_camera_plugin');
  
  // Camera state
  bool _isCameraInitialized = false;
  bool _isCapturing = false;
  String _currentFilter = 'none';
  double _zoomLevel = 0.0;
  String? _lastCapturedImagePath;
  String? _lastErrorMessage;
  FlashMode _currentFlashMode = FlashMode.off;
  
  // UI state
  bool _showFilterSelector = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initializeAndRequestPermissions();
  }
  
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Handle app lifecycle changes
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      // App is in background
      _disposeCamera();
    } else if (state == AppLifecycleState.resumed) {
      // App is in foreground
      if (!_isCameraInitialized) {
        _initializeCamera();
      }
    }
  }

  Future<void> _initializeAndRequestPermissions() async {
    // Request camera and storage permissions
    final statuses = await [
      Permission.camera,
      Permission.storage,
    ].request();

    if (statuses[Permission.camera]!.isGranted) {
      await _initializeCamera();
    } else {
      setState(() {
        _lastErrorMessage = 'Camera permission is required';
      });
    }
  }
  
  Future<void> _initializeCamera() async {
    try {
      // Initialize the camera
      await _channel.invokeMethod('initializeCamera');
      setState(() {
        _isCameraInitialized = true;
        _lastErrorMessage = null;
      });
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to initialize camera: $e';
      });
    }
  }

  Future<void> _takePicture() async {
    if (!_isCameraInitialized || _isCapturing) return;

    setState(() {
      _isCapturing = true;
    });

    try {
      // Play shutter sound and show visual feedback
      HapticFeedback.mediumImpact();
      
      final String? imagePath = await _channel.invokeMethod('takePicture');
      
      setState(() {
        _isCapturing = false;
        _lastCapturedImagePath = imagePath;
      });
      
      if (imagePath != null) {
        _showPhotoPreviewDialog(imagePath);
      }
    } catch (e) {
      setState(() {
        _isCapturing = false;
        _lastErrorMessage = 'Failed to take picture: $e';
      });
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to take picture: $e')),
      );
    }
  }

  void _showPhotoPreviewDialog(String imagePath) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.file(
                  File(imagePath),
                  fit: BoxFit.contain,
                ),
              ),
            ),
            ButtonBar(
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('CLOSE'),
                ),
                TextButton(
                  onPressed: () {
                    // Share or save to gallery logic would go here
                    Navigator.of(context).pop();
                  },
                  child: const Text('SAVE'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _setFilter(String filter) async {
    if (!_isCameraInitialized) return;

    try {
      await _channel.invokeMethod('setFilter', {'filterType': filter});
      setState(() {
        _currentFilter = filter;
      });
    } catch (e) {
      setState(() {
        _lastErrorMessage = 'Failed to set filter: $e';
      });
    }
  }

  Future<void> _toggleFlash() async {
    if (!_isCameraInitialized) return;

    try {
      await _channel.invokeMethod('toggleFlash');
      
      // Update UI to reflect the new flash mode
      setState(() {
        // Cycle through flash modes
        switch (_currentFlashMode) {
          case FlashMode.off:
            _currentFlashMode = FlashMode.on;
            break;
          case FlashMode.on:
            _currentFlashMode = FlashMode.auto;
            break;
          case FlashMode.auto:
            _currentFlashMode = FlashMode.off;
            break;
        }
      });
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to toggle flash: $e')),
      );
    }
  }

  Future<void> _switchCamera() async {
    if (!_isCameraInitialized) return;

    try {
      await _channel.invokeMethod('switchCamera');
      // Reset zoom when switching camera
      setState(() {
        _zoomLevel = 0.0;
      });
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to switch camera: $e')),
      );
    }
  }

  Future<void> _setZoom(double zoom) async {
    if (!_isCameraInitialized) return;

    try {
      await _channel.invokeMethod('setZoom', {'zoom': zoom});
      setState(() {
        _zoomLevel = zoom;
      });
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to set zoom: $e')),
      );
    }
  }

  Future<void> _disposeCamera() async {
    if (_isCameraInitialized) {
      try {
        await _channel.invokeMethod('dispose');
        setState(() {
          _isCameraInitialized = false;
        });
      } catch (e) {
        debugPrint('Error disposing camera: $e');
      }
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _disposeCamera();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Camera Preview
            if (_isCameraInitialized)
              Positioned.fill(
                child: AndroidView(
                  viewType: 'com.example/camera_preview_view',
                  creationParams: const <String, dynamic>{},
                  creationParamsCodec: const StandardMessageCodec(),
                ),
              )
            else
              const Positioned.fill(
                child: Center(
                  child: CircularProgressIndicator(color: Colors.white),
                ),
              ),
              
            // Error Messages
            if (_lastErrorMessage != null)
              Positioned(
                top: 16,
                left: 16,
                right: 16,
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    _lastErrorMessage!,
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
              ),
              
            // Filter selector panel (shown when filter button is tapped)
            if (_showFilterSelector)
              Positioned(
                bottom: 130,
                left: 0,
                right: 0,
                child: Container(
                  height: 80,
                  color: Colors.black.withOpacity(0.5),
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    children: [
                      FilterButton(name: 'None', filterType: 'none', currentFilter: _currentFilter, onPressed: _setFilter),
                      FilterButton(name: 'Sepia', filterType: 'sepia', currentFilter: _currentFilter, onPressed: _setFilter),
                      FilterButton(name: 'Mono', filterType: 'mono', currentFilter: _currentFilter, onPressed: _setFilter),
                      FilterButton(name: 'Negative', filterType: 'negative', currentFilter: _currentFilter, onPressed: _setFilter),
                      FilterButton(name: 'Solarize', filterType: 'solarize', currentFilter: _currentFilter, onPressed: _setFilter),
                      FilterButton(name: 'Posterize', filterType: 'posterize', currentFilter: _currentFilter, onPressed: _setFilter),
                    ],
                  ),
                ),
              ),
              
            // Camera controls
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                height: 120,
                padding: const EdgeInsets.all(16),
                color: Colors.black.withOpacity(0.5),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // Filters button
                    IconButton(
                      icon: Icon(
                        Icons.filter_vintage,
                        color: _showFilterSelector ? Colors.amber : Colors.white,
                      ),
                      onPressed: () {
                        setState(() {
                          _showFilterSelector = !_showFilterSelector;
                        });
                      },
                      tooltip: 'Filters',
                    ),
                    
                    // Capture button
                    GestureDetector(
                      onTap: _isCapturing ? null : _takePicture,
                      child: Container(
                        width: 70,
                        height: 70,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(_isCapturing ? 0.5 : 0.8),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 3),
                        ),
                        child: _isCapturing 
                          ? const CircularProgressIndicator(color: Colors.black)
                          : const Icon(Icons.camera_alt, color: Colors.black, size: 32),
                      ),
                    ),
                    
                    // Flash toggle
                    IconButton(
                      icon: Icon(
                        _currentFlashMode == FlashMode.off
                            ? Icons.flash_off
                            : _currentFlashMode == FlashMode.on
                                ? Icons.flash_on
                                : Icons.flash_auto,
                        color: Colors.white,
                      ),
                      onPressed: _toggleFlash,
                      tooltip: 'Toggle Flash',
                    ),
                  ],
                ),
              ),
            ),
            
            // Camera switch and zoom controls
            Positioned(
              top: 16,
              right: 16,
              child: Column(
                children: [
                  // Camera switch button
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.4),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: const Icon(Icons.flip_camera_ios, color: Colors.white),
                      onPressed: _switchCamera,
                      tooltip: 'Switch Camera',
                    ),
                  ),
                  
                  const SizedBox(height: 16),
                  
                  // Zoom control
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.4),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
                    width: 40,
                    height: 150,
                    child: RotatedBox(
                      quarterTurns: 3,
                      child: Slider(
                        value: _zoomLevel,
                        min: 0.0,
                        max: 1.0,
                        onChanged: _setZoom,
                        activeColor: Colors.white,
                        inactiveColor: Colors.white30,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// Enum for flash modes
enum FlashMode { off, on, auto }

// Helper widget for filter buttons
class FilterButton extends StatelessWidget {
  final String name;
  final String filterType;
  final String currentFilter;
  final Function(String) onPressed;

  const FilterButton({
    Key? key,
    required this.name,
    required this.filterType,
    required this.currentFilter,
    required this.onPressed,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final isSelected = filterType == currentFilter;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GestureDetector(
            onTap: () => onPressed(filterType),
            child: Container(
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: isSelected ? Colors.amber : Colors.transparent,
                  width: 2,
                ),
                color: Colors.grey[800],
              ),
              child: Center(
                child: Text(
                  name.substring(0, 1),
                  style: TextStyle(
                    color: isSelected ? Colors.amber : Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            name,
            style: TextStyle(
              color: isSelected ? Colors.amber : Colors.white,
              fontSize: 10,
            ),
          ),
        ],
      ),
    );
  }
}
```
