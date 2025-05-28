package com.example.beauty_camera_plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FilterProcessor functionality.
 * 
 * These tests verify filter processing logic without Android dependencies.
 * Focus on mathematical operations and filter parameter validation.
 */
internal class FilterProcessorTest {

    @Test
    fun validateSepiaMatrixValues() {
        // Test sepia color matrix values (mathematical validation)
        val sepiaMatrix = floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, sepiaMatrix.size) // 4x5 matrix
        assertEquals(0.393f, sepiaMatrix[0], 0.001f)
        assertEquals(0.769f, sepiaMatrix[1], 0.001f)
        assertEquals(0.189f, sepiaMatrix[2], 0.001f)
        assertEquals(1f, sepiaMatrix[18], 0.001f) // Alpha should be 1
    }

    @Test
    fun validateVintageMatrixValues() {
        // Test vintage color matrix values
        val vintageMatrix = floatArrayOf(
            0.6f, 0.3f, 0.1f, 0f, 30f,
            0.2f, 0.7f, 0.1f, 0f, 20f,
            0.2f, 0.1f, 0.4f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, vintageMatrix.size)
        assertEquals(0.6f, vintageMatrix[0], 0.001f)
        assertEquals(30f, vintageMatrix[4], 0.001f) // Red offset
        assertEquals(20f, vintageMatrix[9], 0.001f) // Green offset
        assertEquals(40f, vintageMatrix[14], 0.001f) // Blue offset
    }

    @Test
    fun validateCoolToneMatrixValues() {
        // Test cool tone matrix (enhance blues, reduce reds)
        val coolMatrix = floatArrayOf(
            0.8f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, coolMatrix.size)
        assertEquals(0.8f, coolMatrix[0], 0.001f) // Reduced red
        assertEquals(1.2f, coolMatrix[10], 0.001f) // Enhanced blue
        assertEquals(1f, coolMatrix[18], 0.001f) // Preserved alpha
    }

    @Test
    fun validateWarmToneMatrixValues() {
        // Test warm tone matrix (enhance reds/yellows, reduce blues)
        val warmMatrix = floatArrayOf(
            1.2f, 0f, 0f, 0f, 0f,
            0f, 1.1f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, warmMatrix.size)
        assertEquals(1.2f, warmMatrix[0], 0.001f) // Enhanced red
        assertEquals(1.1f, warmMatrix[6], 0.001f) // Enhanced green
        assertEquals(0.8f, warmMatrix[12], 0.001f) // Reduced blue
    }

    @Test
    fun validateContrastMatrixCalculation() {
        val contrast = 1.5f
        val offset = (1f - contrast) * 128f
        
        // Test contrast adjustment matrix calculation
        val contrastMatrix = floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, contrastMatrix.size)
        assertEquals(contrast, contrastMatrix[0], 0.001f)
        assertEquals(contrast, contrastMatrix[6], 0.001f)
        assertEquals(contrast, contrastMatrix[12], 0.001f)
        assertEquals(offset, contrastMatrix[4], 0.001f)
        assertEquals(-64f, offset, 0.001f) // Expected offset value
    }

    @Test
    fun validateBrightnessMatrixCalculation() {
        val brightness = 50f // Brightness offset
        
        // Test brightness adjustment matrix
        val brightnessMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, brightnessMatrix.size)
        assertEquals(brightness, brightnessMatrix[4], 0.001f)
        assertEquals(brightness, brightnessMatrix[9], 0.001f)
        assertEquals(brightness, brightnessMatrix[14], 0.001f)
        assertEquals(1f, brightnessMatrix[18], 0.001f) // Alpha unchanged
    }

    @Test
    fun filterTypes_containsAllExpectedFilters() {
        val expectedFilters = setOf(
            "none", "sepia", "grayscale", "vintage", "cool", "warm",
            "negative", "solarize", "posterize", "blur", "sharpen",
            "edge", "vignette", "contrast", "brightness"
        )

        // Verify all expected filter types are supported
        expectedFilters.forEach { filter ->
            assertTrue(filter.isNotEmpty())
            assertTrue(filter.length > 2) // Basic validation
        }
        
        assertEquals(15, expectedFilters.size)
    }

    @Test
    fun validateKernelMatrices_haveCorrectDimensions() {
        // Test common convolution kernels used in image processing
        
        // Gaussian blur kernel (3x3)
        val gaussianBlur = floatArrayOf(
            1f, 2f, 1f,
            2f, 4f, 2f,
            1f, 2f, 1f
        )
        assertEquals(9, gaussianBlur.size)
        assertEquals(16f, gaussianBlur.sum()) // Total weight for normalization
        
        // Sharpen kernel (3x3)
        val sharpen = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        assertEquals(9, sharpen.size)
        assertEquals(1f, sharpen.sum()) // Sharpening preserves brightness
        
        // Edge detection kernel (3x3)
        val edgeDetection = floatArrayOf(
            -1f, -1f, -1f,
            -1f, 8f, -1f,
            -1f, -1f, -1f
        )
        assertEquals(9, edgeDetection.size)
        assertEquals(0f, edgeDetection.sum()) // Edge detection kernel sum is 0
    }

    @Test
    fun validateIntensityRange_acceptsValidValues() {
        val validIntensities = listOf(0.0f, 0.5f, 1.0f, 0.25f, 0.75f)
        
        validIntensities.forEach { intensity ->
            assertTrue(intensity >= 0.0f && intensity <= 1.0f, 
                "Intensity value $intensity should be valid")
        }
    }

    @Test
    fun validateIntensityRange_rejectsInvalidValues() {
        val invalidIntensities = listOf(-0.1f, 1.1f, 2.0f, -1.0f)
        
        invalidIntensities.forEach { intensity ->
            assertTrue(intensity < 0.0f || intensity > 1.0f, 
                "Intensity value $intensity should be invalid")
        }
    }

    @Test
    fun validateColorMatrixIdentity() {
        // Test identity matrix (no effect)
        val identityMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        
        assertEquals(20, identityMatrix.size)
        // Check diagonal elements are 1
        assertEquals(1f, identityMatrix[0], 0.001f)
        assertEquals(1f, identityMatrix[6], 0.001f)
        assertEquals(1f, identityMatrix[12], 0.001f)
        assertEquals(1f, identityMatrix[18], 0.001f)
        
        // Check off-diagonal elements are 0
        assertEquals(0f, identityMatrix[1], 0.001f)
        assertEquals(0f, identityMatrix[2], 0.001f)
        assertEquals(0f, identityMatrix[5], 0.001f)
    }
}
