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
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlin.math.abs
import kotlin.math.hypot

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

internal data class MotionVector(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO: MotionVector = MotionVector(0f, 0f, 0f)
    }

    operator fun minus(other: MotionVector): MotionVector = MotionVector(x - other.x, y - other.y, z - other.z)

    fun magnitude(): Float = hypot(hypot(x, y), z)
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

    private var lastShakeVector: MotionVector = MotionVector.ZERO
    private var accumulateShake = 0f
    private var lastShakeTimestamp: Timestamp = Timestamp.ZERO

    private var currentFlipPosition: DevicePosition? = null

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            processShake(MotionVector(event.values[0], event.values[1], event.values[2]))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val flipListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            processFlip(MotionVector(-event.values[0], -event.values[1], -event.values[2]))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = suspend()

        override fun onStart(owner: LifecycleOwner) = resume()
    }

    override fun startMonitoring(gestures: Set<MotionGesture>): MotionStartResult {
        if (activeGestures.isNotEmpty()) stopMonitoring()
        val unavailable = buildSet {
            if (gestures.contains(MotionGesture.SHAKE) && !isShakeAvailable()) {
                add(MotionGesture.SHAKE)
            }
            if (gestures.contains(MotionGesture.FLIP) && !isFlipAvailable()) {
                add(MotionGesture.FLIP)
            }
        }

        activeGestures = gestures - unavailable
        val result = MotionStartResult(started = activeGestures, unavailable = unavailable)
        if (activeGestures.isEmpty()) return result
        addLifecycleObserver()
        startSensors()

        mindboxLogI("Motion: monitoring started for ${activeGestures.map { it.value }}")
        if (unavailable.isNotEmpty()) {
            mindboxLogI("Motion: unavailable gestures: ${unavailable.map { it.value }}")
        }
        return result
    }

    override fun stopMonitoring() {
        if (activeGestures.isEmpty() && suspendedGestures == null) return
        removeLifecycleObserver()
        stopSensors()
        activeGestures = emptySet()
        suspendedGestures = null
        mindboxLogI("Motion: monitoring stopped")
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
        mindboxLogI("Motion: suspended (app in background)")
    }

    internal fun resume() {
        val gestures = suspendedGestures ?: return
        suspendedGestures = null
        activeGestures = gestures
        startSensors()
        mindboxLogI("Motion: resumed (app in foreground)")
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
        lastShakeVector = MotionVector.ZERO
        accumulateShake = 0f
        lastShakeTimestamp = Timestamp.ZERO
    }

    private fun isShakeAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private fun isFlipAvailable(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    internal fun processShake(vector: MotionVector) {
        val delta = (vector - lastShakeVector).magnitude()
        accumulateShake = accumulateShake * SMOOTHING_FACTOR + delta
        val now: Timestamp = timeProvider.currentTimestamp()
        val elapsed: Milliseconds = timeProvider.elapsedSince(lastShakeTimestamp)
        if (accumulateShake > shakeAccelerationThreshold && elapsed.interval > SHAKE_COOLDOWN.interval) {
            accumulateShake = 0f
            lastShakeTimestamp = now
            loggingRunCatching { onGestureDetected?.invoke(MotionGesture.SHAKE, emptyMap()) }
        }
        lastShakeVector = vector
    }

    private fun processFlip(vector: MotionVector) {
        val newPosition = resolvePosition(vector = vector, current = currentFlipPosition)
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
        vector: MotionVector,
        current: DevicePosition?,
    ): DevicePosition? {
        data class Axis(
            val value: Float,
            val negative: DevicePosition,
            val positive: DevicePosition,
        )

        val axes = listOf(
            Axis(vector.z, DevicePosition.FACE_UP, DevicePosition.FACE_DOWN),
            Axis(vector.y, DevicePosition.PORTRAIT, DevicePosition.PORTRAIT_UPSIDE_DOWN),
            Axis(vector.x, DevicePosition.LANDSCAPE_LEFT, DevicePosition.LANDSCAPE_RIGHT),
        )

        if (current != null) {
            axes.forEach { axis ->
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
