package cloud.mindbox.mobile_sdk.inapp.presentation.view.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import kotlin.math.abs
import kotlin.math.sqrt

internal enum class MotionGesture(val value: String) {
    SHAKE("shake"),
    FLIP("flip"),
}

internal enum class DevicePosition(val value: String) {
    FACE_UP("faceUp"),
    FACE_DOWN("faceDown"),
    PORTRAIT("portrait"),
    PORTRAIT_UPSIDE_DOWN("portraitUpsideDown"),
    LANDSCAPE_LEFT("landscapeLeft"),
    LANDSCAPE_RIGHT("landscapeRight"),
}

internal data class MotionStartResult(
    val started: Set<MotionGesture>,
    val unavailable: Set<MotionGesture>,
) {
    val allUnavailable: Boolean get() = started.isEmpty() && unavailable.isNotEmpty()
}

internal interface MotionServiceProtocol {
    var onGestureDetected: ((gesture: MotionGesture, data: Map<String, String>) -> Unit)?

    fun startMonitoring(gestures: Set<MotionGesture>): MotionStartResult

    fun stopMonitoring()
}

internal class MotionService(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val timeProvider: TimeProvider,
) : MotionServiceProtocol {

    private companion object {
        const val SMOOTHING_FACTOR = 0.7f
        val SHAKE_COOLDOWN = Milliseconds(800L)
        const val TABLET_MIN_WIDTH_DP = 600
        const val PHONE_THRESHOLD_G = 3.0f
        const val TABLET_THRESHOLD_G = 1.5f
        const val FLIP_ENTER_THRESHOLD_G = 0.8f
        const val FLIP_EXIT_THRESHOLD_G = 0.6f
    }

    override var onGestureDetected: ((gesture: MotionGesture, data: Map<String, String>) -> Unit)? = null

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val shakeAccelerationThreshold: Float by lazy {
        val isTablet = context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
        val thresholdG = if (isTablet) TABLET_THRESHOLD_G else PHONE_THRESHOLD_G
        thresholdG * SensorManager.GRAVITY_EARTH
    }

    private var activeGestures: Set<MotionGesture> = emptySet()
    private var suspendedGestures: Set<MotionGesture>? = null

    private var lastShakeX = 0f
    private var lastShakeY = 0f
    private var lastShakeZ = 0f
    private var accumulateShake = 0f
    private var lastShakeTimestamp: Timestamp = Timestamp(0L)

    private var currentFlipPosition: DevicePosition? = null

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            processShake(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val flipListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            processFlip(
                x = -event.values[0],
                y = -event.values[1],
                z = -event.values[2],
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = suspend()

        override fun onStart(owner: LifecycleOwner) = resume()
    }

    override fun startMonitoring(gestures: Set<MotionGesture>): MotionStartResult {
        if (activeGestures.isNotEmpty()) stopMonitoring()
        val unavailable = mutableSetOf<MotionGesture>()
        if (gestures.contains(MotionGesture.SHAKE) && !isShakeAvailable()) {
            unavailable.add(MotionGesture.SHAKE)
        }
        if (gestures.contains(MotionGesture.FLIP) && !isFlipAvailable()) {
            unavailable.add(MotionGesture.FLIP)
        }

        activeGestures = gestures - unavailable
        val result = MotionStartResult(started = activeGestures, unavailable = unavailable)
        if (activeGestures.isEmpty()) return result
        addLifecycleObserver()
        startSensors()

        mindboxLogI("[WebView] Motion: monitoring started for ${activeGestures.map { it.value }}")
        if (unavailable.isNotEmpty()) {
            mindboxLogI("[WebView] Motion: unavailable gestures: ${unavailable.map { it.value }}")
        }
        return result
    }

    override fun stopMonitoring() {
        removeLifecycleObserver()
        stopSensors()
        activeGestures = emptySet()
        suspendedGestures = null
        mindboxLogI("[WebView] Motion: monitoring stopped")
    }

    private fun addLifecycleObserver() {
        lifecycle.addObserver(lifecycleObserver)
    }

    private fun removeLifecycleObserver() {
        lifecycle.removeObserver(lifecycleObserver)
    }

    internal fun suspend() {
        if (activeGestures.isEmpty()) return
        suspendedGestures = activeGestures
        stopSensors()
        mindboxLogI("[WebView] Motion: suspended (app in background)")
    }

    internal fun resume() {
        val gestures = suspendedGestures ?: return
        suspendedGestures = null
        activeGestures = gestures
        startSensors()
        mindboxLogI("[WebView] Motion: resumed (app in foreground)")
    }

    private fun startSensors() {
        if (activeGestures.contains(MotionGesture.SHAKE)) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
                sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        if (activeGestures.contains(MotionGesture.FLIP)) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensor ->
                sensorManager.registerListener(flipListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun stopSensors() {
        sensorManager?.unregisterListener(shakeListener)
        sensorManager?.unregisterListener(flipListener)
        resetShakeState()
        currentFlipPosition = null
    }

    private fun resetShakeState() {
        lastShakeX = 0f
        lastShakeY = 0f
        lastShakeZ = 0f
        accumulateShake = 0f
        lastShakeTimestamp = Timestamp(0L)
    }

    private fun isShakeAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private fun isFlipAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    internal fun processShake(x: Float, y: Float, z: Float) {
        val dx = x - lastShakeX
        val dy = y - lastShakeY
        val dz = z - lastShakeZ
        val delta = sqrt(dx * dx + dy * dy + dz * dz)
        accumulateShake = accumulateShake * SMOOTHING_FACTOR + delta
        val now: Timestamp = timeProvider.currentTimestamp()
        val elapsed: Milliseconds = timeProvider.elapsedSince(lastShakeTimestamp)
        if (accumulateShake > shakeAccelerationThreshold && elapsed.interval > SHAKE_COOLDOWN.interval) {
            accumulateShake = 0f
            lastShakeTimestamp = now
            loggingRunCatching { onGestureDetected?.invoke(MotionGesture.SHAKE, emptyMap()) }
        }

        lastShakeX = x
        lastShakeY = y
        lastShakeZ = z
    }

    private fun processFlip(x: Float, y: Float, z: Float) {
        val newPosition = resolvePosition(x = x, y = y, z = z, current = currentFlipPosition)
        if (newPosition == null || newPosition == currentFlipPosition) return

        val from = currentFlipPosition
        currentFlipPosition = newPosition

        if (from == null) return

        loggingRunCatching {
            onGestureDetected?.invoke(
                MotionGesture.FLIP,
                mapOf("from" to from.value, "to" to newPosition.value),
            )
        }
    }

    internal fun resolvePosition(
        x: Float,
        y: Float,
        z: Float,
        current: DevicePosition?,
    ): DevicePosition? {
        data class Axis(
            val value: Float,
            val negative: DevicePosition,
            val positive: DevicePosition,
        )

        val axes = listOf(
            Axis(z, DevicePosition.FACE_UP, DevicePosition.FACE_DOWN),
            Axis(y, DevicePosition.PORTRAIT, DevicePosition.PORTRAIT_UPSIDE_DOWN),
            Axis(x, DevicePosition.LANDSCAPE_LEFT, DevicePosition.LANDSCAPE_RIGHT),
        )

        if (current != null) {
            for (axis in axes) {
                val position = if (axis.value > 0f) axis.positive else axis.negative
                if (position == current && abs(axis.value) > FLIP_EXIT_THRESHOLD_G * SensorManager.GRAVITY_EARTH) {
                    return current
                }
            }
        }

        var dominantPosition: DevicePosition? = null
        var maxMagnitude = FLIP_ENTER_THRESHOLD_G * SensorManager.GRAVITY_EARTH

        for (axis in axes) {
            val magnitude = abs(axis.value)
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                dominantPosition = if (axis.value > 0f) axis.positive else axis.negative
            }
        }
        return dominantPosition
    }
}
