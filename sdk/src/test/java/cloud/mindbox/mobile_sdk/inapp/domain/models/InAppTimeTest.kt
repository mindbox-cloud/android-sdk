package cloud.mindbox.mobile_sdk.inapp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppTimeTest {

    @Test
    fun `toMillis converts time units correctly`() {
        assertEquals(1000L, InAppTime.SECONDS.toMillis(1))
        assertEquals(60000L, InAppTime.MINUTES.toMillis(1))
        assertEquals(3600000L, InAppTime.HOURS.toMillis(1))
        assertEquals(86400000L, InAppTime.DAYS.toMillis(1))
    }
}