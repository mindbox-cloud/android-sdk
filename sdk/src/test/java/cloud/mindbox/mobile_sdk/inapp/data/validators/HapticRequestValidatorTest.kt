package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticImpactStyle
import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticPatternEvent
import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticRequestValidatorTest {

    private val validator: HapticRequestValidator = HapticRequestValidator()

    @Test
    fun `isValid returns true for Selection`() {
        val actualResult: Boolean = validator.isValid(HapticRequest.Selection)
        assertTrue(actualResult)
    }

    @Test
    fun `isValid returns true for Impact with any style`() {
        assertTrue(validator.isValid(HapticRequest.Impact(HapticImpactStyle.Light)))
        assertTrue(validator.isValid(HapticRequest.Impact(HapticImpactStyle.Medium)))
        assertTrue(validator.isValid(HapticRequest.Impact(HapticImpactStyle.Heavy)))
    }

    @Test
    fun `isValid returns false for Pattern with empty events`() {
        val request: HapticRequest = HapticRequest.Pattern(events = emptyList())
        val actualResult: Boolean = validator.isValid(request)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns false for Pattern with more than 128 events`() {
        val events: List<HapticPatternEvent> = List(129) { validEvent(time = it * 100L) }
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        val actualResult: Boolean = validator.isValid(request)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns true for Pattern with exactly 128 valid events`() {
        val events: List<HapticPatternEvent> = List(128) { validEvent(time = it * 200L) }
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        val actualResult: Boolean = validator.isValid(request)
        assertTrue(actualResult)
    }

    @Test
    fun `isValid returns false when event time is negative`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(time = -1L))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when event time exceeds 30000`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(time = 30_001L))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns true when event time is 0`() {
        assertTrue(validator.isValid(HapticRequest.Pattern(listOf(validEvent(time = 0L)))))
    }

    @Test
    fun `isValid returns false when transient event starts at 30000 because effective duration exceeds limit`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(time = 30_000L, duration = 0L))
        assertFalse(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns false when event duration is negative`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(duration = -1L))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when event duration exceeds 5000`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(duration = 5_001L))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns true when event duration is at boundary 0 and 5000`() {
        assertTrue(validator.isValid(HapticRequest.Pattern(listOf(validEvent(duration = 0L)))))
        assertTrue(validator.isValid(HapticRequest.Pattern(listOf(validEvent(duration = 5_000L)))))
    }

    @Test
    fun `isValid returns false when event intensity is below 0`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(intensity = -0.1f))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when event intensity exceeds 1`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(intensity = 1.1f))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns true when event intensity is at boundary 0 and 1`() {
        assertTrue(validator.isValid(HapticRequest.Pattern(listOf(validEvent(intensity = 0f)))))
        assertTrue(validator.isValid(HapticRequest.Pattern(listOf(validEvent(intensity = 1f)))))
    }

    @Test
    fun `isValid returns false when event sharpness is below 0`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(sharpness = -0.1f))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when event sharpness exceeds 1`() {
        val events: List<HapticPatternEvent> = listOf(validEvent(sharpness = 1.1f))
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when event time plus duration exceeds 30000`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 28_000L, duration = 2_001L),
        )
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns true when event time plus duration equals 30000`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 25_000L, duration = 5_000L),
        )
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertTrue(validator.isValid(request))
    }

    @Test
    fun `isValid returns true for single valid pattern event`() {
        val events: List<HapticPatternEvent> = listOf(validEvent())
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertTrue(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when any event in pattern is invalid`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L),
            validEvent(time = 1000L, duration = 10_000L),
        )
        val request: HapticRequest = HapticRequest.Pattern(events = events)
        assertFalse(validator.isValid(request))
    }

    @Test
    fun `isValid returns false when events overlap`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 200L),
            validEvent(time = 100L, duration = 100L),
        )
        assertFalse(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns false when events have same time`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 100L),
            validEvent(time = 0L, duration = 100L),
        )
        assertFalse(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns true when events are adjacent without overlap`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 100L),
            validEvent(time = 100L, duration = 100L),
        )
        assertTrue(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns true when events have gap between them`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 100L),
            validEvent(time = 300L, duration = 100L),
        )
        assertTrue(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns false when unsorted events overlap after sorting`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 100L, duration = 100L),
            validEvent(time = 0L, duration = 200L),
        )
        assertFalse(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns false when transient event overlaps next event`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 0L),
            validEvent(time = 5L, duration = 100L),
        )
        assertFalse(validator.isValid(HapticRequest.Pattern(events)))
    }

    @Test
    fun `isValid returns true when transient event ends exactly when next event starts`() {
        val events: List<HapticPatternEvent> = listOf(
            validEvent(time = 0L, duration = 0L),
            validEvent(time = 10L, duration = 100L),
        )
        assertTrue(validator.isValid(HapticRequest.Pattern(events)))
    }

    private fun validEvent(
        time: Long = 0L,
        duration: Long = 100L,
        intensity: Float = 1f,
        sharpness: Float = 0f,
    ): HapticPatternEvent = HapticPatternEvent(
        time = time,
        duration = duration,
        intensity = intensity,
        sharpness = sharpness,
    )
}
