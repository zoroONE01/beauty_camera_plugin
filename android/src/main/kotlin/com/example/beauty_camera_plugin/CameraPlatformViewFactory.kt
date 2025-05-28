package com.example.beauty_camera_plugin

import android.content.Context
import com.example.beauty_camera_plugin.FilteredTextureView
import android.util.Log
import android.view.View
// import androidx.camera.view.PreviewView // No longer using PreviewView directly for display
import io.flutter.plugin.common.MessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class CameraPlatformViewFactory(
    createArgsCodec: MessageCodec<Any>,
    private val plugin: BeautyCameraPlugin
) : PlatformViewFactory(createArgsCodec) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return CameraPlatformView(context, viewId, args as? Map<String, Any>, plugin)
    }
}

class CameraPlatformView(
    private val context: Context,
    private val id: Int,
    private val creationParams: Map<String, Any>?,
    private val plugin: BeautyCameraPlugin
) : PlatformView {

    private val filteredTextureView: FilteredTextureView // Changed from PreviewView
    private var cameraManager: CameraManager? = null

    init {
        filteredTextureView = FilteredTextureView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        plugin.registerPlatformView(this)
    }

    fun attachCameraManager(manager: CameraManager) {
        this.cameraManager = manager
        // Pass the FilteredTextureView to CameraManager
        this.cameraManager?.setFilteredTextureView(filteredTextureView)
    }

    override fun getView(): View {
        return filteredTextureView // Return our custom view
    }

    override fun dispose() {
        plugin.registerPlatformView(null) // Unregister this view
        // CameraManager's lifecycle is managed by the plugin,
        // but we should nullify our reference.
        cameraManager = null
    }
}
