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
}
