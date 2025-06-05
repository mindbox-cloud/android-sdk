package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class CalendarUtilsTest {

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    @Test
    fun `getDayStartTimestamp returns correct start for middle of day`() {
        // June 4, 2024, 13:50:45.123 GMT (16:50:45.123 UTC+3)
        val timestamp = 1717509045123L
        val startOfDay = getDayStartTimestamp(Timestamp(timestamp))

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L

        assertEquals(expectedStartOfDay, startOfDay.ms)
    }

    @Test
    fun `getDayStartTimestamp returns correct start for start of day`() {
        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val timestamp = 1717448400000L
        val startOfDay = getDayStartTimestamp(Timestamp(timestamp))

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L

        assertEquals(expectedStartOfDay, startOfDay.ms)
    }

    @Test
    fun `getDayStartTimestamp returns correct start for end of day`() {
        // June 4, 2024, 20:59:59.999 GMT (June 4, 2024, 23:59:59.999 UTC+3)
        val timestamp = 1717534799999L
        val startOfDay = getDayStartTimestamp(Timestamp(timestamp))

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L

        assertEquals(expectedStartOfDay, startOfDay.ms)
    }
}
