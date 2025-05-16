package com.example.beauty_camera_plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Helper class used to convert a YUV_420_888 image from CameraX API to a Bitmap
 */
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteArray
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

    @Synchronized
    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        // Ensure we have a valid output
        if (pixelCount != image.width * image.height) {
            // Compute the allocation size based on the dimensions of the output bitmap
            pixelCount = image.width * image.height
            yuvBuffer = ByteArray(
                pixelCount * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
            
            // Create input/output allocations
            inputAllocation = Allocation.createSized(rs, Element.U8(rs), yuvBuffer.size)
            outputAllocation = Allocation.createFromBitmap(rs, output)
            
            // Set the allocation's format for the script
            scriptYuvToRgb.setInput(inputAllocation)
        }

        // Get the YUV data from the image planes to a byte array
        fillYuvBuffer(image.image!!, image.width, image.height, yuvBuffer)
        
        // Pass data to the RenderScript and execute the conversion
        inputAllocation.copyFrom(yuvBuffer)
        scriptYuvToRgb.forEach(outputAllocation)
        outputAllocation.copyTo(output)
        
        // Important: close the image now that we're done
        image.close()
    }

    private fun fillYuvBuffer(image: Image, width: Int, height: Int, yuvBuffer: ByteArray) {
        val planeCount = image.planes.size
        var yIndex = 0
        var uvIndex = pixelCount
        
        // Copy Y plane data
        val yPlane = image.planes[0].buffer
        val yPixelStride = image.planes[0].pixelStride
        val yRowStride = image.planes[0].rowStride
        
        for (y in 0 until height) {
            val buf = yPlane.duplicate()
            buf.position(y * yRowStride)
            for (x in 0 until width) {
                yuvBuffer[yIndex++] = buf.get(x * yPixelStride)
            }
        }
        
        // Copy U and V plane data (NV21 format: V first, then U)
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val uPixelStride = image.planes[1].pixelStride
        val uRowStride = image.planes[1].rowStride
        val vPixelStride = image.planes[2].pixelStride
        val vRowStride = image.planes[2].rowStride
        
        // Chroma channels are subsampled (half the resolution of luma channel)
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        
        for (y in 0 until chromaHeight) {
            val vBuf = vPlane.duplicate()
            val uBuf = uPlane.duplicate()
            vBuf.position(y * vRowStride)
            uBuf.position(y * uRowStride)
            for (x in 0 until chromaWidth) {
                yuvBuffer[uvIndex++] = vBuf.get(x * vPixelStride)
                yuvBuffer[uvIndex++] = uBuf.get(x * uPixelStride)
            }
        }
    }
}
