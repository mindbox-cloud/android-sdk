package cloud.mindbox.mobile_sdk.converters

import cloud.mindbox.mobile_sdk.models.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

// Events queued by earlier SDK versions sit in the database in this format and must still be readable.
class MindboxRoomConverterTest {

    @Test
    fun eventTypeToString_writesOrdinalAndJsonNames() {
        val stored = MindboxRoomConverter.eventTypeToString(EventType.SyncOperation("Website.Operation"))

        assertEquals("""6;{"operation":"Website.Operation","endpoint":"/v3/operations/sync"}""", stored)
    }

    @Test
    fun stringToEventType_readsStoredOperation() {
        val eventType = MindboxRoomConverter.stringToEventType(
            """5;{"operation":"Website.Operation","endpoint":"/v3/operations/async"}"""
        )

        assertEquals(EventType.AsyncOperation::class, eventType::class)
        assertEquals("Website.Operation", eventType.operation)
        assertEquals("/v3/operations/async", eventType.endpoint)
    }

    @Test
    fun stringToEventType_readsStoredDataObject() {
        val eventType = MindboxRoomConverter.stringToEventType(
            """0;{"operation":"MobilePush.ApplicationInstalled","endpoint":"/v3/operations/async"}"""
        )

        assertEquals(EventType.AppInstalled, eventType)
    }

    @Test
    fun trackingEventTypes_surviveTheRoundTrip() {
        val idsUpdated = MindboxRoomConverter.eventTypeToString(EventType.AppTrackingIdsUpdated)
        val keepalive = MindboxRoomConverter.eventTypeToString(EventType.AppTrackingKeepalive)

        assertEquals("""9;{"operation":"MobilePush.ApplicationInfoUpdated","endpoint":"/v3/operations/async"}""", idsUpdated)
        assertEquals("""10;{"operation":"MobilePush.ApplicationKeepalive","endpoint":"/v3/operations/async"}""", keepalive)
        assertEquals(EventType.AppTrackingIdsUpdated, MindboxRoomConverter.stringToEventType(idsUpdated))
        assertEquals(EventType.AppTrackingKeepalive, MindboxRoomConverter.stringToEventType(keepalive))
    }
}
