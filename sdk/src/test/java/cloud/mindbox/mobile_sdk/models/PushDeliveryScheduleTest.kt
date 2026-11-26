package cloud.mindbox.mobile_sdk.models

import org.junit.Assert.assertEquals
import org.junit.Test

class PushDeliveryScheduleTest {

    private companion object {
        const val ANDROID_11 = 30
        const val ANDROID_12 = 31
    }

    private fun PushDeliverySchedule.offsetsMs(): List<Long> = offsets.map { it.interval }

    @Test
    fun `schedule is chosen by the provider, the priority and the Android version`() {
        val expected = mapOf(
            Triple("FCM", true, ANDROID_12) to PushDeliverySchedule.FCM_HIGH,
            Triple("FCM", true, ANDROID_11) to PushDeliverySchedule.DEFAULT,
            Triple("FCM", false, ANDROID_12) to PushDeliverySchedule.DEFAULT,
            Triple("FCM", false, ANDROID_11) to PushDeliverySchedule.DEFAULT,
            Triple("HCM", true, ANDROID_12) to PushDeliverySchedule.HMS,
            Triple("HCM", true, ANDROID_11) to PushDeliverySchedule.HMS,
            Triple("HCM", false, ANDROID_12) to PushDeliverySchedule.HMS,
            Triple("HCM", false, ANDROID_11) to PushDeliverySchedule.HMS,
            Triple("RuStore", true, ANDROID_12) to PushDeliverySchedule.DEFAULT,
            Triple("RuStore", false, ANDROID_11) to PushDeliverySchedule.DEFAULT,
            Triple(null, true, ANDROID_12) to PushDeliverySchedule.DEFAULT,
            Triple("CUSTOM_PROVIDER", false, ANDROID_11) to PushDeliverySchedule.DEFAULT,
        )

        expected.forEach { (input, schedule) ->
            val (provider, isHighPriority, sdkInt) = input
            assertEquals("$input", schedule, PushDeliverySchedule.of(provider, isHighPriority, sdkInt))
        }
        assertEquals(PushDeliverySchedule.values().toSet(), expected.values.toSet())
    }

    @Test
    fun `only the high FCM push on Android 12 gets the attempt at 15 seconds`() {
        assertEquals(listOf(0L, 3_000L, 6_000L, 15_000L), PushDeliverySchedule.FCM_HIGH.offsetsMs())
        assertEquals(
            listOf(PushDeliverySchedule.FCM_HIGH),
            PushDeliverySchedule.values().filter { it.offsets.size > 3 },
        )
    }

    @Test
    fun `HMS attempts go at 0, 2 and 4 seconds and the default ones at 0, 3 and 6 seconds`() {
        assertEquals(listOf(0L, 2_000L, 4_000L), PushDeliverySchedule.HMS.offsetsMs())
        assertEquals(listOf(0L, 3_000L, 6_000L), PushDeliverySchedule.DEFAULT.offsetsMs())
    }

    @Test
    fun `interval of a schedule is its shortest step`() {
        assertEquals(3_000L, PushDeliverySchedule.FCM_HIGH.interval.interval)
        assertEquals(2_000L, PushDeliverySchedule.HMS.interval.interval)
        assertEquals(3_000L, PushDeliverySchedule.DEFAULT.interval.interval)
    }
}
