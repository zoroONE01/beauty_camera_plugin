package com.example.beauty_camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Surface

class CameraGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer: CameraGLRenderer

    init {
        Log.i("CameraGLSurfaceView", ">>> init START")
        setEGLContextClientVersion(2) // Sử dụng OpenGL ES 2.0
        renderer = CameraGLRenderer(context, this)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY // Chỉ render khi có frame mới hoặc yêu cầu
        Log.i("CameraGLSurfaceView", "<<< init END. Renderer: $renderer")
    }

    fun getSurface(): Surface? {
        Log.i("CameraGLSurfaceView", "getSurface() called. Renderer: $renderer")
        val surface = renderer.getCameraSurface() // This calls Log in CameraGLRenderer
        Log.i("CameraGLSurfaceView", "getSurface() returning: $surface. Renderer.surfaceTexture: ${renderer.surfaceTexture}")
        return surface
    }

    fun setAspectRatio(width: Int, height: Int) {
        Log.d("CameraGLSurfaceView", "setAspectRatio($width, $height) called")
        // Có thể cần thiết để xử lý tỷ lệ khung hình của preview
        // Ví dụ: requestLayout() hoặc điều chỉnh trong onMeasure
    }

    override fun onPause() {
        Log.i("CameraGLSurfaceView", ">>> onPause START. Renderer: $renderer, Current surfaceTexture: ${renderer.surfaceTexture}")
        // Gọi queueEvent TRƯỚC super.onPause() để đảm bảo nó được xử lý
        // trước khi GL context có thể bị vô hiệu hóa hoàn toàn bởi superclass.
        queueEvent {
            Log.i("CameraGLSurfaceView", "onPause() - EXECUTING renderer.onPause() in queueEvent. Renderer.surfaceTexture: ${renderer.surfaceTexture}")
            renderer.onPause() // Renderer sẽ log trạng thái surfaceTexture của nó
        }
        super.onPause() // Bây giờ gọi super.onPause()
        Log.i("CameraGLSurfaceView", "<<< onPause END. Renderer.surfaceTexture after super.onPause() and queueEvent: ${renderer.surfaceTexture}")
    }

    override fun onResume() {
        Log.i("CameraGLSurfaceView", ">>> onResume START. Renderer: $renderer, Renderer.surfaceTexture: ${renderer.surfaceTexture}")
        super.onResume()
        queueEvent {
            Log.i("CameraGLSurfaceView", "onResume() - queuing renderer.onResume(). Renderer.surfaceTexture: ${renderer.surfaceTexture}")
            renderer.onResume() // Renderer sẽ log trạng thái surfaceTexture của nó
        }
        Log.i("CameraGLSurfaceView", "<<< onResume END. Renderer.surfaceTexture after queueing: ${renderer.surfaceTexture}")
    }

    fun setFilter(filterType: String) {
        Log.i("CameraGLSurfaceView", "setFilter('$filterType') called. Queuing to GL thread. Renderer: $renderer")
        queueEvent {
            // renderer.setFilter sẽ log chi tiết
            renderer.setFilter(filterType)
        }
    }

    fun getCameraSurfaceTexture(): SurfaceTexture? {
        Log.i("CameraGLSurfaceView", "getCameraSurfaceTexture() called. Renderer: $renderer")
        val texture = renderer.surfaceTexture
        Log.i("CameraGLSurfaceView", "getCameraSurfaceTexture() returning: $texture. (From renderer.surfaceTexture)")
        return texture
    }
}