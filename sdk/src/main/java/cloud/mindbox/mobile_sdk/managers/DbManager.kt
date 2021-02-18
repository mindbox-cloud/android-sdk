package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import io.paperdb.Paper
import io.paperdb.PaperDbException
import java.util.*

// todo add run catching

internal object DbManager {

    private const val CONFIGURATION_KEY = "configuration_key"
    private const val EVENTS_BOOK_NAME = "mindbox_events_book"
    private const val CONFIGURATION_BOOK_NAME = "mindbox_configuration_book"

    private const val MAX_EVENT_LIST_SIZE = 10000
    private const val HALF_YEAR_IN_MILLISECONDS: Long = 15552000000L

    private val eventsBook = Paper.book(EVENTS_BOOK_NAME)
    private val configurationBook = Paper.book(CONFIGURATION_BOOK_NAME)

    fun addEventToQueue(context: Context, event: Event) {
            try {
                eventsBook.write(event.transactionId, event)
                Logger.d(this, "Event ${event.eventType.operation} was added to queue")
            } catch (exception: PaperDbException) {
                Logger.e(
                    this,
                    "Error writing object to the database: ${event.body}",
                    exception
                )
            }

        BackgroundWorkManager.startOneTimeService(context)
    }

    fun getFilteredEventsKeys(): List<String> {
        filterEventsBySize()
        filterOldEvents()
        return getEventsKeys()
    }

    private fun getEventsKeys(): List<String>  {
        return eventsBook.allKeys
    }

    fun getEvent(key: String): Event? {
            return try {
                eventsBook.read(key) as Event?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                removeEventFromQueue(key)
                Logger.e(this, "Error reading from database", exception)
                null
            }
    }

    fun removeEventFromQueue(key: String) {
            try {
                eventsBook.delete(key)
            } catch (exception: PaperDbException) {
                Logger.e(this, "Error deleting item from database", exception)
            }
    }

    private fun filterEventsBySize() {
            val allKeys = getEventsKeys()
            val diff = allKeys.size - MAX_EVENT_LIST_SIZE

            if (diff > 0) { // allKeys.size >= MAX_EVENT_LIST_SIZE
                for (i in 1..diff) {
                    removeEventFromQueue(allKeys[i])
                }
            }
    }

    private fun filterOldEvents() {
            val keys = getEventsKeys()
            keys.forEach { key ->
                val event = getEvent(key)
                if (event?.isTooOld() == true) {
                    removeEventFromQueue(key)
                } else {
                    return@forEach
                }
            }
    }

    private fun Event.isTooOld(): Boolean =
        this.enqueueTimestamp - Date().time >= HALF_YEAR_IN_MILLISECONDS

    fun saveConfigurations(configuration: Configuration) {
            try {
                configurationBook.write(CONFIGURATION_KEY, configuration)
            } catch (exception: PaperDbException) {
                Logger.e(this, "Error writing object configuration to the database", exception)
            }
    }

    fun getConfigurations(): Configuration? {
            return try {
                configurationBook.read(CONFIGURATION_KEY) as Configuration?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                Logger.e(this, "Error reading from database", exception)
                null
            }
    }
}