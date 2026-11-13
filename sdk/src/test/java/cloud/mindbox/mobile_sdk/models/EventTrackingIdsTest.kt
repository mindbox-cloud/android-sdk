package cloud.mindbox.mobile_sdk.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTrackingIdsTest {

    private val idsBody = """{"instanceId":"i","version":3,"tokens":[],"ids":[{"type":"google","value":"g"}]}"""

    @Test
    fun `the tracking events are the only ones bound to the tracking host`() {
        val tracking: List<EventType> = listOf(EventType.AppTrackingIdsUpdated, EventType.AppTrackingKeepalive)
        val ordinary: List<EventType> = listOf(
            EventType.AppInfoUpdated,
            EventType.AppKeepalive,
            EventType.AppInstalled,
            EventType.AsyncOperation("Custom.Op"),
        )

        assertTrue(tracking.all { it is TrackingHostEvent })
        assertFalse(ordinary.any { it is TrackingHostEvent })
    }

    @Test
    fun `the tracking events use the operation names of their main host counterparts`() {
        assertEquals(EventType.AppInfoUpdated, EventType.AppTrackingIdsUpdated.mainHostCounterpart)
        assertEquals(EventType.AppInfoUpdated.operation, EventType.AppTrackingIdsUpdated.operation)
        assertEquals(EventType.AppKeepalive, EventType.AppTrackingKeepalive.mainHostCounterpart)
        assertEquals(EventType.AppKeepalive.operation, EventType.AppTrackingKeepalive.operation)
    }

    @Test
    fun `withoutTrackingIds removes only the ids field`() {
        assertEquals("""{"instanceId":"i","version":3,"tokens":[]}""", idsBody.withoutTrackingIds())
    }

    @Test
    fun `withoutTrackingIds leaves an unparsable body untouched`() {
        assertEquals("not json", "not json".withoutTrackingIds())
    }

    @Test
    fun `withMaskedTrackingIds keeps only the first group of each identifier readable`() {
        val body = """{"instanceId":"i","ids":[""" +
            """{"type":"google","value":"38400000-8cf0-11bd-b23e-10b96e40000d"},""" +
            """{"type":"huawei","value":"1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"}]}"""

        assertEquals(
            """{"instanceId":"i","ids":[""" +
                """{"type":"google","value":"38400000-****-****-****-************"},""" +
                """{"type":"huawei","value":"1a2b3c4d-****-****-****-************"}]}""",
            body.withMaskedTrackingIds(),
        )
    }

    @Test
    fun `withMaskedTrackingIds leaves a body without identifiers untouched`() {
        assertEquals("""{"instanceId":"i"}""", """{"instanceId":"i"}""".withMaskedTrackingIds())
        assertEquals("""{"ids":[]}""", """{"ids":[]}""".withMaskedTrackingIds())
        assertEquals("not json", "not json".withMaskedTrackingIds())
    }
}
