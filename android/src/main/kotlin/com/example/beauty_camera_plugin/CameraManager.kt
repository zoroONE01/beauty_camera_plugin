package com.example.beauty_camera_plugin

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture // Thêm import này
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
// import android.hardware.camera2.CaptureRequest // Không còn dùng trực tiếp cho preview filter
import android.net.Uri
import android.util.Log
import android.util.Size // Thêm import này
import android.view.Surface
import android.view.OrientationEventListener
import androidx.camera.camera2.interop.Camera2CameraInfo
// import androidx.camera.camera2.interop.Camera2Interop // Không còn dùng cho preview filter
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
// import androidx.camera.view.PreviewView // Không dùng PreviewView nữa
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
    private val lifecycleOwner: LifecycleOwner,
    initialLensFacing: Int? = null,
    initialZoomRatio: Float? = null,
    initialFlashMode: String? = null,
    initialFilterType: String? = null
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraGLSurfaceView: CameraGLSurfaceView? = null

    private var currentFilterType: String
    private var cameraSelector: CameraSelector
    private var currentFlashMode: Int
    private var currentZoomRatio: Float = 0.5f // Default zoom, can be overridden

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var _isDisposed = false

    init {
        this.cameraSelector = initialLensFacing?.let {
            CameraSelector.Builder().requireLensFacing(it).build()
        } ?: CameraSelector.DEFAULT_BACK_CAMERA
        Log.i(TAG, "Initial lens facing: ${if (this.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"} (from saved: $initialLensFacing)")

        this.currentZoomRatio = initialZoomRatio ?: 0.0f // CameraX zoom is 0.0 (min) to 1.0 (max linear)
        Log.i(TAG, "Initial zoom ratio: ${this.currentZoomRatio} (from saved: $initialZoomRatio)")

        this.currentFlashMode = when (initialFlashMode?.lowercase()) {
            "on" -> ImageCapture.FLASH_MODE_ON
            "auto" -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        Log.i(TAG, "Initial flash mode: ${this.currentFlashMode} (from saved: $initialFlashMode)")

        this.currentFilterType = initialFilterType ?: "none"
        Log.i(TAG, "Initial filter type: ${this.currentFilterType} (from saved: $initialFilterType)")

        // Apply initial filter to renderer if view is already available (might be too early)
        // This will be reapplied in bindCameraUseCases or when setFilter is called.
        // cameraGLSurfaceView?.setFilter(this.currentFilterType)
    }
    
    // Flag to track if a capture is in progress
    private var isCaptureInProgress = false
    // Queue to track pending capture requests
    private val pendingCaptureRequests = LinkedList<CaptureRequest>()
    
    // Data class to hold a capture request
    private data class CaptureRequest(
        val photoFile: File,
        val callback: (String?, String?) -> Unit
    )

    private var orientationEventListener: OrientationEventListener? = null
    private var lastKnownRotation: Int = Surface.ROTATION_0
    private var targetResolution: Size? = null // Để lưu kích thước mục tiêu cho preview

    fun initializeCamera(glView: CameraGLSurfaceView, callback: (Boolean, String?) -> Unit) {
        Log.i(TAG, ">>> initializeCamera START. GLSurfaceView (param): $glView, IsDisposed: $_isDisposed, Current cameraGLSurfaceView: ${this.cameraGLSurfaceView}")
        if (_isDisposed) {
            Log.w(TAG, "initializeCamera called on a disposed CameraManager. Re-initializing. Setting _isDisposed to false.")
            // Potentially reset some state if needed, though _isDisposed = false is key
        }
        _isDisposed = false // Ensure manager is not marked as disposed
        this.cameraGLSurfaceView = glView
        Log.i(TAG, "initializeCamera: Assigned GLSurfaceView. this.cameraGLSurfaceView: ${this.cameraGLSurfaceView}, renderer: ${this.cameraGLSurfaceView?.renderer}, surfaceTexture: ${this.cameraGLSurfaceView?.getCameraSurfaceTexture()}")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            Log.i(TAG, "initializeCamera: CameraProviderFuture listener invoked. IsDisposed: $_isDisposed")
            if (_isDisposed) {
                Log.w(TAG, "initializeCamera: CameraProviderFuture listener: CameraManager is disposed. Aborting.")
                callback(false, "CameraManager disposed during initialization.")
                return@addListener
            }
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.i(TAG, "initializeCamera: CameraProvider obtained: $cameraProvider. Calling bindCameraUseCases().")
                // Không cần chờ surfaceProvider của PreviewView nữa
                // GLSurfaceView sẽ tự quản lý surface của nó.
                // Chúng ta sẽ lấy SurfaceTexture từ renderer của GLSurfaceView.
                bindCameraUseCases() // Gọi bindCameraUseCases trực tiếp
                Log.i(TAG, "initializeCamera: bindCameraUseCases() call completed.")
                callback(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "initializeCamera: Camera provider initialization failed", e)
                callback(false, "Camera provider initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
        Log.i(TAG, "initializeCamera: Added listener to cameraProviderFuture.")

        // Setup orientation listener
        if (orientationEventListener == null) {
            orientationEventListener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    val newDisplayRotation = cameraGLSurfaceView?.display?.rotation ?: Surface.ROTATION_0
                    if (newDisplayRotation != lastKnownRotation) {
                        lastKnownRotation = newDisplayRotation
                        Log.d(TAG, "Device display rotation changed to: $newDisplayRotation")
                        updateUseCaseRotations(newDisplayRotation)
                        
                        // Thông báo cho GLRenderer về sự thay đổi hướng hiển thị
                        // và các thông số camera khác nếu cần.
                        val sensorRotation = camera?.cameraInfo?.sensorRotationDegrees ?: 0
                        cameraGLSurfaceView?.renderer?.setCameraParameters(
                            targetResolution?.width ?: 0,
                            targetResolution?.height ?: 0,
                            sensorRotation,
                            newDisplayRotation
                        )
                    }
                }
            }
            orientationEventListener?.enable()
        }
    }

    private fun createPreviewUseCase(surfaceTexture: SurfaceTexture): Preview {
        // Lấy kích thước mục tiêu (ví dụ: 1280x720)
        // Bạn có thể muốn làm cho nó có thể cấu hình hoặc chọn dựa trên thiết bị
        targetResolution = Size(1280, 720) // Ví dụ
        surfaceTexture.setDefaultBufferSize(targetResolution!!.width, targetResolution!!.height)
        
        // Thông báo cho GLRenderer về kích thước preview và hướng ban đầu
        // Lấy sensorRotation ở đây có thể chưa chính xác nếu camera chưa bind.
        // Sẽ tốt hơn nếu cập nhật sau khi camera được bind.
        // cameraGLSurfaceView?.renderer?.setCameraParameters(
        //     targetResolution!!.width,
        //     targetResolution!!.height,
        //     0, // Tạm thời là 0, sẽ cập nhật sau khi bind
        //     cameraGLSurfaceView?.display?.rotation ?: Surface.ROTATION_0
        // )

        return Preview.Builder()
            .setTargetResolution(targetResolution!!) // Đặt kích thước mục tiêu
            .setTargetRotation(cameraGLSurfaceView?.display?.rotation ?: Surface.ROTATION_0) // Quan trọng: CameraX sẽ cố gắng cung cấp frame đã xoay
            .build()
            .also {
                // Không dùng setSurfaceProvider với PreviewView nữa
                // Thay vào đó, chúng ta sẽ cung cấp SurfaceTexture trực tiếp
                it.setSurfaceProvider { request ->
                    val surface = Surface(surfaceTexture)
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                        Log.d(TAG, "Surface released for Preview: ${it.surface}")
                        surface.release() // Quan trọng: giải phóng surface khi không dùng nữa
                    }
                }
                Log.d(TAG, "Preview use case created with SurfaceTexture target.")
            }
    }

    private fun createImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .setTargetRotation(cameraGLSurfaceView?.display?.rotation ?: Surface.ROTATION_0)
            .setFlashMode(currentFlashMode) // Apply current flash mode
            .build()
    }

    private fun bindCameraUseCases() {
        Log.i(TAG, ">>> bindCameraUseCases START. CameraProvider: $cameraProvider, GLView: $cameraGLSurfaceView, IsDisposed: $_isDisposed")
        if (_isDisposed) {
            Log.w(TAG, "bindCameraUseCases: CameraManager is disposed. Aborting.")
            return
        }
        if (cameraProvider == null) {
            Log.e(TAG, "bindCameraUseCases: CameraProvider is null. Cannot bind.")
            // Consider invoking callback with error if this happens after initialization attempt
            return
        }
        val glView = cameraGLSurfaceView // Capture current value
        if (glView == null) {
            Log.e(TAG, "bindCameraUseCases: CameraGLSurfaceView (this.cameraGLSurfaceView) is null. Cannot bind. This should not happen if initializeCamera was called.")
            // Consider invoking callback with error
            return
        }
        Log.i(TAG, "bindCameraUseCases: GLView instance: $glView, GLView.renderer: ${glView.renderer}")

        val surfaceTexture = glView.getCameraSurfaceTexture() // This calls Log in CameraGLSurfaceView
        Log.i(TAG, "bindCameraUseCases: SurfaceTexture from glView.getCameraSurfaceTexture(): $surfaceTexture")

        if (surfaceTexture == null) {
            Log.e(TAG, "bindCameraUseCases: SurfaceTexture from GLRenderer is NULL. GLSurfaceView might not be ready. Posting retry.")
            // GLSurfaceView có thể chưa sẵn sàng. Thử lại sau một chút.
            glView.post {
                Log.i(TAG, "bindCameraUseCases: Retry block posted to glView. IsDisposed: $_isDisposed")
                if (_isDisposed) {
                    Log.w(TAG, "bindCameraUseCases: Retry: CameraManager is disposed. Aborting retry.")
                    return@post
                }
                val currentSurfaceTextureInRetry = glView.getCameraSurfaceTexture()
                Log.i(TAG, "bindCameraUseCases: Retrying bindCameraUseCases via glView.post. Current SurfaceTexture from view in retry: $currentSurfaceTextureInRetry")
                if (currentSurfaceTextureInRetry != null) {
                    bindCameraUseCases() // Recursive call, ensure it's safe
                } else {
                    Log.e(TAG, "bindCameraUseCases: Retry: SurfaceTexture is STILL NULL in retry. Not calling bindCameraUseCases again immediately.")
                    // Consider a more robust retry mechanism if this happens frequently, e.g., with a delay or max attempts.
                }
            }
            Log.i(TAG, "bindCameraUseCases: Posted retry to glView. Returning for now.")
            return
        }
        Log.i(TAG, "bindCameraUseCases: SurfaceTexture obtained successfully: $surfaceTexture. Proceeding with binding.")

        // Apply the current (possibly restored) filter type to the GLSurfaceView's renderer
        // This ensures the renderer is up-to-date before the camera preview starts.
        cameraGLSurfaceView?.setFilter(currentFilterType)
        Log.i(TAG, "bindCameraUseCases: Applied filter '$currentFilterType' to GLSurfaceView renderer.")

        Log.i(TAG, "bindCameraUseCases: Binding camera use cases. Current filter: $currentFilterType, CameraSelector: $cameraSelector, FlashMode: $currentFlashMode, Zoom: $currentZoomRatio")
        
        try {
            Log.i(TAG, "bindCameraUseCases: Unbinding all previous use cases before new bind.")
            cameraProvider?.unbindAll()
            Log.i(TAG, "bindCameraUseCases: Successfully unbound all previous use cases.")

            preview = createPreviewUseCase(surfaceTexture)
            imageCapture = createImageCaptureUseCase()
            Log.i(TAG, "bindCameraUseCases: Created new Preview and ImageCapture use cases. Preview: $preview, ImageCapture: $imageCapture")

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            Log.i(TAG, "bindCameraUseCases: bindToLifecycle call completed. Camera object: $camera")
            
            if (camera != null) {
                Log.i(TAG, "bindCameraUseCases: Camera use cases bound successfully to camera: ${camera?.cameraInfo?.implementationType}")
                // Log available effects (vẫn hữu ích để biết khả năng của phần cứng)
                try {
                    val camera2CameraInfo = Camera2CameraInfo.from(camera!!.cameraInfo)
                    val availableEffects = camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                    Log.d(TAG, "bindCameraUseCases: Available CONTROL_EFFECT_MODES (hardware): ${availableEffects?.joinToString { effect -> effectToString(effect) }}")
                    
                    val sensorRotation = camera!!.cameraInfo.sensorRotationDegrees
                    val currentDisplayRotation = cameraGLSurfaceView?.display?.rotation ?: Surface.ROTATION_0
                    lastKnownRotation = currentDisplayRotation
                    
                    Log.i(TAG, "bindCameraUseCases: Camera bound. SensorRotation: $sensorRotation, DisplayRotation: $currentDisplayRotation, PreviewSize: $targetResolution")

                    cameraGLSurfaceView?.renderer?.setCameraParameters(
                        targetResolution?.width ?: 0,
                        targetResolution?.height ?: 0,
                        sensorRotation,
                        currentDisplayRotation
                    )
                    Log.i(TAG, "bindCameraUseCases: Called renderer.setCameraParameters.")

                    // Apply initial zoom after camera is bound
                    camera?.cameraControl?.setLinearZoom(currentZoomRatio)
                    Log.i(TAG, "bindCameraUseCases: Applied initial zoom ratio: $currentZoomRatio")


                } catch (e: Exception) {
                    Log.e(TAG, "bindCameraUseCases: Error getting camera characteristics or setting params", e)
                }
            } else {
                Log.e(TAG, "bindCameraUseCases: Camera object is NULL after bindToLifecycle.")
            }
            // Không cần requestLayout cho GLSurfaceView ở đây
        } catch (exc: Exception) {
            Log.e(TAG, "bindCameraUseCases: Use case binding failed with exception", exc)
            // Consider invoking callback with error
        }
        Log.i(TAG, "<<< bindCameraUseCases END. Camera: $camera")
    }

// Getters for current state
fun getCurrentLensFacing(): Int {
    return if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
}

fun getCurrentZoomRatio(): Float {
    // Try to get live value if camera is active, otherwise return stored
    return camera?.cameraInfo?.zoomState?.value?.linearZoom ?: currentZoomRatio
}

fun getCurrentFlashMode(): String {
    return when (currentFlashMode) {
        ImageCapture.FLASH_MODE_ON -> "on"
        ImageCapture.FLASH_MODE_AUTO -> "auto"
        else -> "off"
    }
}

fun getCurrentFilterType(): String {
    return currentFilterType
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
                    if (currentFilterType != "none" &&
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
        Log.d(TAG, "CameraManager setFilter called with: $filterType")
        this.currentFilterType = filterType.lowercase()
        // Thông báo cho CameraGLSurfaceView (và renderer của nó) để thay đổi filter
        cameraGLSurfaceView?.setFilter(this.currentFilterType)
        // Không cần bindCameraUseCases() nữa vì filter được xử lý bởi OpenGL
    }

    // Hàm applyEffectToPreview không còn cần thiết vì filter được xử lý bởi GLRenderer
    // private fun applyEffectToPreview(...) { ... }


    fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        // Cần rebind để sử dụng camera mới
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
        val clampedZoom = zoomRatio.coerceIn(0f, 1f)
        camera?.cameraControl?.setLinearZoom(clampedZoom)
        currentZoomRatio = clampedZoom // Update stored zoom ratio
        Log.d(TAG, "Set zoom to $clampedZoom. Stored currentZoomRatio: $currentZoomRatio")
    }

    // Hàm setPreviewView(view: PreviewView) không còn cần thiết nữa
    // Thay vào đó, CameraGLSurfaceView được truyền vào khi initializeCamera

    fun updateActivity(newActivity: Activity) {
        this.activity = newActivity
    }

    fun isDisposed(): Boolean = _isDisposed

    fun getActivity(): Activity = activity

    fun dispose() {
        Log.i(TAG, "dispose() called. Current state: _isDisposed=$_isDisposed")
        if (_isDisposed) {
            Log.d(TAG, "dispose() called but already disposed.")
            return
        }
        _isDisposed = true
        Log.i(TAG, "dispose(): Set _isDisposed to true. Proceeding with resource cleanup.")
        try {
            // Clear pending capture requests
            if (pendingCaptureRequests.isNotEmpty()) {
                Log.i(TAG, "Clearing ${pendingCaptureRequests.size} pending capture requests during dispose.")
                pendingCaptureRequests.forEach {
                    it.callback(null, "Camera disposed while request was pending")
                }
                pendingCaptureRequests.clear()
            }
            
            Log.d(TAG, "dispose(): Unbinding all camera use cases. CameraProvider: $cameraProvider")
            cameraProvider?.unbindAll()
            Log.d(TAG, "dispose(): Shutting down cameraExecutor.")
            cameraExecutor.shutdown() // Consider awaitTermination for clean shutdown
            
            Log.d(TAG, "dispose(): Cancelling coroutine scope.")
            (scope.coroutineContext[Job] as? Job)?.cancel()
            
            Log.d(TAG, "dispose(): Nullifying references.")
            // previewView = null // Không còn dùng previewView
            cameraGLSurfaceView = null // Giải phóng tham chiếu đến GL view
            cameraProvider = null
            camera = null
            preview = null
            imageCapture = null
            isCaptureInProgress = false
            
            Log.d(TAG, "dispose(): Disabling orientationEventListener.")
            orientationEventListener?.disable()
            orientationEventListener = null
            Log.i(TAG, "CameraManager disposed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during CameraManager disposal", e)
        }
    }

    private fun updateUseCaseRotations(rotation: Int) {
        // Update targetRotation for both Preview and ImageCapture
        preview?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    private fun effectToString(effect: Int): String {
        return when (effect) {
            CameraMetadata.CONTROL_EFFECT_MODE_OFF -> "OFF ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_MONO -> "MONO ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE -> "NEGATIVE ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE -> "SOLARIZE ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_SEPIA -> "SEPIA ($effect)" // Mặc dù không dùng cho preview trực tiếp
            CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE -> "POSTERIZE ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD -> "WHITEBOARD ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD -> "BLACKBOARD ($effect)"
            CameraMetadata.CONTROL_EFFECT_MODE_AQUA -> "AQUA ($effect)"
            else -> "UNKNOWN ($effect)"
        }
    }
}
