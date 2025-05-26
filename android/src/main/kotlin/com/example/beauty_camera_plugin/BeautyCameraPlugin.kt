package com.example.beauty_camera_plugin

import android.app.Activity
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
import io.flutter.plugin.common.StandardMessageCodec
import java.io.File

private const val TAG = "BeautyCameraPlugin"

/** BeautyCameraPlugin */
class BeautyCameraPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private lateinit var context: Context
    private var cameraManager: CameraManager? = null
    private var cameraGLSurfaceView: CameraGLSurfaceView? = null // Thêm để lưu trữ GL view

    // State preservation fields
    private var savedLensFacing: Int? = null
    private var savedZoomRatio: Float? = null
    private var savedFlashMode: String? = null
    private var savedFilterType: String? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.example/beauty_camera_plugin")
        channel.setMethodCallHandler(this)

        flutterPluginBinding
            .platformViewRegistry
            .registerViewFactory(
                "com.example/camera_preview_view",
                CameraPlatformViewFactory(StandardMessageCodec.INSTANCE, this)
            )
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "initializeCamera" -> {
                if (activity != null && activity is LifecycleOwner) {
                    // Ensure CameraManager is initialized here, as activity is confirmed.
                    if (cameraManager == null) {
                        Log.i(TAG, "Creating new CameraManager in initializeCamera. Saved state: lens=$savedLensFacing, zoom=$savedZoomRatio, flash=$savedFlashMode, filter=$savedFilterType")
                        cameraManager = CameraManager(
                            context,
                            activity!!,
                            activity as LifecycleOwner,
                            initialLensFacing = savedLensFacing,
                            initialZoomRatio = savedZoomRatio,
                            initialFlashMode = savedFlashMode,
                            initialFilterType = savedFilterType
                        )
                    }
                    // Bây giờ initializeCamera cần CameraGLSurfaceView
                    if (cameraGLSurfaceView != null) {
                        cameraManager?.initializeCamera(cameraGLSurfaceView!!) { success: Boolean, error: String? ->
                            if (success) {
                                result.success(null)
                            } else {
                                result.error("INIT_FAILED", error ?: "Camera initialization failed", null)
                            }
                        }
                    } else {
                        // GLSurfaceView chưa sẵn sàng. Điều này có thể xảy ra nếu initializeCamera
                        // được gọi trước khi PlatformView được hiển thị đầy đủ.
                        // Cần một cơ chế để xử lý trường hợp này, ví dụ: đợi hoặc báo lỗi.
                        Log.e(TAG, "CameraGLSurfaceView is not available for initializeCamera yet.")
                        result.error("VIEW_NOT_READY", "CameraGLSurfaceView not ready for initialization. Ensure the camera preview widget is visible.", null)
                    }
                } else {
                    result.error("NO_ACTIVITY", "Activity not available or not a LifecycleOwner for camera initialization.", null)
                }
            }
            "takePicture" -> {
                Log.d(TAG, "Received takePicture request")
                
                // Check if camera is initialized
                if (cameraManager == null) {
                    Log.e(TAG, "CameraManager is null, cannot take picture")
                    result.error("NOT_INITIALIZED", "Camera not initialized or CameraManager is null.", null)
                    return
                }
                
                // It's good practice to ensure a directory exists before creating a file in it.
                val imageDir = context.getExternalFilesDir(null)
                if (imageDir == null) {
                    Log.e(TAG, "External storage directory is not available")
                    result.error("STORAGE_UNAVAILABLE", "External storage directory is not available.", null)
                    return
                }
                if (!imageDir.exists()) {
                    imageDir.mkdirs() // Create the directory if it doesn't exist.
                }
                
                // Create a unique filename with timestamp
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val photoFile = File(imageDir, "pic_${timestamp}_${System.currentTimeMillis()}.jpg")
                Log.d(TAG, "Taking picture to file: ${photoFile.absolutePath}")

                try {
                    cameraManager?.takePicture(photoFile) { filePath: String?, error: String? ->
                        if (filePath != null) {
                            Log.d(TAG, "Picture taken successfully: $filePath")
                            // Native đã xử lý tất cả rotation - chỉ trả về image path
                            result.success(filePath)
                        } else {
                            Log.e(TAG, "Failed to take picture: $error")
                            result.error("CAPTURE_FAILED", error ?: "Failed to take picture", null)
                        }
                    } ?: run {
                        Log.e(TAG, "CameraManager became null during takePicture")
                        result.error("NOT_INITIALIZED", "Camera not initialized or CameraManager is null.", null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during takePicture", e)
                    result.error("CAPTURE_EXCEPTION", "Exception during capture: ${e.message}", null)
                }
            }
            "setFilter" -> {
                val filterType = call.argument<String>("filterType")
                if (filterType != null) {
                    cameraManager?.setFilter(filterType)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGS", "filterType argument is missing", null)
                }
            }
            "switchCamera" -> {
                cameraManager?.switchCamera()
                result.success(null)
            }
            "toggleFlash" -> {
                 cameraManager?.toggleFlash { newFlashMode: String ->
                    result.success(newFlashMode) // Send back the new flash mode as a string
                } ?: result.error("NOT_INITIALIZED", "Camera not initialized or toggleFlash not available.", null)
            }
            "setZoom" -> {
                val zoomRatio = call.argument<Double>("zoom") // Flutter sends double
                if (zoomRatio != null) {
                    cameraManager?.setZoom(zoomRatio.toFloat()) // CameraX might expect float
                    result.success(null)
                } else {
                    result.error("INVALID_ARGS", "zoom argument is missing or not a Double", null)
                }
            }
            "dispose" -> { // This is called when the Flutter CameraController is disposed.
                Log.i(TAG, "MethodChannel 'dispose' called. Saving state and disposing CameraManager.")
                // Save current camera state before disposing
                cameraManager?.let {
                    savedLensFacing = it.getCurrentLensFacing()
                    savedZoomRatio = it.getCurrentZoomRatio()
                    savedFlashMode = it.getCurrentFlashMode()
                    savedFilterType = it.getCurrentFilterType()
                    Log.i(TAG, "Saved state before dispose: lens=$savedLensFacing, zoom=$savedZoomRatio, flash=$savedFlashMode, filter=$savedFilterType")
                }
                cameraManager?.dispose()
                cameraManager = null // Ensure it's nullified after disposal
                // DO NOT nullify cameraGLSurfaceView here.
                // Its lifecycle is tied to the PlatformView's registration/unregistration.
                Log.i(TAG, "MethodChannel 'dispose': CameraManager disposed. Kept cameraGLSurfaceView (current: $cameraGLSurfaceView) for PlatformView lifecycle.")
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.i(TAG, "onDetachedFromEngine called. Saving state and disposing CameraManager.")
        channel.setMethodCallHandler(null)
        // Save current camera state before disposing
        cameraManager?.let {
            savedLensFacing = it.getCurrentLensFacing()
            savedZoomRatio = it.getCurrentZoomRatio()
            savedFlashMode = it.getCurrentFlashMode()
            savedFilterType = it.getCurrentFilterType()
            Log.i(TAG, "Saved state in onDetachedFromEngine: lens=$savedLensFacing, zoom=$savedZoomRatio, flash=$savedFlashMode, filter=$savedFilterType")
        }
        cameraManager?.dispose() // Dispose camera resources when plugin is detached
        cameraManager = null
        cameraGLSurfaceView = null // This is fine here as the engine is detaching
        activity = null // Clear activity reference
        Log.i(TAG, "onDetachedFromEngine finished.")
    }

    // Phương thức để CameraPlatformView đăng ký GLSurfaceView
    fun registerView(view: CameraGLSurfaceView) {
        Log.i(TAG, "registerView called with view: $view. Current plugin.cameraGLSurfaceView was: ${this.cameraGLSurfaceView}")
        this.cameraGLSurfaceView = view
        Log.i(TAG, "plugin.cameraGLSurfaceView is NOW: ${this.cameraGLSurfaceView} after registration.")
        // Nếu cameraManager đã được tạo và đang chờ view, có thể thử khởi tạo ở đây
        // Hoặc, hàm initializeCamera từ Dart sẽ xử lý.
    }

    fun unregisterView(view: CameraGLSurfaceView?) { // Allow nullable to be safe
        Log.i(TAG, "unregisterView called with viewBeingDisposed: $view. Current plugin.cameraGLSurfaceView: ${this.cameraGLSurfaceView}")
        if (this.cameraGLSurfaceView == view && view != null) { // Only unregister if it's the current view and not null
            this.cameraGLSurfaceView = null
            Log.i(TAG, "Unregistered matching CameraGLSurfaceView. plugin.cameraGLSurfaceView is NOW NULL.")
        } else if (view == null) {
            Log.w(TAG, "unregisterView called with null view. No action taken on plugin.cameraGLSurfaceView (${this.cameraGLSurfaceView}).")
        }
        else {
            Log.w(TAG, "Skipped unregistering CameraGLSurfaceView. View being disposed ($view) does not match current (${this.cameraGLSurfaceView}).")
        }
    }

    // ActivityAware methods
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
    }

    // Getter for CameraManager, used by CameraPlatformViewFactory
    fun getCameraManager(): CameraManager? {
        Log.d(TAG, "getCameraManager called. Current cameraManager: $cameraManager, activity: $activity")
        // Initialize cameraManager if it hasn't been, and activity is available.
        if (cameraManager == null && activity != null && activity is LifecycleOwner) {
            Log.i(TAG, "Creating new CameraManager in getCameraManager. Saved state: lens=$savedLensFacing, zoom=$savedZoomRatio, flash=$savedFlashMode, filter=$savedFilterType")
            cameraManager = CameraManager(
                context,
                activity!!,
                activity as LifecycleOwner,
                initialLensFacing = savedLensFacing,
                initialZoomRatio = savedZoomRatio,
                initialFlashMode = savedFlashMode,
                initialFilterType = savedFilterType
            )
        } else if (cameraManager == null) {
            Log.w(TAG, "CameraManager is null in getCameraManager, but activity ($activity) is not ready or not a LifecycleOwner.")
        } else {
            Log.d(TAG, "Returning existing CameraManager instance in getCameraManager.")
        }
        return cameraManager
    }
}
