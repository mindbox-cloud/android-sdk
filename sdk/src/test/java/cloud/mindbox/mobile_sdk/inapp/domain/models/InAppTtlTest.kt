package cloud.mindbox.mobile_sdk.inapp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppTtlTest {

    @Test
    fun `toMillis converts time units correctly`() {
        assertEquals(1000L, InAppTtl.SECONDS.toMillis(1))
        assertEquals(60000L, InAppTtl.MINUTES.toMillis(1))
        assertEquals(3600000L, InAppTtl.HOURS.toMillis(1))
        assertEquals(86400000L, InAppTtl.DAYS.toMillis(1))
    }

    @Test
    fun `fromString returns correct InAppTtl for valid strings`() {
        assertEquals(InAppTtl.SECONDS, InAppTtl.fromString("seconds"))
        assertEquals(InAppTtl.MINUTES, InAppTtl.fromString("MINUTES"))
        assertEquals(InAppTtl.HOURS, InAppTtl.fromString("HoUrs"))
        assertEquals(InAppTtl.DAYS, InAppTtl.fromString("days"))
    }

    @Test
    fun `fromString returns null for invalid strings`() {
        assertNull(InAppTtl.fromString("someName"))
    }
}