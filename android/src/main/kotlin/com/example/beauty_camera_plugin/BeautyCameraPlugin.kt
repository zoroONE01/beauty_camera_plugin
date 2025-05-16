package com.example.beauty_camera_plugin

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** BeautyCameraPlugin */
class BeautyCameraPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  private lateinit var channel: MethodChannel
  private lateinit var context: Context
  private var cameraManager: CameraManager? = null
  private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private val mainScope = CoroutineScope(Dispatchers.Main)
  
  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    flutterPluginBinding = binding
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "beauty_camera_plugin")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "initializeCamera" -> {
        val facingString = call.argument<String>("facing") ?: "BACK"
        val resolutionPresetString = call.argument<String>("resolutionPreset") ?: "HIGH"
        
        val cameraFacing = try {
          CameraFacing.valueOf(facingString)
        } catch (e: IllegalArgumentException) {
          CameraFacing.BACK
        }
        
        val resolutionPreset = try {
          ResolutionPreset.valueOf(resolutionPresetString)
        } catch (e: IllegalArgumentException) {
          ResolutionPreset.HIGH
        }
        
        initializeCamera(cameraFacing, resolutionPreset, result)
      }
      "takePicture" -> {
        val savePath = call.argument<String>("savePath")
        if (savePath == null) {
          result.error("INVALID_ARGUMENT", "Missing savePath parameter", null)
          return
        }
        takePicture(savePath, result)
      }
      "setFilter" -> {
        val filterTypeString = call.argument<String>("filter") ?: "NONE"
        val filterType = try {
          FilterType.valueOf(filterTypeString)
        } catch (e: IllegalArgumentException) {
          FilterType.NONE
        }
        setFilter(filterType, result)
      }
      "setFlashMode" -> {
        val flashModeString = call.argument<String>("mode") ?: "OFF"
        val flashMode = try {
          FlashMode.valueOf(flashModeString)
        } catch (e: IllegalArgumentException) {
          FlashMode.OFF
        }
        setFlashMode(flashMode, result)
      }
      "setFocusPoint" -> {
        val x = call.argument<Double>("x") ?: 0.5
        val y = call.argument<Double>("y") ?: 0.5
        setFocusPoint(x.toFloat(), y.toFloat(), result)
      }
      "disposeCamera" -> {
        disposeCamera(result)
      }
      "getAndroidVersion" -> {
        result.success(android.os.Build.VERSION.SDK_INT)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    flutterPluginBinding = null
  }
  
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    lifecycleOwner = binding.activity as LifecycleOwner
  }

  override fun onDetachedFromActivityForConfigChanges() {
    lifecycleOwner = null
    cameraManager?.dispose()
    cameraManager = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    lifecycleOwner = binding.activity as LifecycleOwner
  }

  override fun onDetachedFromActivity() {
    lifecycleOwner = null
    cameraManager?.dispose()
    cameraManager = null
  }
  
  private fun initializeCamera(facing: CameraFacing, preset: ResolutionPreset, result: Result) {
    val flutterTextures = flutterPluginBinding?.textureRegistry ?: run {
      result.error("TEXTURE_REGISTRY_UNAVAILABLE", "Flutter texture registry not available", null)
      return
    }
    
    val lifecycle = lifecycleOwner ?: run {
      result.error("LIFECYCLE_UNAVAILABLE", "Activity lifecycle not available", null)
      return
    }
    
    // Dispose of any existing camera manager
    cameraManager?.dispose()
    
    // Create a new Flutter texture entry
    val textureEntry = flutterTextures.createSurfaceTexture()
    
    // Create a new camera manager
    cameraManager = CameraManager(context, textureEntry, lifecycle)
    
    // Initialize the camera asynchronously
    // CameraX operations must be on the main thread
    mainScope.launch {
      try {
        // Run on main thread, as CameraX requires
        val textureId = cameraManager?.initializeCamera()
        
        if (textureId != null) {
          result.success(mapOf(
            "textureId" to textureId
          ))
        } else {
          result.error("CAMERA_INIT_ERROR", "Failed to initialize camera: Texture ID is null", null)
          cameraManager?.dispose()
          cameraManager = null
        }
      } catch (e: Exception) {
        Log.e("BeautyCameraPlugin", "Failed to initialize camera", e)
        result.error("CAMERA_INIT_ERROR", "Failed to initialize camera: ${e.message}", null)
        cameraManager?.dispose()
        cameraManager = null
      }
    }
  }
  
  private fun takePicture(savePath: String, result: Result) {
    val cameraManager = cameraManager ?: run {
      result.error("CAMERA_UNAVAILABLE", "Camera not initialized", null)
      return
    }
    
    cameraManager.takePicture(savePath) { path, error ->
      if (error != null) {
        result.error("CAPTURE_ERROR", "Failed to capture photo: ${error.message}", null)
      } else {
        result.success(mapOf(
          "success" to true,
          "filePath" to path
        ))
      }
    }
  }
  
  private fun setFilter(filterType: FilterType, result: Result) {
    val cameraManager = cameraManager ?: run {
      result.error("CAMERA_UNAVAILABLE", "Camera not initialized", null)
      return
    }
    
    val success = cameraManager.setFilter(filterType)
    
    if (success) {
      result.success(mapOf("success" to true))
    } else {
      result.error("FILTER_ERROR", "Failed to set filter", null)
    }
  }
  
  private fun setFlashMode(flashMode: FlashMode, result: Result) {
    val cameraManager = cameraManager ?: run {
      result.error("CAMERA_UNAVAILABLE", "Camera not initialized", null)
      return
    }
    
    val success = cameraManager.setFlashMode(flashMode)
    
    if (success) {
      result.success(mapOf("success" to true))
    } else {
      result.error("FLASH_MODE_ERROR", "Failed to set flash mode", null)
    }
  }
  
  private fun setFocusPoint(x: Float, y: Float, result: Result) {
    val cameraManager = cameraManager ?: run {
      result.error("CAMERA_UNAVAILABLE", "Camera not initialized", null)
      return
    }
    
    mainScope.launch {
      try {
        val success = cameraManager.setFocusPoint(x, y)
        result.success(mapOf("success" to success))
      } catch (e: Exception) {
        Log.e("BeautyCameraPlugin", "Failed to set focus point", e)
        result.error("FOCUS_ERROR", "Failed to set focus point: ${e.message}", null)
      }
    }
  }
  
  private fun disposeCamera(result: Result) {
    cameraManager?.dispose()
    cameraManager = null
    result.success(mapOf("success" to true))
  }
}
