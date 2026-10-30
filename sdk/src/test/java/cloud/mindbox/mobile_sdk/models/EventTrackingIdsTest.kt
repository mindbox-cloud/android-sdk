package cloud.mindbox.mobile_sdk.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTrackingIdsTest {

    private val idsBody = """{"instanceId":"i","version":3,"tokens":[],"ids":[{"type":"google","value":"g"}]}"""

    @Test
    fun `an application operation with a non-empty ids array carries tracking ids`() {
        assertTrue(Event(eventType = EventType.AppInfoUpdated, body = idsBody).carriesTrackingIds())
        assertTrue(Event(eventType = EventType.AppInstalled, body = idsBody).carriesTrackingIds())
        assertTrue(Event(eventType = EventType.AppInstalledWithoutCustomer, body = idsBody).carriesTrackingIds())
        assertTrue(Event(eventType = EventType.AppKeepalive, body = idsBody).carriesTrackingIds())
    }

    @Test
    fun `an empty or missing ids array does not carry tracking ids`() {
        assertFalse(Event(eventType = EventType.AppInfoUpdated, body = """{"ids":[]}""").carriesTrackingIds())
        assertFalse(Event(eventType = EventType.AppInfoUpdated, body = """{"instanceId":"i"}""").carriesTrackingIds())
        assertFalse(Event(eventType = EventType.AppInfoUpdated, body = null).carriesTrackingIds())
        assertFalse(Event(eventType = EventType.AppInfoUpdated, body = "not json").carriesTrackingIds())
    }

    @Test
    fun `a custom operation with an ids field is not a tracking ids event`() {
        assertFalse(Event(eventType = EventType.AsyncOperation("Custom.Op"), body = idsBody).carriesTrackingIds())
    }

    @Test
    fun `withoutTrackingIds removes only the ids field and keeps the identity of the event`() {
        val event = Event(uid = 7L, eventType = EventType.AppInfoUpdated, body = idsBody)

        val stripped = event.withoutTrackingIds()

        assertEquals("""{"instanceId":"i","version":3,"tokens":[]}""", stripped.body)
        assertEquals(event.transactionId, stripped.transactionId)
        assertEquals(event.uid, stripped.uid)
        assertFalse(stripped.carriesTrackingIds())
    }

    @Test
    fun `withoutTrackingIds leaves an unparsable body untouched`() {
        assertEquals("not json", Event(eventType = EventType.AppInfoUpdated, body = "not json").withoutTrackingIds().body)
    }
}
