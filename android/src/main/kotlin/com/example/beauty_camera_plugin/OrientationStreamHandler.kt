package com.example.beauty_camera_plugin

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.*

class OrientationStreamHandler(private val activity: Activity) : EventChannel.StreamHandler, SensorEventListener {
    private var eventSink: EventChannel.EventSink? = null
    private var sensorManager: SensorManager? = null
    private var orientationSensor: Sensor? = null
    private var orientationEventListener: OrientationEventListener? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        android.util.Log.d("OrientationStreamHandler", "onListen called. Arguments: $arguments")
        eventSink = events
        startOrientationUpdates()
    }

    override fun onCancel(arguments: Any?) {
        stopOrientationUpdates()
        eventSink = null
    }

    private fun startOrientationUpdates() {
        try {
            sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (orientationSensor != null) {
                sensorManager?.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                android.util.Log.w("OrientationStreamHandler", "Accelerometer sensor not available.")
            }

            // Also listen to system orientation changes
            orientationEventListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
                override fun onOrientationChanged(orientation: Int) {
                    android.util.Log.d("OrientationStreamHandler", "onOrientationChanged - raw orientation: $orientation")
                    if (orientation == ORIENTATION_UNKNOWN) {
                        android.util.Log.d("OrientationStreamHandler", "onOrientationChanged - ORIENTATION_UNKNOWN, returning")
                        return
                    }

                    val deviceOrientation = when {
                        orientation >= 315 || orientation < 45 -> 0 // Portrait
                        orientation >= 45 && orientation < 135 -> 270 // Landscape left
                        orientation >= 135 && orientation < 225 -> 180 // Portrait upside down
                        orientation >= 225 && orientation < 315 -> 90 // Landscape right
                        else -> {
                            android.util.Log.d("OrientationStreamHandler", "onOrientationChanged - intermediate/unknown state, returning")
                            return // Avoid sending data for intermediate/unknown states
                        }
                    }
                    android.util.Log.d("OrientationStreamHandler", "onOrientationChanged - deviceOrientation: $deviceOrientation")
                    val uiOrientation = getUIOrientation()
                    sendOrientationData(deviceOrientation, uiOrientation)
                }
            }

            if (orientationEventListener?.canDetectOrientation() == true) {
                orientationEventListener?.enable()
            } else {
                android.util.Log.w("OrientationStreamHandler", "Cannot detect orientation with OrientationEventListener.")
                orientationEventListener?.disable() // Ensure it's disabled if it cannot detect
            }
        } catch (e: Exception) {
            android.util.Log.e("OrientationStreamHandler", "Error in startOrientationUpdates: ${e.message}", e)
            eventSink?.error("INITIALIZATION_ERROR", "Error starting orientation updates: ${e.message}", null)
        }
    }

    private fun stopOrientationUpdates() {
        sensorManager?.unregisterListener(this)
        orientationEventListener?.disable()
        sensorManager = null
        orientationSensor = null
        orientationEventListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // This accelerometer logic can be very noisy and might conflict with OrientationEventListener
        // We'll prioritize OrientationEventListener for now.
        // If needed, this can be re-enabled with more sophisticated filtering.
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun getUIOrientation(): Int {
        return when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun sendOrientationData(deviceOrientation: Int, uiOrientation: Int) {
        android.util.Log.d("OrientationStreamHandler", "sendOrientationData - deviceOrientation: $deviceOrientation, uiOrientation: $uiOrientation")
        val data = mapOf(
            "deviceOrientation" to deviceOrientation,
            "uiOrientation" to uiOrientation,
            "timestamp" to System.currentTimeMillis()
        )
        scope.launch {
            try {
                eventSink?.success(data)
                android.util.Log.d("OrientationStreamHandler", "sendOrientationData - Data sent successfully")
            } catch (e: Exception) {
                android.util.Log.e("OrientationStreamHandler", "Error sending orientation data: ${e.message}", e)
                // Optionally, inform Flutter side about the error if critical
                // eventSink?.error("SEND_ERROR", "Error sending orientation data: ${e.message}", null)
            }
        }
    }

    fun dispose() {
        stopOrientationUpdates()
        scope.cancel()
    }
}
