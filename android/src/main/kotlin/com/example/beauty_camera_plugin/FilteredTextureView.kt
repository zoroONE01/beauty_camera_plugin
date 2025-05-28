package com.example.beauty_camera_plugin

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.Surface // Added for Surface.ROTATION_*
import android.view.TextureView
import android.app.Activity // Added to get display rotation

class FilteredTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var currentDrawnBitmap: Bitmap? = null
    private val bitmapPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true 
    }
    private val TAG = "FilteredTextureView"
    private var surfaceReady = false
    private val bitmapLock = Any()
    private var nextBitmapToDraw: Bitmap? = null
    // private var currentDisplayOrientationDegrees: Float = 0f // REMOVED - No longer needed

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun updateBitmap(newBitmap: Bitmap?) {
        synchronized(bitmapLock) {
            nextBitmapToDraw = newBitmap
        }
        if (surfaceReady && isAvailable) {
            drawFrame()
        }
    }

    // fun updateDisplayOrientation(degrees: Float) { // REMOVED - No longer needed
    //     if (currentDisplayOrientationDegrees != degrees) {
    //         currentDisplayOrientationDegrees = degrees
    //         invalidate() // Request redraw with new orientation
    //     }
    // }

    private fun drawFrame() {
        val bitmapToRender: Bitmap?
        synchronized(bitmapLock) {
            bitmapToRender = nextBitmapToDraw
        }

        if (bitmapToRender != null && !bitmapToRender.isRecycled && isAvailable) {
            val canvas = lockCanvas() ?: return
            try {
                canvas.drawColor(Color.BLACK)

                val canvasWidth = width.toFloat()
                val canvasHeight = height.toFloat()
                val bitmapWidth = bitmapToRender.width.toFloat()
                val bitmapHeight = bitmapToRender.height.toFloat()

                val matrix = Matrix() // This matrix is not used with the current logic.
                                     // scaleMatrix is used instead.
                val viewRect = RectF(0f, 0f, canvasWidth, canvasHeight)
                val bufferRect = RectF(0f, 0f, bitmapWidth, bitmapHeight)
                
                // Matrix for scaling (FIT_CENTER)
                val scaleMatrix = Matrix()
                scaleMatrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)

                canvas.save()
                // Canvas rotation is no longer needed here as BitmapProcessor provides an upright bitmap.
                // The Log.d related to disabling canvas rotation can also be removed.
                
                // Draw the bitmap using the scaling matrix
                canvas.drawBitmap(bitmapToRender, scaleMatrix, bitmapPaint)
                canvas.restore()

                currentDrawnBitmap = bitmapToRender
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing bitmap on canvas", e)
            } finally {
                unlockCanvasAndPost(canvas)
            }
        } else if (bitmapToRender?.isRecycled == true) {
            Log.w(TAG, "Attempted to draw a recycled bitmap in drawFrame.")
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceReady = true
        if (nextBitmapToDraw != null) {
            drawFrame()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        drawFrame()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceReady = false
        synchronized(bitmapLock) {
            nextBitmapToDraw = null
            currentDrawnBitmap = null
        }
        return true 
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Not directly used for manual drawing
    }
}
