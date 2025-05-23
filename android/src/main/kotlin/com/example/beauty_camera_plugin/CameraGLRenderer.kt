package com.example.beauty_camera_plugin

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGLRenderer(
    private val context: Context,
    private val glSurfaceView: CameraGLSurfaceView
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "CameraGLRenderer"
        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3
        private const val UNIFORM_COLOR_ADJUST = "uColorAdjust"
    }

    private val triangleVerticesData = floatArrayOf(
        // X,   Y,    Z,   U,   V
        -1.0f, -1.0f, 0f,  0f,  0f,
         1.0f, -1.0f, 0f,  1f,  0f,
        -1.0f,  1.0f, 0f,  0f,  1f,
         1.0f,  1.0f, 0f,  1f,  1f,
    )
    private val triangleVertices: FloatBuffer

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16) // Texture matrix

    private var programId = 0
    private var textureId = 0
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var uColorAdjustHandle: Int = 0 // Uniform for color adjustment (e.g., for sepia)

    // Variables for YUV to RGB conversion and offscreen rendering
    private var yuvToRgbProgramId = 0
    private var yTextureUniformHandle = 0
    private var uTextureUniformHandle = 0
    private var vTextureUniformHandle = 0
    // Re-use position and textureCoord handles if vertex shader is the same
    // private var yuvPositionHandle = 0
    // private var yuvTextureCoordHandle = 0

    private var yTextureIdForCapture = 0
    private var uTextureIdForCapture = 0
    private var vTextureIdForCapture = 0

    // FBO for offscreen rendering
    private var offscreenFboId = 0
    private var offscreenTextureId = 0 // Texture to render filtered image to
    private var offscreenRenderbufferId = 0 // Optional: for depth/stencil

    var surfaceTexture: SurfaceTexture? = null
        private set
    private var cameraSurface: Surface? = null

    private var frameAvailable = false
    private val lock = Object()

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    
    // Kích thước của frame camera (sau khi đã tính đến hướng của sensor)
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0
    private var cameraSensorRotation: Int = 0 // Độ xoay của sensor (0, 90, 180, 270)
    private var displayRotation: Int = Surface.ROTATION_0 // Độ xoay của màn hình
    
    private var currentFilterType: String = "none"
    // uColorAdjustHandle đã được khai báo ở trên


    // Shader source code
    private var vertexShaderSource: String = "" // Can be reused for YUV conversion if simple
    private var fragmentShaderSourceNone: String = ""
    private var fragmentShaderSourceSepia: String = ""
    private var fragmentShaderSourceGrayscale: String = ""
    private var fragmentShaderSourceYuvToRgb: String = ""
    // Thêm các fragment shader khác ở đây
 
    init {
        triangleVertices = ByteBuffer.allocateDirect(triangleVerticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices.put(triangleVerticesData).position(0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, ">>> onSurfaceCreated START. Resetting filter state.")
        currentFilterType = "none" // Reset filter type
        loadShaderSources() // Load shader code

        // Create and use the "none" filter program initially
        programId = createProgram(vertexShaderSource, fragmentShaderSourceNone)
        if (programId == 0) {
            Log.e(TAG, "onSurfaceCreated: Could not create 'none' program.")
            // Consider throwing an exception or setting an error state
            return
        }
        GLES20.glUseProgram(programId)
        Log.i(TAG, "onSurfaceCreated: Initial 'none' program created and used. ID: $programId. currentFilterType: $currentFilterType")

        // Get handles for the initial "none" program
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        stMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
        // For "none" shader, uColorAdjustHandle might be -1 if not used, which is fine.
        // It will be updated if a filter requiring it is set.
        uColorAdjustHandle = GLES20.glGetUniformLocation(programId, UNIFORM_COLOR_ADJUST)
        Log.i(TAG, "onSurfaceCreated: Shader handles for 'none' program obtained. uColorAdjustHandle: $uColorAdjustHandle (expected -1 if 'none' shader doesn't use it or it's optimized out)")

        // Create texture for camera preview
        val previewTextures = IntArray(1)
        GLES20.glGenTextures(1, previewTextures, 0)
        textureId = previewTextures[0]
        Log.d(TAG, "onSurfaceCreated: Preview texture generated. ID: $textureId")

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        Log.d(TAG, "onSurfaceCreated: Preview texture configured.")

        // Release old SurfaceTexture and Surface if they exist
        surfaceTexture?.release()
        cameraSurface?.release()
        Log.d(TAG, "onSurfaceCreated: Released any existing SurfaceTexture/Surface.")

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture?.setOnFrameAvailableListener(this)
        cameraSurface = Surface(surfaceTexture)
        Log.d(TAG, "onSurfaceCreated: New SurfaceTexture and Surface created. SurfaceTexture: $surfaceTexture, Surface: $cameraSurface")

        // Create YUV to RGB conversion program
        yuvToRgbProgramId = createProgram(vertexShaderSource, fragmentShaderSourceYuvToRgb)
        if (yuvToRgbProgramId == 0) {
            Log.e(TAG, "Could not create YUV to RGB program.")
            // Handle error appropriately, maybe throw exception or set a flag
        } else {
            // Get handles for YUV program (assuming same vertex shader attributes)
            // yuvPositionHandle = GLES20.glGetAttribLocation(yuvToRgbProgramId, "aPosition")
            // yuvTextureCoordHandle = GLES20.glGetAttribLocation(yuvToRgbProgramId, "aTextureCoord")
            yTextureUniformHandle = GLES20.glGetUniformLocation(yuvToRgbProgramId, "sTextureY")
            uTextureUniformHandle = GLES20.glGetUniformLocation(yuvToRgbProgramId, "sTextureU")
            vTextureUniformHandle = GLES20.glGetUniformLocation(yuvToRgbProgramId, "sTextureV")
            Log.d(TAG, "YUV to RGB Program Created. Y:$yTextureUniformHandle U:$uTextureUniformHandle V:$vTextureUniformHandle")
        }

        // Create textures for Y, U, V planes
        val yuvCaptureTextures = IntArray(3)
        GLES20.glGenTextures(3, yuvCaptureTextures, 0)
        yTextureIdForCapture = yuvCaptureTextures[0]
        uTextureIdForCapture = yuvCaptureTextures[1]
        vTextureIdForCapture = yuvCaptureTextures[2]

        configureTexture(yTextureIdForCapture)
        configureTexture(uTextureIdForCapture)
        configureTexture(vTextureIdForCapture)
        
        // FBO and offscreen texture will be created on demand when capturing

        synchronized(lock) {
            frameAvailable = false
        }
        Log.i(TAG, "<<< onSurfaceCreated END. SurfaceTexture: $surfaceTexture, Preview Texture ID: $textureId")
    }

    private fun configureTexture(textureId: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // Unbind
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged. View Width: $width, View Height: $height")
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        updateMvpMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(lock) {
            if (frameAvailable) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                frameAvailable = false
            }
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(programId) // Quan trọng: đảm bảo đúng program đang được sử dụng

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            textureCoordHandle, 2, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(textureCoordHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
        
        // Set uniform for filter
        var colorAdjustValue = 0.0f // Log giá trị này
        if (currentFilterType == "sepia") {
            colorAdjustValue = 1.0f
            GLES20.glUniform1f(uColorAdjustHandle, colorAdjustValue)
        } else if (currentFilterType == "grayscale" || currentFilterType == "mono") {
            colorAdjustValue = 2.0f
            GLES20.glUniform1f(uColorAdjustHandle, colorAdjustValue)
        }
        else { // "none" hoặc các filter khác chưa có uniform riêng
            colorAdjustValue = 0.0f
            GLES20.glUniform1f(uColorAdjustHandle, colorAdjustValue)
        }
        
        if (frameCounter % 60 == 0) { // Log mỗi 60 frames để tránh spam
            Log.i(TAG, "onDrawFrame (frame $frameCounter) - currentFilterType: '$currentFilterType', uColorAdjustHandle: $uColorAdjustHandle, appliedValue: $colorAdjustValue, programId: $programId")
        }


        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
        frameCounter++
    }

    override fun onFrameAvailable(st: SurfaceTexture?) { // Renamed parameter for clarity
        // Log.d(TAG, "onFrameAvailable called for SurfaceTexture: $st. Current renderer.surfaceTexture: ${this.surfaceTexture}") // Can be too spammy
        synchronized(lock) {
            if (st == this.surfaceTexture) { // Only process if it's for our current SurfaceTexture
                frameAvailable = true
                // Log.d(TAG, "onFrameAvailable: Frame is available for rendering.") // Can be too spammy
            } else {
                Log.w(TAG, "onFrameAvailable: Received frame for an old/unexpected SurfaceTexture: $st. Current: ${this.surfaceTexture}")
            }
        }
        glSurfaceView.requestRender()
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType:")
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            checkGlError("glAttachShader")
            GLES20.glAttachShader(program, pixelShader)
            checkGlError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            val programLog = GLES20.glGetProgramInfoLog(program) // Vẫn nên giữ lại log này khi link thất bại
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program (ID $program). Status: ${linkStatus[0]}")
                if (programLog.isNotEmpty()) {
                    Log.e(TAG, "Program link log (ID $program): $programLog")
                }
                GLES20.glDeleteProgram(program)
                program = 0
            }
            // Bỏ log active uniforms
        }
        // Sau khi link, shader object không còn cần thiết
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(pixelShader)
        return program
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }
    
    fun setCameraParameters(previewWidth: Int, previewHeight: Int, sensorRot: Int, dispRot: Int) {
        Log.d(TAG, "setCameraParameters. Preview: ${previewWidth}x${previewHeight}, SensorRot: $sensorRot, DisplayRot: $dispRot")
        textureWidth = previewWidth
        textureHeight = previewHeight
        cameraSensorRotation = sensorRot
        displayRotation = dispRot // Lưu display rotation từ CameraManager
        updateMvpMatrix()
    }

    private fun updateMvpMatrix() {
        if (viewWidth == 0 || viewHeight == 0 || textureWidth == 0 || textureHeight == 0) {
            Matrix.setIdentityM(mvpMatrix, 0)
            return
        }

        val projectionMatrix = FloatArray(16)
        // ModelViewMatrix = ViewMatrix * ModelMatrix
        // Trong trường hợp này, ModelMatrix có thể chứa phép xoay và scale cho quad.
        // ViewMatrix có thể coi là identity nếu camera OpenGL nhìn thẳng.
        val modelViewMatrix = FloatArray(16)

        Matrix.setIdentityM(modelViewMatrix, 0)

        // 1. Tính toán độ xoay cần thiết cho quad
        // CameraX Preview use case với setTargetRotation(displayRotation)
        // sẽ cố gắng cung cấp các frame buffer đã được xoay để chúng "upright"
        // đối với displayRotation đó. stMatrix từ SurfaceTexture sẽ chứa các phép biến đổi
        // cần thiết để map texture này lên quad một cách chính xác.
        //
        // Nhiệm vụ chính của chúng ta là scale quad cho đúng tỷ lệ khung hình.

        // Tính toán kích thước texture hiệu dụng dựa trên độ xoay của sensor
        val effectiveTextureWidth: Int
        val effectiveTextureHeight: Int
        if (cameraSensorRotation == 90 || cameraSensorRotation == 270) {
            effectiveTextureWidth = textureHeight // Hoán đổi khi xoay ngang
            effectiveTextureHeight = textureWidth
        } else {
            effectiveTextureWidth = textureWidth
            effectiveTextureHeight = textureHeight
        }

        if (effectiveTextureWidth == 0 || effectiveTextureHeight == 0) {
            Log.w(TAG, "updateMvpMatrix: Effective texture dimensions are zero, cannot calculate aspect ratio.")
            Matrix.setIdentityM(mvpMatrix, 0) // Or handle error appropriately
            return
        }

        val texAspect = effectiveTextureWidth.toFloat() / effectiveTextureHeight.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

        Log.d(TAG, "updateMvpMatrix: viewWidth=$viewWidth, viewHeight=$viewHeight, viewAspect=$viewAspect")
        Log.d(TAG, "updateMvpMatrix: rawTextureWidth=$textureWidth, rawTextureHeight=$textureHeight, sensorRotation=$cameraSensorRotation")
        Log.d(TAG, "updateMvpMatrix: effectiveTextureWidth=$effectiveTextureWidth, effectiveTextureHeight=$effectiveTextureHeight, effectiveTexAspect=$texAspect")

        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        var scaleX = 1.0f
        var scaleY = 1.0f

        if (viewAspect > texAspect) {
            // View rộng hơn texture -> Cần scale Y để cover (texture sẽ bị crop theo chiều X)
            scaleY = viewAspect / texAspect
            Log.d(TAG, "updateMvpMatrix: Scaling Y by $scaleY to COVER (viewAspect > texAspect)")
        } else {
            // View cao hơn texture (hoặc bằng) -> Cần scale X để cover (texture sẽ bị crop theo chiều Y)
            scaleX = texAspect / viewAspect
            Log.d(TAG, "updateMvpMatrix: Scaling X by $scaleX to COVER (viewAspect <= texAspect)")
        }
        Matrix.scaleM(scaleMatrix, 0, scaleX, scaleY, 1f)
        Log.d(TAG, "updateMvpMatrix: final scaleX=$scaleX, scaleY=$scaleY for COVER")
        
        // Áp dụng scale vào modelViewMatrix
        // ModelViewMatrix ban đầu là identity, nên phép nhân này sẽ đặt scaleMatrix vào modelViewMatrix
        Matrix.multiplyMM(modelViewMatrix, 0, modelViewMatrix, 0, scaleMatrix, 0)


        // 2. Thiết lập ma trận chiếu (Projection)
        // Sử dụng phép chiếu trực giao đơn giản vì chúng ta đang vẽ một quad 2D.
        // Quad của chúng ta có tọa độ từ -1 đến 1.
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        // 3. Tính toán MVP Matrix cuối cùng
        // MVP = Projection * ModelView (Vì ViewMatrix trong ModelView là identity)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)
        
        // Log.d(TAG, "Updated MVP Matrix. View: ${viewWidth}x${viewHeight} (aspect $viewAspect), Texture: ${textureWidth}x${textureHeight} (aspect $texAspect)")
        // Log.d(TAG, "MVP: ${mvpMatrix.joinToString()}")
        // Log.d(TAG, "ST: ${stMatrix.joinToString()}") // stMatrix được cập nhật trong onDrawFrame bởi SurfaceTexture

        glSurfaceView.requestRender()
    }

    fun getCameraSurface(): Surface? {
        Log.i(TAG, "getCameraSurface() called. Current surfaceTexture: $surfaceTexture, Returning: $cameraSurface")
        return cameraSurface
    }

    fun onPause() {
        Log.i(TAG, ">>> onPause START. Current surfaceTexture before release: $surfaceTexture")
        // Release resources
        surfaceTexture?.release()
        Log.i(TAG, "onPause: SurfaceTexture released (was $surfaceTexture before set to null)")
        surfaceTexture = null // Set to null after release

        cameraSurface?.release()
        Log.d(TAG, "onPause: CameraSurface released: $cameraSurface")
        cameraSurface = null // Set to null after release

        if (programId != 0) {
            Log.d(TAG, "onPause: Deleting program ID: $programId")
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        if (yuvToRgbProgramId != 0) {
            Log.d(TAG, "onPause: Deleting YUV program ID: $yuvToRgbProgramId")
            GLES20.glDeleteProgram(yuvToRgbProgramId)
            yuvToRgbProgramId = 0
        }
        val texturesToDelete = mutableListOf<Int>()
        if (textureId != 0) texturesToDelete.add(textureId)
        if (yTextureIdForCapture != 0) texturesToDelete.add(yTextureIdForCapture)
        if (uTextureIdForCapture != 0) texturesToDelete.add(uTextureIdForCapture)
        if (vTextureIdForCapture != 0) texturesToDelete.add(vTextureIdForCapture)
        if (offscreenTextureId != 0) texturesToDelete.add(offscreenTextureId)

        if (texturesToDelete.isNotEmpty()) {
            Log.d(TAG, "onPause: Deleting textures: ${texturesToDelete.joinToString()}")
            GLES20.glDeleteTextures(texturesToDelete.size, texturesToDelete.toIntArray(), 0)
            textureId = 0 // Reset main texture ID
        }

        if (offscreenFboId != 0) {
            Log.d(TAG, "onPause: Deleting FBO ID: $offscreenFboId")
            GLES20.glDeleteFramebuffers(1, intArrayOf(offscreenFboId), 0)
            offscreenFboId = 0
        }
        if (offscreenRenderbufferId != 0) {
            Log.d(TAG, "onPause: Deleting Renderbuffer ID: $offscreenRenderbufferId")
            GLES20.glDeleteRenderbuffers(1, intArrayOf(offscreenRenderbufferId), 0)
            offscreenRenderbufferId = 0
        }
        Log.i(TAG, "<<< onPause END. GL resources released. Current SurfaceTexture (should be null): $surfaceTexture")
    }

    fun onResume() {
        // onSurfaceCreated will be called by GLSurfaceView if the context was lost.
        // If context was not lost, existing resources might still be valid,
        // but it's generally safer to rely on onSurfaceCreated to re-initialize.
        Log.i(TAG, ">>> onResume START. Current surfaceTexture: $surfaceTexture. Expecting onSurfaceCreated if context was lost.")
    }

    fun setFilter(filterType: String) {
        Log.i(TAG, "Renderer setFilter called with input: '$filterType'. Current programId: $programId, currentFilter: '$currentFilterType'. This is on GL Thread: ${Thread.currentThread().name.startsWith("GLThread")}")
        val newFilterType = filterType.lowercase()
        
        // Chỉ tạo lại program nếu filter thực sự thay đổi VÀ shader tương ứng khác nhau
        var needsNewProgram = false
        val newFragmentShaderSourceSelected: String
        val oldFilterType = currentFilterType // Lưu lại filter cũ để so sánh
        
        when(newFilterType) {
            "sepia" -> {
                newFragmentShaderSourceSelected = fragmentShaderSourceSepia
                if (oldFilterType != "sepia") needsNewProgram = true
            }
            "grayscale", "mono" -> {
                newFragmentShaderSourceSelected = fragmentShaderSourceGrayscale
                if (oldFilterType != "grayscale" && oldFilterType != "mono") needsNewProgram = true
            }
            else -> { // "none"
                newFragmentShaderSourceSelected = fragmentShaderSourceNone
                // Chỉ cần tạo program mới nếu filter trước đó không phải là "none" (hoặc rỗng lúc khởi tạo)
                if (oldFilterType != "none" && oldFilterType.isNotEmpty()) needsNewProgram = true
            }
        }

        currentFilterType = newFilterType // Cập nhật currentFilterType
        Log.i(TAG, "setFilter: currentFilterType is NOW '$currentFilterType'. needsNewProgram: $needsNewProgram")

        if (needsNewProgram) {
            Log.i(TAG, "setFilter: Filter changed from '$oldFilterType' to '$newFilterType'. Recreating program.")
            val newProgramId = createProgram(vertexShaderSource, newFragmentShaderSourceSelected)
            if (newProgramId != 0) {
                if (programId != 0) {
                    GLES20.glDeleteProgram(programId)
                }
                programId = newProgramId
                GLES20.glUseProgram(programId) // Quan trọng: sử dụng program mới

                // Lấy lại các handle cho program mới
                positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
                textureCoordHandle = GLES20.glGetAttribLocation(programId, "aTextureCoord")
                mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
                stMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
                uColorAdjustHandle = GLES20.glGetUniformLocation(programId, UNIFORM_COLOR_ADJUST)
                Log.i(TAG, "Switched to filter: '$currentFilterType', new program ID: $programId, new uColorAdjustHandle: $uColorAdjustHandle")
            } else {
                Log.e(TAG, "Failed to create program for filter: '$currentFilterType'. Reverting to old programId: $programId and old filter: '$oldFilterType'")
                currentFilterType = oldFilterType // Khôi phục filter cũ nếu tạo program mới thất bại
                // Không cần làm gì với programId vì nó vẫn là program cũ
            }
        } else {
             Log.i(TAG, "setFilter: Filter type '$newFilterType' did not require new program or shader source is the same. Current programId: $programId, uColorAdjustHandle: $uColorAdjustHandle")
             // Đảm bảo uColorAdjustHandle vẫn hợp lệ nếu program không thay đổi và nó đã từng không hợp lệ
             if (programId != 0 && GLES20.glGetUniformLocation(programId, UNIFORM_COLOR_ADJUST) == -1 && uColorAdjustHandle == -1) {
                 uColorAdjustHandle = GLES20.glGetUniformLocation(programId, UNIFORM_COLOR_ADJUST)
                 Log.i(TAG, "setFilter: Re-fetched uColorAdjustHandle: $uColorAdjustHandle for programId: $programId because it was invalid.")
             } else if (programId != 0 && uColorAdjustHandle == -1 && newFilterType != "none") {
                 // Trường hợp đặc biệt: chuyển từ "none" (có thể có handle -1) sang filter khác mà không tạo lại program
                 // (ví dụ: từ "none" sang "grayscale" lần đầu, programId có thể chưa được cập nhật đúng)
                 // Cần đảm bảo handle được lấy lại nếu nó là -1 và filter không phải là "none"
                 uColorAdjustHandle = GLES20.glGetUniformLocation(programId, UNIFORM_COLOR_ADJUST)
                 Log.i(TAG, "setFilter: Filter is '$newFilterType', uColorAdjustHandle was -1. Re-fetched: $uColorAdjustHandle for programId: $programId")
             }
        }
        glSurfaceView.requestRender() // Yêu cầu render lại với filter mới
    }

    private var frameCounter = 0 // Để giới hạn log trong onDrawFrame

    private fun loadShaderSources() {
        // Vertex Shader: cơ bản để vẽ texture
        vertexShaderSource = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        // Fragment Shader: Không filter (chỉ vẽ texture)
        fragmentShaderSourceNone = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float $UNIFORM_COLOR_ADJUST; // Sử dụng hằng số ở đây
            void main() {
                // Để uColorAdjust không bị tối ưu hóa, chúng ta có thể thực hiện một phép toán giả
                // Hoặc đơn giản là chấp nhận rằng đối với shader "none", handle có thể là -1
                // nếu nó không được sử dụng. Tuy nhiên, để nhất quán và tránh lỗi,
                // tốt hơn là khai báo nó.
                // Nếu shader này thực sự không cần uColorAdjust, thì việc handle là -1 là bình thường.
                // Nhưng vì các shader khác có nó, việc khai báo ở đây giúp cấu trúc đồng nhất.
                gl_FragColor = texture2D(sTexture, vTextureCoord); // + vec4($UNIFORM_COLOR_ADJUST * 0.0, 0.0, 0.0, 0.0); // Phép toán giả nếu cần
            }
        """.trimIndent()

        // Fragment Shader: Sepia
        fragmentShaderSourceSepia = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float $UNIFORM_COLOR_ADJUST; // Sử dụng hằng số ở đây
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                if (abs($UNIFORM_COLOR_ADJUST - 1.0) < 0.01) { // Sử dụng hằng số và so sánh epsilon
                    float r = color.r * 0.393 + color.g * 0.769 + color.b * 0.189;
                    float g = color.r * 0.349 + color.g * 0.686 + color.b * 0.168;
                    float b = color.r * 0.272 + color.g * 0.534 + color.b * 0.131;
                    gl_FragColor = vec4(r, g, b, color.a);
                } else {
                    gl_FragColor = color; // Fallback if uColorAdjust is not 1.0
                }
            }
        """.trimIndent()
        
        // Fragment Shader: Grayscale (Khôi phục logic gốc một cách cẩn thận)
        // Bắt đầu bằng cách copy nội dung của sepia (đã hoạt động) và sửa đổi
        fragmentShaderSourceGrayscale = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float $UNIFORM_COLOR_ADJUST; // Sử dụng hằng số
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                // Điều kiện cho grayscale
                if (abs($UNIFORM_COLOR_ADJUST - 2.0) < 0.01) { // So sánh với 2.0 cho grayscale
                    // Phép tính grayscale
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    gl_FragColor = vec4(gray, gray, gray, color.a);
                } else {
                    // Fallback: màu gốc nếu không phải grayscale
                    gl_FragColor = color;
                }
            }
        """.trimIndent()
        // Log.d(TAG, "loadShaderSources: Restored original Grayscale shader logic.")

        // Fragment Shader: YUV (3 planes: Y, U, V) to RGB
        // Assumes Y, U, V are separate textures. U and V might be half resolution.
        fragmentShaderSourceYuvToRgb = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTextureY;
            uniform sampler2D sTextureU;
            uniform sampler2D sTextureV;

            // BT.601 standard for YCbCr to RGB conversion
            // Can be adjusted for BT.709 if needed
            const mat3 colorConversionMatrix = mat3(
                1.164,  1.164, 1.164,
                0.0,   -0.392, 2.017,
                1.596, -0.813, 0.0
            );
            // Alternative for direct YUV (not YCbCr) if U/V are in [-0.5, 0.5] range
            // const mat3 colorConversionMatrix = mat3(
            //     1.0, 0.0, 1.402,
            //     1.0, -0.344136, -0.714136,
            //     1.0, 1.772, 0.0
            // );


            void main() {
                float y = texture2D(sTextureY, vTextureCoord).r;
                // U and V textures might be sampled at vTextureCoord or vTextureCoord / 2.0
                // depending on their actual size relative to Y.
                // For YUV_420_888, U and V are typically half resolution.
                // However, if we upload them to textures of the same size as Y (by repeating pixels),
                // then vTextureCoord can be used directly.
                // For simplicity here, assume vTextureCoord is fine, but this might need adjustment.
                float u = texture2D(sTextureU, vTextureCoord).r - 0.5; // U is typically [0,1], shift to [-0.5,0.5]
                float v = texture2D(sTextureV, vTextureCoord).r - 0.5; // V is typically [0,1], shift to [-0.5,0.5]
                
                // Y is [0,1] (or [16/255, 235/255] for limited range)
                // For BT.601, Y is typically [16, 235] and Cb/Cr are [16, 240].
                // We'll assume Y is already normalized [0,1] from texture.
                // And U,V are normalized [0,1] from texture, then shifted.
                
                // Y Cb Cr to RGB conversion for values in range Y:[0,1] CbCr:[-0.5,0.5]
                // R = Y + 1.402 * V
                // G = Y - 0.344136 * U - 0.714136 * V
                // B = Y + 1.772 * U
                // This is one common form. The matrix form is more general.

                // Using the BT.601 matrix for Y in [0,1] and Cb,Cr in [-0.5, 0.5]
                // requires Y to be shifted (Y - 16/255.0) if it's limited range.
                // For simplicity, let's use a common direct conversion:
                vec3 yuv = vec3(y, u, v);
                // R = y + 1.402 * v;
                // G = y - 0.344136 * u - 0.714136 * v;
                // B = y + 1.772 * u;
                // gl_FragColor = vec4(R, G, B, 1.0);

                // Using the provided matrix (assuming Y is [0,1], U/V are shifted to [-0.5, 0.5])
                // The matrix seems to be for YCbCr where Y is [0,1] and Cb,Cr are [0,1] then shifted.
                // Let's use a standard YCbCr to RGB conversion for Y in [0,1], Cb,Cr in [-0.5,0.5]
                // R = Y + 1.40200 * V
                // G = Y - 0.34413 * U - 0.71414 * V
                // B = Y + 1.77200 * U
                // This is equivalent to:
                // vec3 rgb = vec3(y,y,y) + mat3(1.0, 0.0, 1.402,
                //                              1.0, -0.34413, -0.71414,
                //                              1.0, 1.772, 0.0) * vec3(0.0, u, v);
                // No, this is not right.

                // Correct for Y:[0,1], U:[-0.5,0.5], V:[-0.5,0.5] (standard definition for shaders)
                float r = y + 1.402 * v;
                float g = y - 0.344136 * u - 0.714136 * v;
                float b = y + 1.772 * u;
                gl_FragColor = vec4(r, g, b, 1.0);
            }
        """.trimIndent()
    }
}