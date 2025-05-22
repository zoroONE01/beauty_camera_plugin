package com.example.beauty_camera_plugin

import android.content.Context
import android.util.Log
import android.view.View
import androidx.camera.view.PreviewView
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
    
    private val previewView: PreviewView
    private val cameraManager = plugin.getCameraManager() // Get CameraManager instance from plugin
    
    init {
        Log.d(TAG, "CameraPlatformView id $id created. CreationParams: $creationParams")
        previewView = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            
            // Make sure the view is visible by setting a background color
            setBackgroundColor(android.graphics.Color.BLACK) // Keep black for now to see if view appears
            
            // Set layout parameters to ensure the view takes up space
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            Log.d(TAG, "PreviewView id $id initialized with MATCH_PARENT layout and COMPATIBLE mode.")
        }
        
        // Set the PreviewView in the CameraManager
        if (cameraManager != null) {
            Log.d(TAG, "Setting PreviewView for CameraManager in CameraPlatformView id $id.")
            cameraManager.setPreviewView(previewView)
        } else {
            Log.e(TAG, "CameraManager is null when trying to set PreviewView in CameraPlatformView id $id.")
        }
    }
    
    override fun getView(): View {
        return previewView
    }
    
    override fun dispose() {
        Log.d(TAG, "PlatformView $id disposed.")
    }
}