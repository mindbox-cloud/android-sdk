package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.hardware.SensorManager
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.DevicePosition
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MotionServiceResolvePositionTest {

    private lateinit var motionService: MotionService

    private val enterThreshold = 0.8f * SensorManager.GRAVITY_EARTH
    private val exitThreshold = 0.6f * SensorManager.GRAVITY_EARTH

    @Before
    fun setUp() {
        val mockContext: Context = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockk<SensorManager>(relaxed = true)
        every { mockContext.resources } returns mockk(relaxed = true)
        motionService = MotionService(context = mockContext)
    }

    @Test
    fun `resolvePosition returns faceUp when z is strongly negative and no current position`() {
        val inputZ = -enterThreshold - 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = null,
        )
        assertEquals(DevicePosition.FACE_UP, actualPosition)
    }

    @Test
    fun `resolvePosition returns faceDown when z is strongly positive and no current position`() {
        val inputZ = enterThreshold + 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = null,
        )
        assertEquals(DevicePosition.FACE_DOWN, actualPosition)
    }

    @Test
    fun `resolvePosition returns portrait when y is strongly negative and no current position`() {
        val inputY = -enterThreshold - 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = 0f,
            current = null,
        )
        assertEquals(DevicePosition.PORTRAIT, actualPosition)
    }

    @Test
    fun `resolvePosition returns portraitUpsideDown when y is strongly positive and no current position`() {
        val inputY = enterThreshold + 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = 0f,
            current = null,
        )
        assertEquals(DevicePosition.PORTRAIT_UPSIDE_DOWN, actualPosition)
    }

    @Test
    fun `resolvePosition returns landscapeLeft when x is strongly negative and no current position`() {
        val inputX = -enterThreshold - 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = inputX,
            y = 0f,
            z = 0f,
            current = null,
        )
        assertEquals(DevicePosition.LANDSCAPE_LEFT, actualPosition)
    }

    @Test
    fun `resolvePosition returns landscapeRight when x is strongly positive and no current position`() {
        val inputX = enterThreshold + 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = inputX,
            y = 0f,
            z = 0f,
            current = null,
        )
        assertEquals(DevicePosition.LANDSCAPE_RIGHT, actualPosition)
    }

    @Test
    fun `resolvePosition returns null when all axes are below enter threshold and no current position`() {
        val inputValue = enterThreshold - 0.1f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = -inputValue,
            current = null,
        )
        assertNull(actualPosition)
    }

    @Test
    fun `resolvePosition returns null when all axes are zero and no current position`() {
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = 0f,
            current = null,
        )
        assertNull(actualPosition)
    }

    @Test
    fun `resolvePosition retains current faceUp when z is above exit threshold`() {
        val inputZ = -(exitThreshold + 0.1f)
        val inputCurrentPosition = DevicePosition.FACE_UP
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = inputCurrentPosition,
        )
        assertEquals(DevicePosition.FACE_UP, actualPosition)
    }

    @Test
    fun `resolvePosition retains current portrait when y is above exit threshold`() {
        val inputY = -(exitThreshold + 0.1f)
        val inputCurrentPosition = DevicePosition.PORTRAIT
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = 0f,
            current = inputCurrentPosition,
        )
        assertEquals(DevicePosition.PORTRAIT, actualPosition)
    }

    @Test
    fun `resolvePosition retains current landscapeLeft when x is above exit threshold`() {
        val inputX = -(exitThreshold + 0.1f)
        val inputCurrentPosition = DevicePosition.LANDSCAPE_LEFT
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = inputX,
            y = 0f,
            z = 0f,
            current = inputCurrentPosition,
        )
        assertEquals(DevicePosition.LANDSCAPE_LEFT, actualPosition)
    }

    @Test
    fun `resolvePosition drops current faceUp when z falls below exit threshold and switches to portrait`() {
        val inputZ = -(exitThreshold - 0.1f)
        val inputY = -(enterThreshold + 0.5f)
        val inputCurrentPosition = DevicePosition.FACE_UP
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = inputZ,
            current = inputCurrentPosition,
        )
        assertEquals(DevicePosition.PORTRAIT, actualPosition)
    }

    @Test
    fun `resolvePosition returns null when current position is lost and no axis exceeds enter threshold`() {
        val inputZ = -(exitThreshold - 0.1f)
        val inputCurrentPosition = DevicePosition.FACE_UP
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = inputCurrentPosition,
        )
        assertNull(actualPosition)
    }

    @Test
    fun `resolvePosition picks dominant axis when multiple axes exceed enter threshold`() {
        val inputZ = -(enterThreshold + 0.1f)
        val inputY = -(enterThreshold + 2.0f)
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = inputZ,
            current = null,
        )
        assertEquals(DevicePosition.PORTRAIT, actualPosition)
    }

    @Test
    fun `resolvePosition picks z axis when z magnitude exceeds y magnitude`() {
        val inputZ = -(enterThreshold + 1.0f)
        val inputY = -(enterThreshold + 0.1f)
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = inputZ,
            current = null,
        )
        assertEquals(DevicePosition.FACE_UP, actualPosition)
    }

    @Test
    fun `resolvePosition returns null when z is exactly at enter threshold`() {
        val inputZ = -enterThreshold
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = null,
        )
        assertNull(actualPosition)
    }

    @Test
    fun `resolvePosition transitions from faceUp to faceDown when z flips to positive`() {
        val inputZ = enterThreshold + 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputZ,
            current = DevicePosition.FACE_UP,
        )
        assertEquals(DevicePosition.FACE_DOWN, actualPosition)
    }

    @Test
    fun `resolvePosition transitions from portrait to portraitUpsideDown when y flips to positive`() {
        val inputY = enterThreshold + 0.5f
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = 0f,
            current = DevicePosition.PORTRAIT,
        )
        assertEquals(DevicePosition.PORTRAIT_UPSIDE_DOWN, actualPosition)
    }

    @Test
    fun `resolvePosition handles multi-step transition from portrait through faceUp to faceDown`() {
        val inputStrongZ = -(enterThreshold + 0.5f)
        val step1ActualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = inputStrongZ,
            current = DevicePosition.PORTRAIT,
        )
        assertEquals(DevicePosition.FACE_UP, step1ActualPosition)

        val step2ActualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = 0f,
            z = enterThreshold + 0.5f,
            current = DevicePosition.FACE_UP,
        )
        assertEquals(DevicePosition.FACE_DOWN, step2ActualPosition)
    }

    @Test
    fun `resolvePosition retains portrait when y is above exit threshold even though z is below enter threshold`() {
        val inputY = -(exitThreshold + 0.5f)
        val inputZ = -(enterThreshold - 1.0f)
        val actualPosition: DevicePosition? = motionService.resolvePosition(
            x = 0f,
            y = inputY,
            z = inputZ,
            current = DevicePosition.PORTRAIT,
        )
        assertEquals(DevicePosition.PORTRAIT, actualPosition)
    }
}
