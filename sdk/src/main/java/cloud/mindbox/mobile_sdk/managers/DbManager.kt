package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.MindboxLogger
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.returnOnException
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import io.paperdb.Paper
import io.paperdb.PaperDbException
import java.util.*
import kotlin.collections.ArrayList

internal object DbManager {

    private const val CONFIGURATION_KEY = "configuration_key"
    private const val EVENTS_BOOK_NAME = "mindbox_events_book"
    private const val CONFIGURATION_BOOK_NAME = "mindbox_configuration_book"

    private const val MAX_EVENT_LIST_SIZE = 10000
    private const val HALF_YEAR_IN_MILLISECONDS: Long = 15552000000L

    private val eventsBook = Paper.book(EVENTS_BOOK_NAME)
    private val configurationBook = Paper.book(CONFIGURATION_BOOK_NAME)

    fun addEventToQueue(context: Context, event: Event) {
        runCatching {
            try {
                eventsBook.write("${event.enqueueTimestamp};${event.transactionId}", event)
                MindboxLogger.d(this, "Event ${event.eventType.operation} was added to queue")
            } catch (exception: PaperDbException) {
                MindboxLogger.e(
                    this,
                    "Error writing object to the database: ${event.body}",
                    exception
                )
            }

            BackgroundWorkManager.startOneTimeService(context)
        }.logOnException()
    }

    fun getFilteredEventsKeys(): List<String> = runCatching {
        return sortKeys(getEventsKeys())
            .filterOldEvents()
            .filterEventsBySize()
            .toList()
    }.returnOnException { emptyList() }

    private fun getEventsKeys(): List<String> {
        return runCatching {
            return eventsBook.allKeys
        }.returnOnException { emptyList() }
    }

    fun getEvent(key: String): Event? {
        return runCatching {
            try {
                return eventsBook.read(key) as Event?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                removeEventFromQueue(key)
                MindboxLogger.e(this, "Error reading from database", exception)
                return null
            }
        }.returnOnException { null }
    }

    fun removeEventFromQueue(key: String) {
        runCatching {
            try {
                eventsBook.delete(key)
                MindboxLogger.d(this, "Event $key was deleted from queue")
            } catch (exception: PaperDbException) {
                MindboxLogger.e(this, "Error deleting item from database", exception)
            }
        }.logOnException()
    }

    private fun sortKeys(list: List<String>): ArrayList<String> {
        val arrayList = ArrayList<String>()
        runCatching {
            arrayList.addAll(list)

            arrayList.sortBy { key ->
                val keyTimeStamp = key.substringBefore(";")
                try {
                    keyTimeStamp.toLong()
                } catch (e: NumberFormatException) {
                    0L
                }
            }
        }.logOnException()
        return arrayList
    }

    private fun ArrayList<String>.filterEventsBySize(): ArrayList<String> {
        return runCatching {
            val filteredList = ArrayList(this) //coping of list
            val diff = this.size - MAX_EVENT_LIST_SIZE

            if (diff > 0) { // allKeys.size >= MAX_EVENT_LIST_SIZE
                for (i in 1..diff) {
                    removeEventFromQueue(this[i])
                    filteredList.remove(this[i])
                }
            }
            return filteredList
        }.returnOnException { arrayListOf() }
    }

    private fun ArrayList<String>.filterOldEvents(): ArrayList<String> {
        return runCatching {
            val filteredList = ArrayList(this) //coping of list
            this.forEach { key ->
                if (key.isTooOldKey()) {
                    removeEventFromQueue(key)
                    filteredList.remove(key)
                } else {
                    return@forEach
                }
            }
            return filteredList
        }.returnOnException { arrayListOf() }
    }

    private fun String.isTooOldKey(): Boolean {
        return runCatching {
            val keyTimeStamp = this.substringBefore(";", "0")

            val enqueueTimestamp = try {
                keyTimeStamp.toLong()
            } catch (e: NumberFormatException) {
                0L
            }

            return enqueueTimestamp - Date().time >= HALF_YEAR_IN_MILLISECONDS
        }.returnOnException { false }
    }

    fun saveConfigurations(configuration: MindboxConfiguration) {
        runCatching {
            try {
                configurationBook.write(CONFIGURATION_KEY, configuration)
            } catch (exception: PaperDbException) {
                MindboxLogger.e(
                    this,
                    "Error writing object configuration to the database",
                    exception
                )
            }
        }.returnOnException { }
    }

    fun getConfigurations(): MindboxConfiguration? {
        return runCatching {
            try {
                return configurationBook.read(CONFIGURATION_KEY) as MindboxConfiguration?
            } catch (exception: PaperDbException) {

                // invalid data in case of exception
                MindboxLogger.e(this, "Error reading from database", exception)
                return null
            }
        }.returnOnException { null }
    }

    internal fun removeConfiguration() {
        try {
            configurationBook.destroy()
        } catch (exception: PaperDbException) {
            exception.printStackTrace()
        }
    }
}