package com.example.beauty_camera_plugin

import android.app.Activity
import android.content.Context
import android.graphics.* 
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.LinkedList

class CameraManager(
    private val context: Context,
    private var activity: Activity,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var previewView: PreviewView? = null

    private var currentFilterType: String = "none"
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentFlashMode: Int = ImageCapture.FLASH_MODE_OFF

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var _isDisposed = false
    
    // Flag to track if a capture is in progress
    private var isCaptureInProgress = false
    // Queue to track pending capture requests
    private val pendingCaptureRequests = LinkedList<CaptureRequest>()
    
    // Data class to hold a capture request
    private data class CaptureRequest(
        val photoFile: File,
        val callback: (String?, String?) -> Unit
    )

    fun initializeCamera(callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "initializeCamera called")
        _isDisposed = false
        
        // Check if previewView is set
        if (previewView == null) {
            Log.e(TAG, "PreviewView is null in initializeCamera. Cannot initialize camera.")
            callback(false, "PreviewView is not set. Cannot initialize camera.")
            return
        }
        Log.d(TAG, "PreviewView is set in initializeCamera.")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "Camera provider obtained successfully.")
                
                // Make sure previewView is ready
                if (previewView?.surfaceProvider != null) {
                    Log.d(TAG, "PreviewView surface provider is available. Binding use cases.")
                    bindCameraUseCases()
                    callback(true, null)
                } else {
                    Log.e(TAG, "PreviewView surface provider is null. Posting to check again.")
                    // Try to attach a listener to the previewView to know when it's ready
                    previewView?.post {
                        if (previewView?.surfaceProvider != null) {
                            Log.d(TAG, "PreviewView surface provider is now available after post. Binding use cases.")
                            bindCameraUseCases()
                            callback(true, null)
                        } else {
                            Log.e(TAG, "PreviewView surface provider is still null after post.")
                            callback(false, "PreviewView surface provider is not available.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider initialization failed", e)
                callback(false, "Camera provider initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun createPreviewUseCase(): Preview {
        return Preview.Builder()
            .apply {
                setTargetRotation(
                    previewView?.display?.rotation ?: Surface.ROTATION_0
                )
                if (currentFilterType != "none") {
                    val camera2Interop = Camera2Interop.Extender(this)
                    applyEffectToPreview(camera2Interop, currentFilterType)
                }
            }
            .build()
            .also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }
    }

    private fun createImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .setTargetRotation(previewView?.display?.rotation ?: Surface.ROTATION_0)
            .setFlashMode(currentFlashMode) // Apply current flash mode
            .build()
    }

    private fun bindCameraUseCases() {
        Log.d(TAG, "bindCameraUseCases called")
        if (cameraProvider == null) {
            Log.e(TAG, "CameraProvider is null in bindCameraUseCases. Cannot bind.")
            return
        }
        if (previewView == null) {
            Log.e(TAG, "PreviewView is null in bindCameraUseCases. Cannot bind.")
            return
        }
        Log.d(TAG, "CameraProvider and PreviewView are not null in bindCameraUseCases.")
        
        if (previewView?.surfaceProvider == null) {
            Log.e(TAG, "SurfaceProvider is null in bindCameraUseCases. Waiting for it to be ready.")
            previewView?.post {
                if (previewView?.surfaceProvider != null) {
                    Log.d(TAG, "SurfaceProvider is now ready in bindCameraUseCases (after post). Retrying bind.")
                    bindCameraUseCases() // Retry binding once surface provider is ready
                } else {
                    Log.e(TAG, "SurfaceProvider is still null in bindCameraUseCases (after post).")
                }
            }
            return
        }
        Log.d(TAG, "SurfaceProvider is available in bindCameraUseCases.")

        Log.d(TAG, "Binding camera use cases. Current filter: $currentFilterType, CameraSelector: $cameraSelector")
        
        try {
            Log.d(TAG, "Unbinding all previous use cases.") // Khôi phục lại dòng này
            cameraProvider?.unbindAll() // Khôi phục lại dòng này

            Log.d(TAG, "Creating new Preview and ImageCapture use cases.")
            preview = createPreviewUseCase()
            imageCapture = createImageCaptureUseCase()

            Log.d(TAG, "Binding to lifecycle with LifecycleOwner: $lifecycleOwner")
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            
            if (camera != null) {
                Log.d(TAG, "Camera use cases bound successfully to camera: ${camera?.cameraInfo?.implementationType}")
            } else {
                Log.e(TAG, "Camera object is null after bindToLifecycle.")
            }
            
            // Ensure the surface provider is set
            Log.d(TAG, "Setting surface provider for preview.")
            preview?.setSurfaceProvider(previewView!!.surfaceProvider)
            Log.d(TAG, "Surface provider set for preview. Requesting layout for PreviewView.")
            previewView?.requestLayout() // Explicitly request layout
            
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    fun takePicture(photoFile: File, callback: (String?, String?) -> Unit) {
        Log.d(TAG, "Taking picture requested to file: ${photoFile.absolutePath}")
        
        // Check if imageCapture is initialized
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture use case not initialized.")
            callback(null, "ImageCapture use case not initialized. Camera may not be ready.")
            return
        }

        // Ensure directory exists
        val parentDir = photoFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        
        // If a capture is already in progress, queue this request
        if (isCaptureInProgress) {
            Log.d(TAG, "Capture already in progress, queueing request")
            pendingCaptureRequests.add(CaptureRequest(photoFile, callback))
            return
        }
        
        isCaptureInProgress = true

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Image capture successful, processing result")
                    
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    val filePath = savedUri.path
                    
                    // If a filter is active that requires post-processing
                    if (currentFilterType != "none" && currentFilterType != "mono" && 
                        currentFilterType != "negative" && currentFilterType != "solarize" && 
                        currentFilterType != "posterize") {
                        
                        // Perform post-processing on a background thread
                        scope.launch(Dispatchers.IO) {
                            try {
                                Log.d(TAG, "Applying filter to captured image: $currentFilterType")
                                applyFilterToSavedImage(photoFile.absolutePath, currentFilterType)
                                launch(Dispatchers.Main) {
                                    // Notify caller of success on main thread
                                    callback(photoFile.absolutePath, null)
                                    processNextCaptureRequest()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to apply filter to image", e)
                                launch(Dispatchers.Main) {
                                    callback(null, "Failed to apply filter: ${e.message}")
                                    processNextCaptureRequest()
                                }
                            }
                        }
                    } else {
                        // No post-processing needed
                        callback(filePath, null)
                        processNextCaptureRequest()
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    callback(null, "Photo capture failed: ${exception.message}")
                    processNextCaptureRequest()
                }
            }
        )
    }
    
    private fun processNextCaptureRequest() {
        // Process the next capture request in the queue if any
        if (pendingCaptureRequests.isNotEmpty()) {
            val nextRequest = pendingCaptureRequests.poll()
            nextRequest?.let { request ->
                Log.d(TAG, "Processing next queued capture request")
                // We need to set isCaptureInProgress to false before calling takePicture
                // so that the next capture isn't queued
                isCaptureInProgress = false
                takePicture(request.photoFile, request.callback)
            }
        } else {
            // No more pending requests
            isCaptureInProgress = false
        }
    }

    private fun applyFilterToSavedImage(filePath: String, filterType: String) {
        try {
            // Load the bitmap from file
            val originalBitmap = BitmapFactory.decodeFile(filePath)
            
            // Apply appropriate filter based on type
            val filteredBitmap = when (filterType.lowercase()) {
                "sepia" -> applySepia(originalBitmap)
                "grayscale", "mono" -> applyGrayscale(originalBitmap)
                else -> originalBitmap // If no filter matched, return the original
            }
            
            // If a new bitmap was created, save it back to the original file
            if (filteredBitmap != originalBitmap) {
                val outputStream = FileOutputStream(filePath)
                filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                outputStream.flush()
                outputStream.close()
                
                // Recycle the bitmaps to free memory
                if (originalBitmap != filteredBitmap) {
                    originalBitmap.recycle()
                }
                filteredBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter to image", e)
            throw e
        }
    }

    private fun applySepia(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val sepiaMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(sepiaMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayscaleMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun setFilter(filterType: String) {
        this.currentFilterType = filterType.lowercase()
        // Rebind use cases to apply the filter to the preview
        bindCameraUseCases()
    }

    private fun applyEffectToPreview(
        interop: androidx.camera.camera2.interop.Camera2Interop.Extender<*>, 
        filterType: String
    ) {
        when (filterType.lowercase()) {
            "grayscale", "mono" -> {
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE, 
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_MONO
                )
            }
            "negative" -> {
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE, 
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE
                )
            }
            "solarize" -> {
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE, 
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE
                )
            }
            "posterize" -> {
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE, 
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE
                )
            }
            "sepia", "none" -> {
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE,
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
            else -> { // Default case for any other filterType or if it's an unrecognized type
                interop.setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_EFFECT_MODE,
                    android.hardware.camera2.CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
        }
    }

    fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        bindCameraUseCases()
    }

    fun toggleFlash(callback: (String) -> Unit) {
        currentFlashMode = when (currentFlashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_OFF // Default fallback
        }
        // Re-create and bind imageCapture use case for flash mode to take effect on some devices
        // Or, try to set it on the fly if supported by CameraControl
        imageCapture?.flashMode = currentFlashMode // Update existing use case
        // For some devices or CameraX versions, rebinding might be more reliable:
        // bindCameraUseCases() 
        val flashModeString = when (currentFlashMode) {
            ImageCapture.FLASH_MODE_ON -> "on"
            ImageCapture.FLASH_MODE_AUTO -> "auto"
            else -> "off"
        }
        callback(flashModeString)
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setLinearZoom(zoomRatio.coerceIn(0f, 1f))
    }

    fun setPreviewView(view: PreviewView) {
        Log.d(TAG, "setPreviewView called. New PreviewView: $view")
        this.previewView = view
        // If camera is already initialized, set surface provider. Otherwise, it will be set during init.
        if (cameraProvider != null && preview != null) {
            Log.d(TAG, "CameraProvider and Preview use case exist. Setting surface provider for new PreviewView.")
            if (view.surfaceProvider != null) {
                preview?.setSurfaceProvider(view.surfaceProvider)
                Log.d(TAG, "Surface provider set for new PreviewView.")
            } else {
                Log.e(TAG, "New PreviewView's surfaceProvider is null in setPreviewView.")
            }
        } else {
            Log.d(TAG, "CameraProvider or Preview use case is null in setPreviewView. Surface provider will be set during initialization or binding.")
        }
    }
    
    fun updateActivity(newActivity: Activity) {
        this.activity = newActivity
    }

    fun isDisposed(): Boolean = _isDisposed

    fun getActivity(): Activity = activity

    fun dispose() {
        if (_isDisposed) return
        _isDisposed = true
        try {
            // Clear pending capture requests
            if (pendingCaptureRequests.isNotEmpty()) {
                Log.d(TAG, "Clearing ${pendingCaptureRequests.size} pending capture requests")
                pendingCaptureRequests.forEach { 
                    it.callback(null, "Camera disposed while request was pending")
                }
                pendingCaptureRequests.clear()
            }
            
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            // Cancel coroutines
            (scope.coroutineContext[Job] as? Job)?.cancel()
            previewView = null // Release reference to PreviewView
            cameraProvider = null
            camera = null
            preview = null
            imageCapture = null
            isCaptureInProgress = false
            Log.d(TAG, "CameraManager disposed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during CameraManager disposal", e)
        }
    }
}
