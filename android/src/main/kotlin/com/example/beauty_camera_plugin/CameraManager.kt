package com.example.beauty_camera_plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.view.TextureRegistry
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Camera Manager class that handles CameraX functionality
 */
class CameraManager(
    private val context: Context,
    private val flutterTexture: TextureRegistry.SurfaceTextureEntry,
    private val lifecycleOwner: LifecycleOwner
) {
    private val tag = "CameraManager"
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    
    // GPUImage for filtering
    private val gpuImage = GPUImage(context)
    private var currentFilter: GPUImageFilter = GPUImageFilter()
    private val surfaceTexture = flutterTexture.surfaceTexture()
    
    // YUV to RGB conversion
    private val yuvToRgbConverter = YuvToRgbConverter(context)
    
    // Current settings
    private var currentFlashMode = FlashMode.OFF
    private var currentCameraFacing = CameraFacing.BACK
    private var currentResolutionPreset = ResolutionPreset.HIGH
    
    // Initialize bitmap for processing
    private var processedBitmap: Bitmap? = null
    private var isProcessing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Display rotation listener
    private var displayManager: DisplayManager? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            // Update camera rotation when display rotates
            imageCapture?.targetRotation = getDisplayRotation()
            imageAnalysis?.targetRotation = getDisplayRotation()
        }
    }
    
    /**
     * Initialize the camera
     */
    suspend fun initializeCamera(): Int {
        // All CameraX operations must run on the main thread
        return withContext(Dispatchers.Main) {
            try {
                // Register display listener for rotation changes
                displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                displayManager?.registerDisplayListener(displayListener, mainHandler)
                
                // Get ProcessCameraProvider
                cameraProvider = suspendCoroutine { continuation ->
                    ProcessCameraProvider.getInstance(context).also { future ->
                        future.addListener({
                            try {
                                continuation.resume(future.get())
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to get camera provider", e)
                                continuation.resumeWithException(e)
                            }
                        }, mainExecutor)
                    }
                }
                
                // Configure camera use cases
                setupCameraUseCases()
                
                // Return the texture ID for Flutter to display
                flutterTexture.id().toInt()
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize camera: ${e.message}", e)
                throw e
            }
        }
    }
    
    /**
     * Setup camera use cases (preview, image capture, image analysis)
     */
    private fun setupCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera provider not initialized")
        
        // Unbind all use cases before rebinding
        cameraProvider.unbindAll()
        
        // Camera selector based on facing direction
        val cameraSelector = when (currentCameraFacing) {
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        
        // Get display rotation
        val rotation = getDisplayRotation()
        
        // Setup preview use case with a direct connection to surfaceTexture
        // This provides the best performance and compatibility
        val previewSize = currentResolutionPreset.getPreviewSize()
        preview = Preview.Builder()
            .setTargetResolution(previewSize)
            .setTargetRotation(rotation)
            .build()
            .also {
                // Create a SurfaceProvider from our SurfaceTexture
                it.setSurfaceProvider { request ->
                    // Configure surface texture
                    surfaceTexture.setDefaultBufferSize(
                        request.resolution.width,
                        request.resolution.height
                    )
                    
                    // Provide the surface directly to CameraX
                    val surface = Surface(surfaceTexture)
                    request.provideSurface(surface, backgroundExecutor) { 
                        // This callback is invoked when the surface is no longer needed
                        surface.release()
                    }
                }
            }
        
        // Setup image capture use case
        val captureSize = currentResolutionPreset.getCaptureSize()
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(captureSize)
            .setTargetRotation(rotation)
            .setFlashMode(getImageCaptureFlashMode(currentFlashMode))
            .build()
        
        // In this simplified approach, we'll skip the ImageAnalysis use case
        // for real-time filters, and just apply filters when taking photos
        
        // We'll keep the image analysis to detect scene information, but not try to render
        // the filtered output to the preview
        val analysisSize = currentResolutionPreset.getPreviewSize()
        
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(analysisSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) 
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { imageProxy ->
                    // Just analyze and close immediately - we're not applying visual filters in real-time
                    // to avoid the surface texture issues
                    try {
                        // Process image data if needed (metrics, scene detection, etc.)
                        // But don't try to render back to the preview
                        
                        // We could detect faces, lighting conditions, etc. here if needed
                    } catch (e: Exception) {
                        Log.e(tag, "Error in image analysis", e)
                    } finally {
                        // Always close the imageProxy when done
                        imageProxy.close()
                    }
                }
            }
        
        try {
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            
            // Initialize GPUImage
            gpuImage.setFilter(currentFilter)
            
        } catch (e: Exception) {
            Log.e(tag, "Use case binding failed", e)
        }
    }
    
    // Flag to track if we've already logged the native library error
    private var nativeLibraryErrorLogged = false
    
    // Flag to use fallback bitmap processing instead of GPUImage when native libraries fail
    private var useGpuImageFallback = false
    
    /**
     * Apply filter to the image from ImageAnalysis
     */
    private fun applyFilterToImage(imageProxy: ImageProxy) {
        try {
            val bitmap = processedBitmap ?: return
            
            // Convert YUV image to RGB bitmap
            yuvToRgbConverter.yuvToRgb(imageProxy, bitmap)
            
            // Apply filter to the bitmap (with fallback handling)
            val filteredBitmap = if (useGpuImageFallback) {
                // Fallback: Apply a simple filter directly on the bitmap without GPUImage
                applyFilterFallback(bitmap)
            } else {
                try {
                    // Try to use GPUImage for better quality filters
                    gpuImage.getBitmapWithFilterApplied(bitmap)
                } catch (e: UnsatisfiedLinkError) {
                    // Native library error - switch to fallback for future frames
                    useGpuImageFallback = true
                    if (!nativeLibraryErrorLogged) {
                        Log.w(tag, "GPUImage native library not available, using fallback filters", e)
                        nativeLibraryErrorLogged = true
                    }
                    
                    // Process this frame with the fallback
                    applyFilterFallback(bitmap)
                }
            }
            
            // For now, we won't attempt to draw filtered bitmaps directly to the surface
            // as this is causing issues. Instead, we'll rely on the CameraX Preview use case
            // for preview and just prepare the filter for when taking actual photos
            mainHandler.post {
                isProcessing = false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to process image", e)
            imageProxy.close()
            isProcessing = false
        }
    }
    
    /**
     * Take a picture and save it to the given path
     */
    fun takePicture(filePath: String, onComplete: (String?, Exception?) -> Unit) {
        val imageCapture = imageCapture ?: run {
            onComplete(null, IllegalStateException("Image capture not initialized"))
            return
        }
        
        // Create output file to hold the image
        val outputFile = File(filePath)
        if (!outputFile.parentFile?.exists()!!) {
            outputFile.parentFile?.mkdirs()
        }
        
        // Setup image capture output
        val outputOptions: ImageCapture.OutputFileOptions
        
        // Use the more compatible direct file output
        outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        
        // Take the picture
        imageCapture.takePicture(
            outputOptions,
            backgroundExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed: ${exception.message}", exception)
                    onComplete(null, exception)
                }
                
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    Log.d(tag, "Photo capture succeeded: ${outputFile.absolutePath}")
                    
                    // Apply filter to the captured image if we're not using the NONE filter
                    if (currentFilter !is GPUImageFilter || currentFilter.javaClass.simpleName != "GPUImageFilter") {
                        try {
                            // Load the image into a bitmap
                            var bitmap = android.graphics.BitmapFactory.decodeFile(outputFile.absolutePath)
                            
                            // Apply the selected filter
                            try {
                                bitmap = if (useGpuImageFallback) {
                                    applyFilterFallback(bitmap)
                                } else {
                                    try {
                                        gpuImage.getBitmapWithFilterApplied(bitmap)
                                    } catch (e: Exception) {
                                        Log.w(tag, "GPUImage filter failed, using fallback", e)
                                        useGpuImageFallback = true
                                        applyFilterFallback(bitmap)
                                    }
                                }
                                
                                // Save the filtered bitmap back to the file
                                outputFile.outputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Filter application failed", e)
                                // Continue with the original image if filter fails
                            }
                            
                            // Recycle the bitmap to free memory
                            bitmap.recycle()
                        } catch (e: Exception) {
                            Log.e(tag, "Error processing captured image", e)
                            // Continue with the original image if processing fails
                        }
                    }
                    
                    // Complete the operation with the file path
                    onComplete(outputFile.absolutePath, null)
                }
            }
        )
    }
    
    /**
     * Set the filter to be applied
     */
    fun setFilter(filterType: FilterType): Boolean {
        currentFilter = when (filterType) {
            FilterType.NONE -> GPUImageFilter()
            FilterType.SEPIA -> GPUImageSepiaToneFilter()
            FilterType.GRAYSCALE -> GPUImageGrayscaleFilter()
            FilterType.INVERT -> GPUImageColorInvertFilter()
            FilterType.BRIGHTNESS -> GPUImageBrightnessFilter(0.5f)
            FilterType.CONTRAST -> GPUImageContrastFilter(1.5f)
            FilterType.SATURATION -> GPUImageSaturationFilter(1.5f)
            FilterType.GAMMA -> GPUImageGammaFilter(2.0f)
            FilterType.MONOCHROME -> GPUImageMonochromeFilter()
        }
        
        gpuImage.setFilter(currentFilter)
        return true
    }
    
    /**
     * Set the flash mode
     */
    fun setFlashMode(flashMode: FlashMode): Boolean {
        currentFlashMode = flashMode
        
        imageCapture?.flashMode = getImageCaptureFlashMode(flashMode)
        return true
    }
    
    /**
     * Convert FlashMode enum to CameraX's ImageCapture.FlashMode
     */
    private fun getImageCaptureFlashMode(flashMode: FlashMode): Int {
        return when (flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }
    
    /**
     * Get the current display rotation
     */
    private fun getDisplayRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay.rotation
    }
    
    /**
     * Dispose camera resources
     */
    fun dispose() {
        try {
            // Unregister display listener
            displayManager?.unregisterDisplayListener(displayListener)
            displayManager = null
            
            // Shutdown background executor
            backgroundExecutor.shutdown()
            
            // Unbind all use cases
            cameraProvider?.unbindAll()
            
            // Release the GPUImage resources
            currentFilter.destroy()
            gpuImage.deleteImage()
            
            // Clear bitmap
            processedBitmap?.recycle()
            processedBitmap = null
            
            // Release the flutter texture entry
            flutterTexture.release()
        } catch (e: Exception) {
            Log.e(tag, "Error disposing camera", e)
        }
    }
    
    /**
     * Fallback filter implementation when GPUImage native library fails to load
     * This uses standard Android APIs instead of GPUImage
     */
    private fun applyFilterFallback(bitmap: Bitmap): Bitmap {
        // Check if config is null and use a default value if needed
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val result = bitmap.copy(config, true)
        val filterType = currentFilter
        
        // Apply simple filters using Paint and ColorMatrix
        try {
            // Match filter type using string comparison since we might not have access to GPUImage classes
            val filterClassName = filterType.javaClass.simpleName
            
            when {
                filterClassName.contains("Grayscale") -> {
                    // Create a grayscale ColorMatrix
                    val canvas = android.graphics.Canvas(result)
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(
                            floatArrayOf(
                                0.33f, 0.33f, 0.33f, 0f, 0f,
                                0.33f, 0.33f, 0.33f, 0f, 0f,
                                0.33f, 0.33f, 0.33f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                }
                filterClassName.contains("Sepia") -> {
                    // Create a sepia ColorMatrix
                    val canvas = android.graphics.Canvas(result)
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(
                            floatArrayOf(
                                1.0f, 0.0f, 0.0f, 0f, 40f,
                                0.0f, 0.9f, 0.0f, 0f, 20f,
                                0.0f, 0.0f, 0.7f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                }
                filterClassName.contains("Invert") -> {
                    // Create an inverted ColorMatrix
                    val canvas = android.graphics.Canvas(result)
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                }
                else -> {
                    // No filter or unsupported filter, just return the original bitmap
                    return bitmap
                }
            }
            return result
        } catch (e: Exception) {
            Log.e(tag, "Error in fallback filter", e)
            return bitmap
        }
    }
    
    /**
     * Set the focus point for the camera
     * 
     * @param x Normalized x coordinate (0.0 to 1.0)
     * @param y Normalized y coordinate (0.0 to 1.0)
     * @return Boolean indicating success
     */
    suspend fun setFocusPoint(x: Float, y: Float): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                val cam = camera ?: return@withContext false
                
                // Convert normalized coordinates to camera-normalized MeteringPoint
                val factory = SurfaceOrientedMeteringPointFactory(
                    /* width */ 1.0f,
                    /* height */ 1.0f
                )
                val point = factory.createPoint(x, y)
                
                // Create focus action with auto-cancel
                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS) // Auto cancel after 3 seconds
                    .build()
                
                // Execute focus action
                val future = cam.cameraControl.startFocusAndMetering(action)
                
                // Wait for the focus to complete
                val result = suspendCoroutine<Boolean> { continuation ->
                    future.addListener({
                        try {
                            val meteringResult = future.get()
                            val success = meteringResult.isFocusSuccessful
                            Log.d(tag, "Focus set at ($x, $y), success: $success")
                            continuation.resume(success)
                        } catch (e: Exception) {
                            Log.e(tag, "Focus failed", e)
                            continuation.resume(false)
                        }
                    }, mainExecutor)
                }
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(tag, "Failed to set focus point", e)
                return@withContext false
            }
        }
    }
}
