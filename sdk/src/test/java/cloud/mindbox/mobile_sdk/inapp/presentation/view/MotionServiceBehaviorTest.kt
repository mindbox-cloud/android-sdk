package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.Lifecycle
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionGesture
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionService
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTimeProvider(private var nowMs: Long = 0L) : TimeProvider {
    override fun currentTimeMillis(): Long = nowMs

    override fun currentTimestamp(): Timestamp = Timestamp(nowMs)

    override fun elapsedSince(startTimeMillis: Timestamp): Milliseconds =
        Milliseconds(nowMs - startTimeMillis.ms)

    fun advanceBy(ms: Long) {
        nowMs += ms
    }
}

class MotionServiceShakeTest {

    private val phoneThresholdG = 3.0f * SensorManager.GRAVITY_EARTH

    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var motionService: MotionService

    @Before
    fun setUp() {
        val mockContext: Context = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockk<SensorManager>(relaxed = true)
        every { mockContext.resources } returns mockk(relaxed = true)
        fakeTimeProvider = FakeTimeProvider(nowMs = 10_000L)
        motionService = MotionService(
            context = mockContext,
            lifecycle = mockk<Lifecycle>(relaxed = true),
            timeProvider = fakeTimeProvider,
        )
    }

    @Test
    fun `processShake fires callback when accumulated force exceeds threshold`() {
        var isDetected = false
        motionService.onGestureDetected = { gesture, _ -> isDetected = gesture == MotionGesture.SHAKE }

        motionService.processShake(x = phoneThresholdG + 1f, y = 0f, z = 0f)

        assertTrue(isDetected)
    }

    @Test
    fun `processShake does not fire callback when force is below threshold`() {
        var isDetected = false
        motionService.onGestureDetected = { _, _ -> isDetected = true }

        motionService.processShake(x = phoneThresholdG - 1f, y = 0f, z = 0f)

        assertFalse(isDetected)
    }

    @Test
    fun `processShake does not fire callback during cooldown`() {
        var detectedCount = 0
        motionService.onGestureDetected = { _, _ -> detectedCount++ }

        motionService.processShake(x = phoneThresholdG + 1f, y = 0f, z = 0f)
        motionService.processShake(x = 0f, y = 0f, z = 0f)

        assertEquals(1, detectedCount)
    }

    @Test
    fun `processShake fires again after cooldown expires`() {
        var detectedCount = 0
        motionService.onGestureDetected = { _, _ -> detectedCount++ }

        motionService.processShake(x = phoneThresholdG + 1f, y = 0f, z = 0f)
        fakeTimeProvider.advanceBy(900L)
        motionService.processShake(x = 0f, y = 0f, z = 0f)

        assertEquals(2, detectedCount)
    }

    @Test
    fun `processShake does not fire after exactly cooldown boundary`() {
        var detectedCount = 0
        motionService.onGestureDetected = { _, _ -> detectedCount++ }

        motionService.processShake(x = phoneThresholdG + 1f, y = 0f, z = 0f)
        fakeTimeProvider.advanceBy(800L)
        motionService.processShake(x = 0f, y = 0f, z = 0f)

        assertEquals(1, detectedCount)
    }

    @Test
    fun `processShake sends empty data map for shake gesture`() {
        var capturedData: Map<String, String>? = null
        motionService.onGestureDetected = { _, data -> capturedData = data }

        motionService.processShake(x = phoneThresholdG + 1f, y = 0f, z = 0f)

        assertTrue(capturedData != null && capturedData?.isEmpty() == true)
    }

    @Test
    fun `processShake accumulates force across multiple frames`() {
        var isDetected = false
        motionService.onGestureDetected = { _, _ -> isDetected = true }

        val halfThreshold = phoneThresholdG / 2f
        motionService.processShake(x = halfThreshold, y = 0f, z = 0f)
        motionService.processShake(x = 0f, y = 0f, z = 0f)
        motionService.processShake(x = halfThreshold, y = 0f, z = 0f)

        assertTrue(isDetected)
    }
}

class MotionServiceLifecycleTest {

    private lateinit var mockSensorManager: SensorManager
    private lateinit var mockContext: Context
    private lateinit var motionService: MotionService

    @Before
    fun setUp() {
        val mockSensor = mockk<Sensor>(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns mockSensor
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns mockSensor

        mockContext = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager
        every { mockContext.resources } returns mockk(relaxed = true)

        motionService = MotionService(
            context = mockContext,
            lifecycle = mockk<Lifecycle>(relaxed = true),
            timeProvider = SystemTimeProvider(),
        )
    }

    @Test
    fun `startMonitoring registers sensor listener`() {
        motionService.startMonitoring(setOf(MotionGesture.SHAKE))

        verify(exactly = 1) { mockSensorManager.registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) }
    }

    @Test
    fun `suspend stops sensors when monitoring is active`() {
        motionService.startMonitoring(setOf(MotionGesture.SHAKE))

        motionService.suspend()

        verify { mockSensorManager.unregisterListener(any<SensorEventListener>()) }
    }

    @Test
    fun `suspend does nothing when monitoring is not active`() {
        motionService.suspend()

        verify(exactly = 0) { mockSensorManager.unregisterListener(any<SensorEventListener>()) }
    }

    @Test
    fun `resume restarts sensors after suspend`() {
        motionService.startMonitoring(setOf(MotionGesture.SHAKE))
        motionService.suspend()

        motionService.resume()

        verify(exactly = 2) { mockSensorManager.registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) }
    }

    @Test
    fun `resume does nothing without prior suspend`() {
        motionService.resume()

        verify(exactly = 0) { mockSensorManager.registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) }
    }

    @Test
    fun `stopMonitoring after suspend prevents resume from restarting sensors`() {
        motionService.startMonitoring(setOf(MotionGesture.SHAKE))
        motionService.suspend()
        motionService.stopMonitoring()

        motionService.resume()

        verify(exactly = 1) { mockSensorManager.registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) }
    }

    @Test
    fun `stopMonitoring unregisters all sensors`() {
        motionService.startMonitoring(setOf(MotionGesture.SHAKE, MotionGesture.FLIP))

        motionService.stopMonitoring()

        verify(atLeast = 1) { mockSensorManager.unregisterListener(any<SensorEventListener>()) }
    }
}
