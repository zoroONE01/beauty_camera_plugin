package com.example.beauty_camera_plugin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix // Added for rotation
import android.graphics.Rect
import android.graphics.YuvImage
import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
// import androidx.camera.view.PreviewView // No longer directly managing PreviewView here
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.beauty_camera_plugin.FilteredTextureView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val activity: Activity) {
    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var filteredTextureView: FilteredTextureView? = null // Changed from previewView
    
    // Camera state
    private var currentFilterType = "none"
    private var currentFilterIntensity = 1.0f
    private var isBackCamera = true
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var currentTargetRotation: Int? = null // Store current target rotation
    
    // Threading
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Orientation handler
    val orientationStreamHandler = OrientationStreamHandler(activity)

    fun initialize(callback: (Boolean, String?) -> Unit) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    
                    // If filteredTextureView is already set (via setFilteredTextureView), start the camera
                    if (filteredTextureView != null) {
                        startCamera()
                    }
                    callback(true, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Camera provider initialization failed", e)
                    callback(false, "Failed to initialize camera: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(activity))
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed", e)
            callback(false, "Failed to initialize camera: ${e.message}")
        }
    }

    // New method to accept FilteredTextureView
    fun setFilteredTextureView(view: FilteredTextureView) {
        this.filteredTextureView = view
        // Update its orientation immediately if we already have a target rotation
        // currentTargetRotation?.let { surfaceRotation -> // REMOVED - FTV no longer needs display orientation
        //     val degrees = degreesFromSurfaceRotation(surfaceRotation)
        //     view.updateDisplayOrientation(degrees)
        // }
        // Only start camera if cameraProvider is already initialized
        if (cameraProvider != null) {
            startCamera()
        }
    }

    private fun startCamera() {
        val lifecycleOwner = activity as? LifecycleOwner ?: run {
            Log.e(TAG, "Activity ($activity) is not a LifecycleOwner. Cannot start camera.")
            return
        }

        val provider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider is null. Cannot start camera.")
            return
        }

        // We still need a Preview use case for CameraX to function correctly with ImageAnalysis,
        // but we don't necessarily need to display its output if FilteredTextureView handles it.
        // However, startCamera might be called before filteredTextureView is set if initialization order changes.
        // For now, let's assume filteredTextureView will be set before or around the time startCamera is needed.
        // If filteredTextureView is null here, it might mean an issue with initialization order.
        if (this.filteredTextureView == null) {
            Log.w(TAG, "startCamera called but filteredTextureView is null. Camera might not display correctly yet.")
            // Depending on the flow, we might want to return or proceed cautiously.
            // For now, proceed, as Preview use case itself doesn't strictly need a visible view.
        }

        try {
            // Unbind all use cases
            provider.unbindAll()

            // Build camera selector
            val cameraSelector = if (isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            // Build preview use case.
            // Even if not displayed directly, CameraX might require it.
            preview = createPreviewUseCase()
            // preview?.setSurfaceProvider(currentPreviewView.surfaceProvider) // No longer setting to a PreviewView

            // Build image capture use case
            imageCapture = createImageCaptureUseCase()

            // Build image analysis use case
            // Ensure currentTargetRotation is non-null, fallback to display rotation if necessary
            val rotationForAnalysis = currentTargetRotation ?: activity.display?.rotation ?: android.view.Surface.ROTATION_0
            imageAnalysis = createImageAnalysisUseCase(rotationForAnalysis)

            // Bind use cases to lifecycle
            val useCasesToBind = mutableListOf(preview, imageCapture, imageAnalysis)
                .filterNotNull() // Filter out null use cases, just in case

            if (useCasesToBind.size < 2) { // Need at least preview and one other (capture or analysis)
                return
            }
            
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCasesToBind.toTypedArray() // Spread operator for varargs
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera or bind use cases", e)
            // Reset state on failure
            camera = null
            preview = null
            imageCapture = null
            imageAnalysis = null
        }
    }

    private fun createPreviewUseCase(): Preview {
        // Use a default rotation or the activity's display rotation if filteredTextureView is not yet available
        val displayRotation = activity.display?.rotation ?: android.view.Surface.ROTATION_0
        val rotationToApply = currentTargetRotation ?: displayRotation
        return Preview.Builder()
            .apply {
                setTargetRotation(rotationToApply)
                if (currentFilterType != "none") {
                    // Apply camera2 specific options for live preview filter
                    val camera2Interop = Camera2Interop.Extender(this)
                    applyEffectToPreview(camera2Interop, currentFilterType)
                }
            }
            .build()
    }

    private fun createImageCaptureUseCase(): ImageCapture {
        val displayRotation = activity.display?.rotation ?: android.view.Surface.ROTATION_0
        val rotationToApply = currentTargetRotation ?: displayRotation
        return ImageCapture.Builder()
            .apply {
                setTargetRotation(rotationToApply)
                setFlashMode(flashMode)
                setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            }
            .build()
    }

        // Modified to accept rotation as a parameter
        private fun createImageAnalysisUseCase(targetRotationForUseCase: Int): ImageAnalysis {
            Log.d(TAG, "createImageAnalysisUseCase: Setting targetRotation to: $targetRotationForUseCase (Surface.ROTATION_*)")
            return ImageAnalysis.Builder()
                .setTargetRotation(targetRotationForUseCase) // Quan trọng!
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    // Pass the targetRotationForUseCase to the BitmapProcessor
                    it.setAnalyzer(cameraExecutor, BitmapProcessor(targetRotationForUseCase))
                }
        }

    // Utility to convert YUV_420_888 ImageProxy to Bitmap
    // This is a simplified version. For production, consider a more robust library or method.
    private fun ImageProxy.toBitmap(): Bitmap? {
        val format = this.format
        if (format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported image format: $format. Only YUV_420_888 is supported for now.")
            return null
        }

        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize) // Swapped U and V for NV21
        uBuffer.get(nv21, ySize + vSize, uSize)


        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }


    // Modified to accept the configured Surface rotation for the ImageAnalysis use case
    private inner class BitmapProcessor(private val configuredSurfaceRotationForAnalyzer: Int) : ImageAnalysis.Analyzer {
        private var frameCounter = 0
        private val maxFramesToSave = 3 // Save 3 frames for debugging

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(image: ImageProxy) {
            val imageProxyRotationDegrees = image.imageInfo.rotationDegrees // This is from ImageProxy
            Log.d(TAG, "BitmapProcessor: analyze() called. ImageProxy rotationDegrees: $imageProxyRotationDegrees, Image W: ${image.width}, H: ${image.height}, Format: ${image.format}")

            val unrotatedBitmap = image.toBitmap()

            if (unrotatedBitmap != null) {
                Log.d(TAG, "BitmapProcessor: unrotatedBitmap created. W: ${unrotatedBitmap.width}, H: ${unrotatedBitmap.height}")
                // saveBitmapForDebug(unrotatedBitmap, "debug_unrotated_${System.currentTimeMillis()}_${imageProxyRotationDegrees}deg.jpg")

                val rotatedBitmap: Bitmap
                if (imageProxyRotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(imageProxyRotationDegrees.toFloat()) }
                    rotatedBitmap = Bitmap.createBitmap(
                        unrotatedBitmap, 0, 0, unrotatedBitmap.width, unrotatedBitmap.height, matrix, true
                    )
                    Log.d(TAG, "BitmapProcessor: rotatedBitmap created with ${imageProxyRotationDegrees}deg. W: ${rotatedBitmap.width}, H: ${rotatedBitmap.height}")
                    if (unrotatedBitmap !== rotatedBitmap && !unrotatedBitmap.isRecycled) {
                        // unrotatedBitmap.recycle(); // Keep for now if saving for debug
                    }
                } else {
                    rotatedBitmap = unrotatedBitmap // No rotation needed
                    Log.d(TAG, "BitmapProcessor: rotatedBitmap is unrotatedBitmap (no rotation needed). W: ${rotatedBitmap.width}, H: ${rotatedBitmap.height}")
                }
                // saveBitmapForDebug(rotatedBitmap, "debug_rotated_${System.currentTimeMillis()}_${imageProxyRotationDegrees}deg.jpg")


                var bitmapToProcess = rotatedBitmap
                var processedBitmap = bitmapToProcess
                var filterAppliedThisFrame = false

                if (currentFilterType != "none") {
                    // Only apply custom filters here. Camera2 native effects are on Preview use case.
                    // Apply custom filters here. Camera2 native effects are on Preview use case.
                    // We will now apply mono and negative here as well for consistency if FilteredTextureView is used.
                    filterAppliedThisFrame = true
                    processedBitmap = when (currentFilterType.lowercase()) {
                        "sepia" -> FilterProcessor.applySepia(bitmapToProcess, currentFilterIntensity)
                        "vintage" -> FilterProcessor.applyVintage(bitmapToProcess, currentFilterIntensity)
                        "cool" -> FilterProcessor.applyCool(bitmapToProcess, currentFilterIntensity)
                        "warm" -> FilterProcessor.applyWarm(bitmapToProcess, currentFilterIntensity)
                        "blur" -> FilterProcessor.applyBlur(bitmapToProcess, currentFilterIntensity)
                        "sharpen" -> FilterProcessor.applySharpen(bitmapToProcess, currentFilterIntensity)
                        "edge" -> FilterProcessor.applyEdge(bitmapToProcess, currentFilterIntensity)
                        "vignette" -> FilterProcessor.applyVignette(bitmapToProcess, currentFilterIntensity)
                        "contrast" -> FilterProcessor.applyContrast(bitmapToProcess, currentFilterIntensity)
                        "brightness" -> FilterProcessor.applyBrightness(bitmapToProcess, currentFilterIntensity)
                        "mono", "grayscale" -> FilterProcessor.applyMono(bitmapToProcess, currentFilterIntensity) // Added mono/grayscale
                        "negative" -> FilterProcessor.applyNegative(bitmapToProcess, currentFilterIntensity) // Added negative
                        else -> {
                            // If filterType is "none" or a Camera2 native effect like "solarize", "posterize"
                            // that we don't handle with FilterProcessor for ImageAnalysis.
                            if (currentFilterType !in listOf("none", "solarize", "posterize")) {
                                Log.w(TAG, "BitmapProcessor: Unhandled custom filter type '$currentFilterType' in when. Using original.")
                            }
                            filterAppliedThisFrame = false
                            bitmapToProcess
                        }
                    }
                }

                if (filterAppliedThisFrame) {
                    if (frameCounter < maxFramesToSave) {
                        saveBitmapForDebug(processedBitmap, "filtered_frame_${currentFilterType}_${frameCounter}_${System.currentTimeMillis()}.jpg")
                        frameCounter++
                    }
                } else if (currentFilterType == "none" || currentFilterType in listOf("grayscale", "mono", "negative", "solarize", "posterize")) {
                    // Optionally, save a few "unfiltered" (but rotated) frames for comparison or if a native filter is active
                    // This part is for debugging the rotation itself even if no custom filter is applied
                    if (frameCounter < maxFramesToSave && currentFilterType != "none") { // Example: save if a native filter is selected
                    } else if (frameCounter < 1 && currentFilterType == "none") { // Save one "none" frame
                    }
                }


                // --- Bitmap Recycling Logic ---
                // processedBitmap is the one we saved (if any).
                // bitmapToProcess was the input to the filter.
                // rotatedBitmap was the result of rotation.

                if (processedBitmap !== bitmapToProcess && !bitmapToProcess.isRecycled) {
                    // If filter created a new bitmap, and bitmapToProcess was the input to filter, recycle bitmapToProcess.
                    // This happens if bitmapToProcess was 'rotatedBitmap' and filter made a new one.
                    bitmapToProcess.recycle()
                }

                // If processedBitmap is the one that was newly created by a filter (i.e., different from its input bitmapToProcess)
                // and we are done with it (e.g., after saving), it should be recycled.
                // If no filter was applied, processedBitmap is the same as bitmapToProcess (which is rotatedBitmap).
                if (filterAppliedThisFrame && processedBitmap !== bitmapToProcess && !processedBitmap.isRecycled) {
                    // processedBitmap.recycle(); // Temporarily disable for checking saved files
                } else if (!filterAppliedThisFrame && processedBitmap === rotatedBitmap && !processedBitmap.isRecycled) {
                    // If no custom filter applied, processedBitmap is just rotatedBitmap.
                    // If we didn't save it or do anything else with it, recycle it.
                    // processedBitmap.recycle(); // Temporarily disable
                }

                // Update the FilteredTextureView with the processed (or just rotated) bitmap
                filteredTextureView?.updateBitmap(processedBitmap)
                // Note: The ownership and recycling of 'processedBitmap' needs careful handling.
                // If updateBitmap takes ownership (e.g. makes a copy or recycles after drawing),
                // then we don't recycle here. If not, and it's a new bitmap, it should be recycled
                // after updateBitmap is done with it. For now, FilteredTextureView's logic is
                // designed to handle the bitmap passed to it. We've also disabled aggressive recycling
                // in FilteredTextureView for now.

            } else {
                Log.e(TAG, "ImageAnalysis: Failed to convert ImageProxy to Bitmap.")
            }
            image.close()
        }
    }

    private fun saveBitmapForDebug(bitmap: Bitmap, filename: String) {
        val FOLDER_NAME = "filtered_frames_debug"
        val mediaDir = activity.getExternalFilesDir(null)?.let {
            File(it, FOLDER_NAME).apply { mkdirs() }
        }

        if (mediaDir == null || !mediaDir.exists()) {
            Log.e(TAG, "Failed to create directory for debug frames.")
            return
        }

        val file = File(mediaDir, filename)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug frame", e)
        }
    }

    private fun applyEffectToPreview(
        interop: Camera2Interop.Extender<*>,
        filterType: String
    ) {
        when (filterType.lowercase()) {
            "sepia" -> {
                // Sepia is applied in post-processing via FilterProcessor.
                // For preview, set to OFF or a similar basic effect if desired.
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_OFF for sepia preview (post-processing only)")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
            "grayscale", "mono" -> {
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_MONO for $filterType preview")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_MONO
                )
            }
            "negative" -> {
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_NEGATIVE for $filterType preview")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE
                )
            }
            "solarize" -> {
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_SOLARIZE for $filterType preview")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE
                )
            }
            "posterize" -> {
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_POSTERIZE for $filterType preview")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE
                )
            }
            // Explicitly handle other custom filters that are post-processing only
            "vintage", "cool", "warm", "blur", "sharpen", "edge", "vignette", "contrast", "brightness" -> {
                 Log.d(TAG, "Applying CONTROL_EFFECT_MODE_OFF for $filterType preview (post-processing only)")
                 interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
            "none" -> {
                Log.d(TAG, "Applying CONTROL_EFFECT_MODE_OFF for 'none' filter preview")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
            else -> {
                // This case handles any other filterType not explicitly listed,
                // including potentially new ones or typos.
                Log.w(TAG, "Unknown filterType: '$filterType'. Applying CONTROL_EFFECT_MODE_OFF as default.")
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_EFFECT_MODE,
                    CameraMetadata.CONTROL_EFFECT_MODE_OFF
                )
            }
        }
    }

    fun setFilter(filterType: String) {
        Log.d(TAG, "setFilter called with filterType: $filterType")
        this.currentFilterType = filterType
        
        // Check if camera is properly initialized before applying filter
        if (cameraProvider == null) {
            Log.e(TAG, "CameraProvider is null, cannot apply filter")
            return
        }
        
        if (filteredTextureView == null) {
            Log.e(TAG, "FilteredTextureView is null, cannot apply filter. Camera might not be fully set up.")
            // Depending on the desired behavior, you might queue the filter change
            // or simply log and wait for the view to be set.
            // For now, we'll proceed, and applyFilterToCamera will rebind,
            // which should pick up the new filter for ImageAnalysis.
        }
        
        applyFilterToCamera()
    }

    fun setFilterIntensity(intensity: Float) {
        this.currentFilterIntensity = intensity.coerceIn(0.0f, 1.0f)
        // For filters that support intensity, reapply them
        if (currentFilterType in listOf("vignette", "contrast", "brightness")) {
            applyFilterToCamera()
        }
    }

    private fun applyFilterToCamera() {
        val lifecycleOwner = activity as? LifecycleOwner ?: run {
            Log.e(TAG, "Activity is not a LifecycleOwner")
            return
        }
        val provider = cameraProvider ?: run {
            Log.e(TAG, "CameraProvider is null")
            return
        }
        
        // Store current use cases before unbinding to restore if needed
        val currentPreview = preview
        val currentImageCapture = imageCapture
        val currentImageAnalysis = imageAnalysis // Store current imageAnalysis
        
        try {
            // Unbind existing use cases
            provider.unbindAll()
            
            // Create new preview with filter applied (for Camera2 effects)
            preview = createPreviewUseCase()
            
            // Create new imageCapture use case
            imageCapture = createImageCaptureUseCase()

            // Create new imageAnalysis use case (its analyzer will pick up currentFilterType)
            // Pass the current device orientation to it
            val rotationForAnalysisOnFilterChange = currentTargetRotation ?: activity.display?.rotation ?: android.view.Surface.ROTATION_0
            Log.e(TAG, "applyFilterToCamera: Passing rotation $rotationForAnalysisOnFilterChange to createImageAnalysisUseCase.")
            imageAnalysis = createImageAnalysisUseCase(rotationForAnalysisOnFilterChange)
            
            // Set surface provider for preview - NO LONGER DOING THIS FOR A VISIBLE PREVIEWVIEW
            // previewView?.let { preview?.setSurfaceProvider(it.surfaceProvider) }
            // The Preview use case will still exist and provide frames if bound,
            // but its output isn't directly tied to a visible PreviewView here.
            // ImageAnalysis is our source for the FilteredTextureView.
            
            // Rebind all use cases
            val cameraSelector = if (isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            
            val useCasesToBind = mutableListOf(preview, imageCapture, imageAnalysis)
                .filterNotNull()
            
            if (useCasesToBind.size < 2) {
                 Log.e(TAG, "Not enough use cases to rebind during filter application.")
                 // Attempt to restore previous state
                 preview = currentPreview
                 imageCapture = currentImageCapture
                 imageAnalysis = currentImageAnalysis
                 // Rebind previous if possible (simplified restoration)
                 if (currentPreview != null) { // At least bind preview
                    val prevUseCases = mutableListOf(currentPreview, currentImageCapture, currentImageAnalysis).filterNotNull()
                    if (prevUseCases.isNotEmpty()) {
                        camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, *prevUseCases.toTypedArray())
                    }
                 }
                 return
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCasesToBind.toTypedArray()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed during filter application", e)
            
            // Restore previous use cases if binding failed
            try {
                preview = currentPreview
                imageCapture = currentImageCapture
                imageAnalysis = currentImageAnalysis // Restore imageAnalysis
                
                val prevUseCasesToRestore = mutableListOf(preview, imageCapture, imageAnalysis).filterNotNull()

                if (prevUseCasesToRestore.isNotEmpty()) {
                     val cameraSelector = if (isBackCamera) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *prevUseCasesToRestore.toTypedArray()
                    )
                }
            } catch (restoreException: Exception) {
                Log.e(TAG, "Failed to restore previous camera state", restoreException)
            }
        }
    }

    fun takePicture(callback: (String?, String?) -> Unit) {
        val captureInstance = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null - camera not properly initialized")
            callback(null, "Camera not initialized")
            return
        }

        // Ensure flash mode is correctly set on the capture instance
        // This might be slightly redundant if toggleFlash and startCamera handle it perfectly,
        // but acts as a safeguard.
        captureInstance.flashMode = this.flashMode // Directly set the flash mode on the existing instance
        Log.d(TAG, "takePicture: Set flashMode on ImageCapture instance to: ${getFlashModeString()}")


        // Create output file
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val photoFile = File(
            activity.getExternalFilesDir(null),
            "$name.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        captureInstance.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    val filePath = savedUri.path

                    if (currentFilterType != "none" && currentFilterType != "auto") {
                        // Apply filter to the captured image if it's not done in real-time
                        scope.launch(Dispatchers.IO) {
                            try {
                                // applyFilterToSavedImage now handles bitmap loading, filtering, and saving
                                applyFilterToSavedImage(photoFile.absolutePath, currentFilterType, currentFilterIntensity)
                                launch(Dispatchers.Main) {
                                    callback(photoFile.absolutePath, null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error applying filter to saved image", e)
                                launch(Dispatchers.Main) {
                                    callback(null, "Failed to apply filter: ${e.message}")
                                }
                            }
                        }
                    } else {
                        // No post-processing needed
                        callback(filePath, null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed. Code: ${exception.imageCaptureError}, Message: ${exception.message}", exception)
                    callback(null, "Photo capture failed: ${exception.message} (Code: ${exception.imageCaptureError})")
                }
            }
        )
    }

    private fun applyFilterToSavedImage(imagePath: String, filterType: String, intensity: Float) {
        val originalBitmap = BitmapFactory.decodeFile(imagePath)
        if (originalBitmap == null) {
            Log.e(TAG, "Failed to decode image file for filtering: $imagePath")
            return
        }

        val filteredBitmap: Bitmap = when (filterType.lowercase()) {
            "sepia" -> FilterProcessor.applySepia(originalBitmap, intensity)
            "vintage" -> FilterProcessor.applyVintage(originalBitmap, intensity)
            "cool" -> FilterProcessor.applyCool(originalBitmap, intensity)
            "warm" -> FilterProcessor.applyWarm(originalBitmap, intensity)
            "blur" -> FilterProcessor.applyBlur(originalBitmap, intensity)
            "sharpen" -> FilterProcessor.applySharpen(originalBitmap, intensity)
            "edge" -> FilterProcessor.applyEdge(originalBitmap, intensity)
            "vignette" -> FilterProcessor.applyVignette(originalBitmap, intensity)
            "contrast" -> FilterProcessor.applyContrast(originalBitmap, intensity)
            "brightness" -> FilterProcessor.applyBrightness(originalBitmap, intensity)
            "mono", "grayscale" -> FilterProcessor.applyMono(originalBitmap, intensity) // Added mono/grayscale
            "negative" -> FilterProcessor.applyNegative(originalBitmap, intensity) // Added negative
            else -> originalBitmap // If filter type is unknown or "none", return original
        }

        try {
            FileOutputStream(imagePath).use { out ->
                filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Filtered image saved to $imagePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save filtered bitmap to $imagePath", e)
        } finally {
            // Recycle bitmaps if they are different and no longer needed
            if (originalBitmap !== filteredBitmap && !filteredBitmap.isRecycled) {
                filteredBitmap.recycle()
            }
            // Original bitmap was loaded here, so it should be recycled if not used by filteredBitmap
            if (!originalBitmap.isRecycled) {
                 // originalBitmap.recycle() // Be cautious if FilterProcessor might reuse it.
                                         // Assuming FilterProcessor returns a new bitmap or the same one.
                                         // If FilterProcessor always returns new, original can be recycled.
            }
        }
    }

    fun switchCamera() {
        isBackCamera = !isBackCamera
        startCamera()
    }

    fun toggleFlash(): String {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_OFF
        }
        Log.d(TAG, "Toggled flashMode to: ${getFlashModeString()}") // Added log

        // Update the image capture use case with new flash mode
        // This creates a new instance configured with the new flashMode
        imageCapture = createImageCaptureUseCase() 
        
        val lifecycleOwner = activity as? LifecycleOwner ?: return getFlashModeString()
        val provider = cameraProvider ?: run { // Added check for provider
            Log.e(TAG, "toggleFlash: CameraProvider is null, cannot rebind.")
            return getFlashModeString()
        }

        try {
            val cameraSelector = if (isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            
            Log.d(TAG, "Rebinding use cases for flash mode change.") // Added log
            provider.unbindAll()
            val useCasesToBind = mutableListOf(preview, imageCapture, imageAnalysis).filterNotNull()
            if (useCasesToBind.isNotEmpty()) {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCasesToBind.toTypedArray()
                )
                Log.d(TAG, "Use cases rebound successfully after flash mode change.") // Added log
            } else {
                Log.e(TAG, "No valid use cases to bind after flash mode toggle.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rebind use cases for flash mode change", e)
        }
        
        return getFlashModeString()
    }

    private fun getFlashModeString(): String {
        return when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "on"
            ImageCapture.FLASH_MODE_AUTO -> "auto"
            else -> "off" // Covers FLASH_MODE_OFF and any other unexpected values
        }
    }

    fun setZoom(zoom: Float) {
        val zoomLevel = zoom.coerceIn(0.0f, 1.0f)
        camera?.cameraControl?.setLinearZoom(zoomLevel)
    }

    fun updateTargetRotation(rotation: Int) { // rotation is Surface.ROTATION_*
        Log.e(TAG, "updateTargetRotation CALLED. New SurfaceRotation: $rotation, Old: ${currentTargetRotation ?: "null"}")
        if (currentTargetRotation == rotation && camera != null) {
            Log.d(TAG, "updateTargetRotation: Rotation unchanged ($rotation). No action needed.") // Changed Log.e to Log.d
            // filteredTextureView?.let { // REMOVED - FTV no longer needs display orientation
            //     val degrees = degreesFromSurfaceRotation(rotation)
            //     it.updateDisplayOrientation(degrees)
            // }
            return
        }
        
        this.currentTargetRotation = rotation
        Log.d(TAG, "updateTargetRotation: currentTargetRotation (Surface rotation) updated to: $currentTargetRotation") // Changed Log.e to Log.d

        // Revert to calling startCamera() to ensure ImageAnalysis use case is reconfigured
        // with the new targetRotation, which affects image.imageInfo.rotationDegrees.
        if (cameraProvider != null && activity != null) {
            Log.d(TAG, "updateTargetRotation: Calling startCamera() due to rotation change.") // Changed Log.e to Log.d
            startCamera() // This will rebind use cases with the new currentTargetRotation
            Log.d(TAG, "updateTargetRotation: startCamera() CALL COMPLETE.") // Changed Log.e to Log.d
        } else {
            Log.e(TAG, "updateTargetRotation: CANNOT call startCamera(). cameraProvider: $cameraProvider, activity: $activity")
            // If we can't restart camera, no FTV update is needed here as it doesn't handle orientation anymore
            // filteredTextureView?.let { // REMOVED
            //     val degrees = degreesFromSurfaceRotation(rotation)
            //     it.updateDisplayOrientation(degrees)
            //     Log.e(TAG, "updateTargetRotation: Updated FTV orientation directly as camera could not be restarted.")
            // }
            return
        }
        
        // After startCamera() has (hopefully) reconfigured ImageAnalysis,
        // FTV no longer needs its display orientation updated from here.
        // filteredTextureView?.let { // REMOVED
        //     val degrees = degreesFromSurfaceRotation(rotation)
        //     it.updateDisplayOrientation(degrees)
        //     Log.e(TAG, "updateTargetRotation: Called updateDisplayOrientation on FTV with $degrees degrees AFTER startCamera().")
        // }
    }

    // Helper function to convert Surface.ROTATION_* to degrees for canvas rotation // REMOVED - No longer needed
    // private fun degreesFromSurfaceRotation(surfaceRotation: Int): Float {
    //     return when (surfaceRotation) {
    //         android.view.Surface.ROTATION_0 -> 0f
    //         android.view.Surface.ROTATION_90 -> 270f
    //         android.view.Surface.ROTATION_180 -> 180f
    //         android.view.Surface.ROTATION_270 -> 90f
    //         else -> 0f
    //     }
    // }

    fun dispose() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            scope.cancel()
            filteredTextureView = null // Clear reference to custom view
            orientationStreamHandler.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing camera", e)
        }
    }
}
