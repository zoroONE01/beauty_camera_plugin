package com.example.beauty_camera_plugin

import android.app.Activity
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.StandardMessageCodec

/** BeautyCameraPlugin */
import android.view.Surface // Required for Surface.ROTATION_* constants

class BeautyCameraPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var orientationEventChannel: EventChannel
  private var activity: Activity? = null
  
  // Camera manager instance
  private var cameraManager: CameraManager? = null
  private var currentPlatformView: CameraPlatformView? = null

  // Helper to convert degrees to Surface.ROTATION_* constants
  private fun getSurfaceRotationFromDegrees(degrees: Int): Int {
    return when (degrees) {
        0 -> Surface.ROTATION_0
        90 -> Surface.ROTATION_90
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_270
        else -> {
            android.util.Log.w("BeautyCameraPlugin", "Invalid degrees ($degrees) for surface rotation. Defaulting to ROTATION_0.")
            Surface.ROTATION_0
        }
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.example/beauty_camera_plugin")
    channel.setMethodCallHandler(this)

    // Set up orientation event channel
    orientationEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "com.example/beauty_camera_plugin/orientation")

    // Register platform view factory for camera preview
    flutterPluginBinding
      .platformViewRegistry
      .registerViewFactory(
        "com.example/camera_preview_view", 
        CameraPlatformViewFactory(StandardMessageCodec.INSTANCE, this)
      )
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val manager = cameraManager
    if (manager == null && call.method != "getPlatformVersion" && call.method != "initializeCamera") {
      result.error("NOT_INITIALIZED", "Camera manager not initialized", null)
      return
    }

    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }        "initializeCamera" -> {
        activity?.let { 
          cameraManager = CameraManager(it)
          cameraManager?.initialize { success, error ->
            if (success) {
              // Set up orientation stream
              val handler = cameraManager?.orientationStreamHandler
              android.util.Log.d("BeautyCameraPlugin", "Setting OrientationStreamHandler: $handler")
              orientationEventChannel.setStreamHandler(handler)
              
              // If platformView is already registered, attach the manager
              currentPlatformView?.let { pv ->
                cameraManager?.let { cm ->
                  pv.attachCameraManager(cm)
                }
              }
              result.success(null)
            } else {
              android.util.Log.e("BeautyCameraPlugin", "CameraManager initialization failed: $error")
              result.error("INITIALIZATION_FAILED", error, null)
            }
          }
        } ?: result.error("NO_ACTIVITY", "Activity not available", null)
      }
      
      "takePicture" -> {
        manager!!.takePicture { filePath, error ->
          if (filePath != null) {
            result.success(filePath)
          } else {
            result.error("CAPTURE_FAILED", error, null)
          }
        }
      }
      
      "setFilter" -> {
        val filterType = call.argument<String>("filterType")
        if (filterType != null) {
          manager!!.setFilter(filterType)
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Filter type is required", null)
        }
      }
      
      "setFilterIntensity" -> {
        val intensity = call.argument<Double>("intensity")
        if (intensity != null) {
          manager!!.setFilterIntensity(intensity.toFloat())
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Intensity is required", null)
        }
      }
      
      "switchCamera" -> {
        manager!!.switchCamera()
        result.success(null)
      }
      
      "toggleFlash" -> {
        val flashMode = manager!!.toggleFlash()
        result.success(flashMode)
      }
      
      "setZoom" -> {
        val zoom = call.argument<Double>("zoom")
        if (zoom != null) {
          manager!!.setZoom(zoom.toFloat())
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Zoom value is required", null)
        }
      }
      
      "dispose" -> {
        manager!!.dispose()
        cameraManager = null
        result.success(null)
      }
      "updateCameraRotation" -> {
        val rotation = call.argument<Int>("rotation")
        // Use a consistent TAG for logging if you define one for the class, otherwise use a string.
        val TAG_BCP = "BeautyCameraPlugin" // Or use a class-level TAG
        val rotationDegrees = call.argument<Int>("rotation") // This is 0, 90, 180, 270
        android.util.Log.d(TAG_BCP, "onMethodCall: 'updateCameraRotation' received. Rotation DEGREES from Flutter: $rotationDegrees. Current cameraManager: $cameraManager, manager var: $manager")
        
        if (rotationDegrees != null) {
          if (manager != null) { // 'manager' is a local val copy of cameraManager
            val surfaceRotation = getSurfaceRotationFromDegrees(rotationDegrees) // Convert degrees to Surface.ROTATION_*
            android.util.Log.d(TAG_BCP, "onMethodCall 'updateCameraRotation': Converted $rotationDegrees degrees to SurfaceRotation: $surfaceRotation. Calling manager.updateTargetRotation().")
            manager.updateTargetRotation(surfaceRotation) // Pass Surface.ROTATION_* constant
            result.success(null)
          } else {
            android.util.Log.e(TAG_BCP, "onMethodCall 'updateCameraRotation': 'manager' (cameraManager) is null. Cannot update rotation.")
            result.error("NOT_INITIALIZED", "CameraManager is null, cannot update rotation.", null)
          }
        } else {
          android.util.Log.e(TAG_BCP, "onMethodCall 'updateCameraRotation': Rotation degrees argument from Flutter is null.")
          result.error("INVALID_ARGUMENT", "Rotation argument (degrees) is missing or invalid", null)
        }
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    orientationEventChannel.setStreamHandler(null)
    cameraManager?.dispose()
    cameraManager = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
    cameraManager?.dispose()
    cameraManager = null
  }

  fun getCameraManager(): CameraManager? {
    return cameraManager
  }

  fun registerPlatformView(view: CameraPlatformView?) {
    this.currentPlatformView = view
    // If cameraManager is already initialized and a new view is registered, set it.
    if (view != null && cameraManager != null) {
        view.attachCameraManager(cameraManager!!)
    }
  }
}
