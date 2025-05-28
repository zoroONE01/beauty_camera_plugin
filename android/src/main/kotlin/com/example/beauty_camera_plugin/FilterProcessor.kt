package com.example.beauty_camera_plugin

import android.graphics.*
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.FileOutputStream
import kotlin.math.*

object FilterProcessor {
    private const val TAG = "FilterProcessor"

    fun applySepia(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applySepia called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getSepiaMatrix(intensity), "applySepia")
    }

    fun applyVintage(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyVintage called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getVintageMatrix(intensity), "applyVintage")
    }

    fun applyCool(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyCool called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getCoolMatrix(intensity), "applyCool")
    }

    fun applyWarm(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyWarm called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getWarmMatrix(intensity), "applyWarm")
    }

    fun applyBlur(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyBlur called with intensity: $intensity")
        return try {
            // Note: The original saveBitmap and recycle logic is removed as this function
            // now returns the bitmap for further processing or display.
            // Caller is responsible for recycling the original and returned bitmaps if needed.
            applyGaussianBlur(inputBitmap, intensity * 25f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply blur filter to bitmap", e)
            inputBitmap // Return original on error
        }
    }

    fun applySharpen(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applySharpen called with intensity: $intensity")
        return try {
            applySharpenFilter(inputBitmap, intensity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply sharpen filter to bitmap", e)
            inputBitmap
        }
    }

    fun applyEdge(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyEdge called with intensity: $intensity")
        return try {
            applyEdgeDetection(inputBitmap, intensity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply edge filter to bitmap", e)
            inputBitmap
        }
    }

    fun applyVignette(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyVignette called with intensity: $intensity")
        return try {
            applyVignetteEffect(inputBitmap, intensity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply vignette filter to bitmap", e)
            inputBitmap
        }
    }

fun applyMono(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyMono called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getMonoMatrix(intensity), "applyMono")
    }

    fun applyNegative(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyNegative called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getNegativeMatrix(intensity), "applyNegative")
    }
    fun applyContrast(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyContrast called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getContrastMatrix(intensity), "applyContrast")
    }

    fun applyBrightness(inputBitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "applyBrightness called with intensity: $intensity")
        return applyColorMatrixToBitmap(inputBitmap, getBrightnessMatrix(intensity), "applyBrightness")
    }

    // Renamed from applyColorMatrixFilter and modified to work with Bitmaps
    private fun applyColorMatrixToBitmap(inputBitmap: Bitmap, colorMatrix: ColorMatrix, filterName: String): Bitmap {
        Log.d(TAG, "applyColorMatrixToBitmap called for filter: $filterName")
        return try {
            // The applyColorMatrix internal function already returns a new Bitmap
            // and does not modify the inputBitmap.
            applyColorMatrix(inputBitmap, colorMatrix)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply color matrix filter to bitmap", e)
            inputBitmap // Return original bitmap on error
        }
    }

    // This function remains largely the same as it already operates on Bitmaps
    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyGaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Simple box blur approximation
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val blurredPixels = boxBlur(pixels, width, height, radius.toInt())
        result.setPixels(blurredPixels, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val result = IntArray(pixels.size)
        val r = minOf(radius, width / 2, height / 2)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var totalR = 0
                var totalG = 0
                var totalB = 0
                var count = 0
                
                for (dy in -r..r) {
                    for (dx in -r..r) {
                        val nx = x + dx
                        val ny = y + dy
                        
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            val pixel = pixels[ny * width + nx]
                            totalR += Color.red(pixel)
                            totalG += Color.green(pixel)
                            totalB += Color.blue(pixel)
                            count++
                        }
                    }
                }
                
                val avgR = totalR / count
                val avgG = totalG / count
                val avgB = totalB / count
                
                result[y * width + x] = Color.rgb(avgR, avgG, avgB)
            }
        }
        
        return result
    }

    private fun applySharpenFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Sharpen kernel
        val kernel = arrayOf(
            arrayOf(0f, -intensity, 0f),
            arrayOf(-intensity, 1f + 4f * intensity, -intensity),
            arrayOf(0f, -intensity, 0f)
        )
        
        val sharpened = applyConvolution(pixels, width, height, kernel)
        result.setPixels(sharpened, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun applyEdgeDetection(bitmap: Bitmap, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Sobel edge detection kernel
        val kernel = arrayOf(
            arrayOf(-intensity, -intensity, -intensity),
            arrayOf(-intensity, 8f * intensity, -intensity),
            arrayOf(-intensity, -intensity, -intensity)
        )
        
        val edges = applyConvolution(pixels, width, height, kernel)
        result.setPixels(edges, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun applyConvolution(pixels: IntArray, width: Int, height: Int, kernel: Array<Array<Float>>): IntArray {
        val result = IntArray(pixels.size)
        val kernelSize = kernel.size
        val offset = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in 0 until kernelSize) {
                    for (kx in 0 until kernelSize) {
                        val px = x + kx - offset
                        val py = y + ky - offset
                        
                        if (px >= 0 && px < width && py >= 0 && py < height) {
                            val pixel = pixels[py * width + px]
                            val weight = kernel[ky][kx]
                            
                            r += Color.red(pixel) * weight
                            g += Color.green(pixel) * weight
                            b += Color.blue(pixel) * weight
                        }
                    }
                }
                
                result[y * width + x] = Color.rgb(
                    r.coerceIn(0f, 255f).toInt(),
                    g.coerceIn(0f, 255f).toInt(),
                    b.coerceIn(0f, 255f).toInt()
                )
            }
        }
        
        return result
    }

    private fun applyVignetteEffect(bitmap: Bitmap, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = sqrt(centerX * centerX + centerY * centerY)
        
        val paint = Paint().apply {
            shader = RadialGradient(
                centerX, centerY, maxRadius,
                intArrayOf(Color.TRANSPARENT, Color.argb((255 * intensity).toInt(), 0, 0, 0)),
                floatArrayOf(0.6f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        return result
    }

    private fun getSepiaMatrix(intensity: Float): ColorMatrix {
        val sepiaMatrix = ColorMatrix()
        sepiaMatrix.set(floatArrayOf(
            0.393f * intensity + (1 - intensity), 0.769f * intensity, 0.189f * intensity, 0f, 0f,
            0.349f * intensity, 0.686f * intensity + (1 - intensity), 0.168f * intensity, 0f, 0f,
            0.272f * intensity, 0.534f * intensity, 0.131f * intensity + (1 - intensity), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return sepiaMatrix
    }

    private fun getVintageMatrix(intensity: Float): ColorMatrix {
        val vintageMatrix = ColorMatrix()
        vintageMatrix.set(floatArrayOf(
            0.6f * intensity + (1 - intensity), 0.3f * intensity, 0.1f * intensity, 0f, 10f * intensity,
            0.2f * intensity, 0.8f * intensity + (1 - intensity), 0.1f * intensity, 0f, 5f * intensity,
            0.1f * intensity, 0.2f * intensity, 0.7f * intensity + (1 - intensity), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return vintageMatrix
    }

    private fun getCoolMatrix(intensity: Float): ColorMatrix {
        val coolMatrix = ColorMatrix()
        coolMatrix.set(floatArrayOf(
            1f, 0f, 0.2f * intensity, 0f, 0f,
            0f, 1f, 0.1f * intensity, 0f, 0f,
            0f, 0f, 1f + 0.3f * intensity, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return coolMatrix
    }

    private fun getWarmMatrix(intensity: Float): ColorMatrix {
        val warmMatrix = ColorMatrix()
        warmMatrix.set(floatArrayOf(
            1f + 0.3f * intensity, 0f, 0f, 0f, 0f,
            0f, 1f + 0.1f * intensity, 0f, 0f, 0f,
            0f, 0f, 1f - 0.2f * intensity, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return warmMatrix
    }

    private fun getContrastMatrix(intensity: Float): ColorMatrix {
        val contrast = 1f + intensity
        val offset = (1f - contrast) / 2f * 255f
        
        val contrastMatrix = ColorMatrix()
        contrastMatrix.set(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        return contrastMatrix
    }

    private fun getBrightnessMatrix(intensity: Float): ColorMatrix {
        val brightness = intensity * 100f - 50f // Convert to -50 to +50 range
        
        val brightnessMatrix = ColorMatrix()
        brightnessMatrix.set(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        return brightnessMatrix
    }

private fun getMonoMatrix(intensity: Float): ColorMatrix {
        val matrix = ColorMatrix()
        val r = 0.299f
        val g = 0.587f
        val b = 0.114f
        matrix.set(floatArrayOf(
            r, g, b, 0f, 0f,
            r, g, b, 0f, 0f,
            r, g, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Lerp between identity and full monochrome based on intensity
        val identity = ColorMatrix() // Default is identity matrix
        val resultMatrix = ColorMatrix()
        for (i in 0..19) {
            resultMatrix.getArray()[i] = (1 - intensity) * identity.getArray()[i] + intensity * matrix.getArray()[i]
        }
        return resultMatrix
    }

    private fun getNegativeMatrix(intensity: Float): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.set(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Lerp between identity and full negative based on intensity
        val identity = ColorMatrix() // Default is identity matrix
        val resultMatrix = ColorMatrix()
        for (i in 0..19) {
            resultMatrix.getArray()[i] = (1 - intensity) * identity.getArray()[i] + intensity * matrix.getArray()[i]
        }
        // Ensure alpha is not inverted if it's part of the lerp
        resultMatrix.getArray()[18] = 1f
        return resultMatrix
    }

    private fun saveBitmap(bitmap: Bitmap, filePath: String) {
        try {
            // Preserve EXIF data
            val originalExif = ExifInterface(filePath)
            
            FileOutputStream(filePath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Restore EXIF data
            val newExif = ExifInterface(filePath)
            copyExifData(originalExif, newExif)
            newExif.saveAttributes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap", e)
        }
    }

    private fun copyExifData(source: ExifInterface, destination: ExifInterface) {
        val tags = listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT
        )
        
        for (tag in tags) {
            val value = source.getAttribute(tag)
            if (value != null) {
                destination.setAttribute(tag, value)
            }
        }
    }
}
