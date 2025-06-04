package cloud.mindbox.mobile_sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarUtilsTest {

    @Test
    fun `getDayBounds returns correct bounds for middle of day`() {
        // June 4, 2024, 13:50:45.123 GMT (16:50:45.123 UTC+3)
        val timestamp = 1717509045123L
        val (startOfDay, endOfDay) = getDayBounds(timestamp)

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L
        // June 4, 2024, 21:00:00.000 GMT (June 5, 2024, 00:00:00.000 UTC+3)
        val expectedEndOfDay = 1717534800000L

        assertEquals(expectedStartOfDay, startOfDay)
        assertEquals(expectedEndOfDay, endOfDay)
    }

    @Test
    fun `getDayBounds returns correct bounds for start of day`() {
        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val timestamp = 1717448400000L
        val (startOfDay, endOfDay) = getDayBounds(timestamp)

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L
        // June 4, 2024, 21:00:00.000 GMT (June 5, 2024, 00:00:00.000 UTC+3)
        val expectedEndOfDay = 1717534800000L

        assertEquals(expectedStartOfDay, startOfDay)
        assertEquals(expectedEndOfDay, endOfDay)
    }

    @Test
    fun `getDayBounds returns correct bounds for end of day`() {
        // June 4, 2024, 20:59:59.999 GMT (June 4, 2024, 23:59:59.999 UTC+3)
        val timestamp = 1717534799999L
        val (startOfDay, endOfDay) = getDayBounds(timestamp)

        // June 3, 2024, 21:00:00.000 GMT (June 4, 2024, 00:00:00.000 UTC+3)
        val expectedStartOfDay = 1717448400000L
        // June 4, 2024, 21:00:00.000 GMT (June 5, 2024, 00:00:00.000 UTC+3)
        val expectedEndOfDay = 1717534800000L

        assertEquals(expectedStartOfDay, startOfDay)
        assertEquals(expectedEndOfDay, endOfDay)
    }
}
