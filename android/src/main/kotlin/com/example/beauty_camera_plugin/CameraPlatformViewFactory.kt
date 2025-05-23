package com.example.beauty_camera_plugin

import android.content.Context
import android.util.Log
import android.view.View
// import androidx.camera.view.PreviewView // Không dùng PreviewView nữa
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

private const val TAG = "CameraPlatformView"

class CameraPlatformViewFactory(
    private val messenger: StandardMessageCodec,
    private val plugin: BeautyCameraPlugin
) : PlatformViewFactory(messenger) {
    
    override fun create(context: Context?, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as? Map<String?, Any?>?
        return CameraPlatformView(context!!, viewId, creationParams, plugin)
    }
}

class CameraPlatformView(
    private val context: Context,
    private val id: Int,
    private val creationParams: Map<String?, Any?>?,
    private val plugin: BeautyCameraPlugin
) : PlatformView {
    
    private val cameraGLSurfaceView: CameraGLSurfaceView
    private val cameraManager = plugin.getCameraManager()
    
    init {
        Log.i(TAG, "CameraPlatformView id $id: init START. CreationParams: $creationParams")
        cameraGLSurfaceView = CameraGLSurfaceView(context).apply {
            // Set layout parameters to ensure the view takes up space
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            Log.i(TAG, "CameraPlatformView id $id: CameraGLSurfaceView instance created and layout params set.")
        }
        // plugin.registerView sẽ log chi tiết
        plugin.registerView(cameraGLSurfaceView) // Đăng ký view với plugin
        Log.i(TAG, "CameraPlatformView id $id: Called plugin.registerView with $cameraGLSurfaceView.")
        
        // CameraManager sẽ cần lấy SurfaceTexture từ CameraGLSurfaceView.renderer
        // Việc này sẽ được thực hiện trong CameraManager khi nó khởi tạo camera.
        // Chúng ta không gọi cameraManager.setPreviewView(previewView) nữa.
        // Thay vào đó, CameraManager sẽ được cập nhật để làm việc với SurfaceTexture.
        if (cameraManager == null) {
             Log.e(TAG, "CameraManager is null in CameraPlatformView id $id.")
        }
    }
    
    override fun getView(): View {
        return cameraGLSurfaceView
    }
    
    override fun dispose() {
        Log.i(TAG, "CameraPlatformView id $id: dispose() called. Current cameraGLSurfaceView instance: $cameraGLSurfaceView")
        // Quan trọng: Phải gọi onPause của GLSurfaceView để giải phóng tài nguyên OpenGL
        // và dừng renderer thread một cách an toàn.
        // Việc này nên được thực hiện trên GL thread, onPause() của GLSurfaceView sẽ queueEvent.
        cameraGLSurfaceView.onPause() // This will log internally
        Log.i(TAG, "CameraPlatformView id $id: Called cameraGLSurfaceView.onPause(). Now calling plugin.unregisterView.")
        plugin.unregisterView(cameraGLSurfaceView) // Hủy đăng ký view với plugin, this will log internally
        Log.i(TAG, "CameraPlatformView id $id: dispose() finished.")
    }
}