package com.example.beauty_camera_plugin

import android.util.Size

/**
 * Enum defining the resolution presets for the camera
 */
enum class ResolutionPreset {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    ULTRA_HIGH;

    /**
     * Get the resolution size for preview
     */
    fun getPreviewSize(): Size {
        return when (this) {
            LOW -> Size(320, 240)
            MEDIUM -> Size(640, 480)
            HIGH -> Size(1280, 720)
            VERY_HIGH -> Size(1920, 1080)
            ULTRA_HIGH -> Size(3840, 2160)
        }
    }

    /**
     * Get the resolution size for photo capture
     */
    fun getCaptureSize(): Size {
        return when (this) {
            LOW -> Size(640, 480)
            MEDIUM -> Size(1280, 720)
            HIGH -> Size(1920, 1080)
            VERY_HIGH -> Size(3840, 2160)
            ULTRA_HIGH -> Size(4032, 3024) // Typical 12MP camera
        }
    }
}
