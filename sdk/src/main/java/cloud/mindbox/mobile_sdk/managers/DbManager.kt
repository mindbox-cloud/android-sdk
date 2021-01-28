package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.Event
import io.paperdb.Paper
import io.paperdb.PaperDbException
import java.util.*

internal object DbManager {

    private const val CONFIGURATION_KEY = "configuration_key"
    private const val EVENTS_BOOK_NAME = "mindbox_events_book"
    private const val CONFIGURATION_BOOK_NAME = "mindbox_configuration_book"

    private val eventsBook = Paper.book(EVENTS_BOOK_NAME)
    private val configurationBook = Paper.book(CONFIGURATION_BOOK_NAME)

    fun addEventToQueue(event: Event) {
        synchronized(this) {
            try {
                eventsBook.write(event.transactionId, event)
            } catch (exception: PaperDbException) {
                Logger.e(
                    this,
                    "Error writing object to the database: ${event.body}",
                    exception
                )
            }
        }
    }

    fun getEventsQueue(): List<Event> {
        synchronized(this) {
            val list = arrayListOf<Event>()
            val keys = eventsBook.allKeys

            for (key in keys) {
                val value = getEvent(key) ?: continue
                list.add(value)
            }

            return filterOldEvents(list)
        }
    }

    private fun getEvent(key: String): Event? {
        synchronized(this) {
            return try {
                eventsBook.read(key) as Event?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                removeEventFromQueue(key)
                Logger.e(this, "Error reading from database", exception)
                null
            }
        }
    }

    fun removeEventFromQueue(key: String) {
        synchronized(this) {
            try {
                eventsBook.delete(key)
            } catch (exception: PaperDbException) {
                Logger.e(this, "Error deleting item from database", exception)
            }
        }
    }

    private fun filterOldEvents(list: ArrayList<Event>): List<Event> {
        synchronized(this) {
            var filteredList = list.toList()

            // filter by volume
            if (list.size > 10000) {
                for (i in 1..(list.size - 10000)) {
                    removeEventFromQueue(list[i].transactionId)
                }
                filteredList = list.filterIndexed { index, _ -> index > (list.size - 10000) }
            }

            //todo
            // filter by time
//        val now = Date().time
//
//        filteredList.forEach { event ->
//            val date: LocalDate = ofEpochMilli(event.enqueueTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
//            if (event.enqueueTimestamp / 1000 * 60 * 60 ==)
//        }
            return filteredList
        }
    }

    fun saveConfigurations(configuration: Configuration) {
        synchronized(this) {
            try {
                configurationBook.write(CONFIGURATION_KEY, configuration)
            } catch (exception: PaperDbException) {
                Logger.e(this, "Error writing object configuration to the database", exception)
            }
        }
    }

    fun getConfigurations(): Configuration? {
        synchronized(this) {
            return try {
                configurationBook.read(CONFIGURATION_KEY) as Configuration?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                Logger.e(this, "Error reading from database", exception)
                null
            }
        }
    }
}