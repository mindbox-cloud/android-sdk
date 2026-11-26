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
    fun `schedule is chosen by the provider and the Android version`() {
        val expected = mapOf(
            ("FCM" to ANDROID_12) to PushDeliverySchedule.FCM,
            ("FCM" to ANDROID_11) to PushDeliverySchedule.DEFAULT,
            ("HCM" to ANDROID_12) to PushDeliverySchedule.HMS,
            ("HCM" to ANDROID_11) to PushDeliverySchedule.HMS,
            ("RuStore" to ANDROID_12) to PushDeliverySchedule.DEFAULT,
            ("RuStore" to ANDROID_11) to PushDeliverySchedule.DEFAULT,
            (null to ANDROID_12) to PushDeliverySchedule.DEFAULT,
            ("CUSTOM_PROVIDER" to ANDROID_11) to PushDeliverySchedule.DEFAULT,
        )

        expected.forEach { (input, schedule) ->
            val (provider, sdkInt) = input
            assertEquals("$input", schedule, PushDeliverySchedule.of(provider, sdkInt))
        }
        assertEquals(PushDeliverySchedule.values().toSet(), expected.values.toSet())
    }

    @Test
    fun `only FCM on Android 12 gets the attempt at 15 seconds`() {
        assertEquals(listOf(0L, 3_000L, 6_000L, 15_000L), PushDeliverySchedule.FCM.offsetsMs())
        assertEquals(
            listOf(PushDeliverySchedule.FCM),
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
        assertEquals(3_000L, PushDeliverySchedule.FCM.interval.interval)
        assertEquals(2_000L, PushDeliverySchedule.HMS.interval.interval)
        assertEquals(3_000L, PushDeliverySchedule.DEFAULT.interval.interval)
    }
}
