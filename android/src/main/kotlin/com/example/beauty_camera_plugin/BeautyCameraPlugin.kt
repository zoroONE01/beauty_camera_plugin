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
      
      "setExposure" -> {
        val exposure = call.argument<Double>("exposure")
        if (exposure != null) {
          manager!!.setExposure(exposure.toFloat())
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Exposure value is required", null)
        }
      }
      
      "setFocusPoint" -> {
        val x = call.argument<Double>("x")
        val y = call.argument<Double>("y")
        if (x != null && y != null) {
          manager!!.setFocusPoint(x.toFloat(), y.toFloat())
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Focus point x and y are required", null)
        }
      }
      
      "setAutoFocus" -> {
        val enabled = call.argument<Boolean>("enabled")
        if (enabled != null) {
          manager!!.setAutoFocus(enabled)
          result.success(null)
        } else {
          result.error("INVALID_ARGUMENT", "Auto focus enabled flag is required", null)
        }
      }
      
      "dispose" -> {
        manager!!.dispose()
        cameraManager = null
        result.success(null)
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
