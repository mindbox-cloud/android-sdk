package cloud.mindbox.mobile_sdk.inapp.presentation.view.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
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
) : MotionServiceProtocol {

    private companion object {
        const val SMOOTHING_FACTOR = 0.7f
        const val COOLDOWN_MS = 800L
        const val TABLET_MIN_WIDTH_DP = 600
        const val PHONE_THRESHOLD_G = 3.0f
        const val TABLET_THRESHOLD_G = 1.5f
        const val FLIP_ENTER_THRESHOLD_G = 0.8f
        const val FLIP_EXIT_THRESHOLD_G = 0.6f
    }

    override var onGestureDetected: ((gesture: MotionGesture, data: Map<String, String>) -> Unit)? = null

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

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
    private var accumShake = 0f
    private var lastShakeTimestampMs = 0L

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
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = suspend()

        override fun onStart(owner: LifecycleOwner) = resume()
    }

    override fun startMonitoring(gestures: Set<MotionGesture>): MotionStartResult {
        stopMonitoring()

        val unavailable = mutableSetOf<MotionGesture>()
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    private fun removeLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }

    private fun suspend() {
        if (activeGestures.isEmpty()) return
        suspendedGestures = activeGestures
        stopSensors()
        mindboxLogI("[WebView] Motion: suspended (app in background)")
    }

    private fun resume() {
        val gestures = suspendedGestures ?: return
        suspendedGestures = null
        activeGestures = gestures
        startSensors()
        mindboxLogI("[WebView] Motion: resumed (app in foreground)")
    }

    private fun startSensors() {
        if (activeGestures.contains(MotionGesture.SHAKE)) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        if (activeGestures.contains(MotionGesture.FLIP)) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            if (sensor != null) {
                sensorManager.registerListener(flipListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(shakeListener)
        sensorManager.unregisterListener(flipListener)
        resetShakeState()
        currentFlipPosition = null
    }

    private fun resetShakeState() {
        lastShakeX = 0f
        lastShakeY = 0f
        lastShakeZ = 0f
        accumShake = 0f
        lastShakeTimestampMs = 0L
    }

    private fun isFlipAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    private fun processShake(x: Float, y: Float, z: Float) {
        val dx = x - lastShakeX
        val dy = y - lastShakeY
        val dz = z - lastShakeZ
        val delta = sqrt(dx * dx + dy * dy + dz * dz)
        accumShake = accumShake * SMOOTHING_FACTOR + delta
        mindboxLogI("Accum shake is $accumShake")
        val nowMs = System.currentTimeMillis()
        if (accumShake > shakeAccelerationThreshold && nowMs - lastShakeTimestampMs > COOLDOWN_MS) {
            val detectedAccum = accumShake
            accumShake = 0f
            lastShakeTimestampMs = nowMs
            mindboxLogD("[WebView] Motion: shake detected (accum=$detectedAccum, threshold=$shakeAccelerationThreshold)")
            onGestureDetected?.invoke(MotionGesture.SHAKE, emptyMap())
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

        mindboxLogD("[WebView] Motion: flip detected ${from.value} -> ${newPosition.value}")
        onGestureDetected?.invoke(
            MotionGesture.FLIP,
            mapOf("from" to from.value, "to" to newPosition.value),
        )
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

        var best: DevicePosition? = null
        var bestMagnitude = FLIP_ENTER_THRESHOLD_G * SensorManager.GRAVITY_EARTH

        for (axis in axes) {
            val magnitude = abs(axis.value)
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                best = if (axis.value > 0f) axis.positive else axis.negative
            }
        }
        return best
    }
}
