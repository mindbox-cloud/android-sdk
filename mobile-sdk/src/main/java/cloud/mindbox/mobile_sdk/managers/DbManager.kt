package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.Event
import io.paperdb.Paper
import io.paperdb.PaperDbException

//todo sync it
//todo save configs to hawk
internal object DbManager {

    private const val CONFIGURATION_KEY = "configuration_key"
    private const val EVENTS_BOOK_NAME = "mindbox_events_book"
    private const val CONFIGURATION_BOOK_NAME = "mindbox_configuration_book"

    private val eventsBook = Paper.book(EVENTS_BOOK_NAME)
    private val configurationBook = Paper.book(CONFIGURATION_BOOK_NAME)

    fun addEventToStack(event: Event) {
        try {
            eventsBook.write(event.transactionId, event)
        } catch (exception: PaperDbException) {
            Logger.e(this, "Error writing object ${event.transactionId} to the database", exception)
        }
    }

    fun getEventsStack(): List<Event> {
        val list = arrayListOf<Event>()
        val keys = eventsBook.allKeys

        for (key in keys) {
            val value = getEvent(key) ?: continue
            list.add(value)
        }

        return list
    }

    private fun getEvent(key: String): Event? {
        return try {
            eventsBook.read(key) as Event?
        } catch (exception: PaperDbException) {

            // invalid data in case of exception
            removeEventFromStack(key)
            Logger.e(this, "Error reading from database", exception)
            null
        }
    }

    fun removeEventFromStack(key: String) {
        try {
            eventsBook.delete(key)
        } catch (exception: PaperDbException) {
            Logger.e(this, "Error deleting item from database", exception)
        }
    }
}