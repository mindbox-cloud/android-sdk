package cloud.mindbox.mobile_sdk.inapp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppTtlTest {

    @Test
    fun `toMillis converts time units correctly`() {
        assertEquals(1000L, InAppTtl.SECONDS.toMillis(1))
        assertEquals(60000L, InAppTtl.MINUTES.toMillis(1))
        assertEquals(3600000L, InAppTtl.HOURS.toMillis(1))
        assertEquals(86400000L, InAppTtl.DAYS.toMillis(1))
    }
}