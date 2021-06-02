package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.util.Log
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk.returnOnException
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object DbManager {

    const val EVENTS_TABLE_NAME = "mindbox_events_table"
    const val CONFIGURATION_TABLE_NAME = "mindbox_configuration_table"

    private const val MAX_EVENT_LIST_SIZE = 10000
    private const val HALF_YEAR_IN_MILLISECONDS: Long = 15552000000L

    private lateinit var mindboxDb: MindboxDatabase

    fun init(context: Context) = runCatching {
        if (!this::mindboxDb.isInitialized) {
            mindboxDb = MindboxDatabase.getInstance(context)
        }
    }.logOnException()

    fun addEventToQueue(context: Context, event: Event) = runCatching {
        try {
            mindboxDb.eventsDao().insert(event)
            MindboxLogger.d(this, "Event ${event.eventType.operation} was added to queue")
        } catch (exception: RuntimeException) {
            MindboxLogger.e(
                this,
                "Error writing object to the database: ${event.body}",
                exception
            )
        }

        BackgroundWorkManager.startOneTimeService(context)
    }.logOnException()

    fun getFilteredEvents(): List<Event> = runCatching {
        val events = getEvents().sortedBy(Event::enqueueTimestamp)
        val resultEvents = filterEvents(events)

        if (events.size > resultEvents.size) {
            CoroutineScope(Dispatchers.IO).launch { removeEventsFromQueue(events - resultEvents) }
        }

        resultEvents
    }.returnOnException { emptyList() }

    fun removeEventFromQueue(event: Event) = runCatching {
        try {
            synchronized(this) { mindboxDb.eventsDao().delete(event) }
            MindboxLogger.d(
                this,
                "Event ${event.eventType};${event.transactionId} was deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLogger.e(this, "Error deleting item from database", exception)
        }
    }.logOnException()

    private fun removeEventsFromQueue(events: List<Event>) = runCatching {
        try {
            synchronized(this) { mindboxDb.eventsDao().deleteEvents(events) }
            MindboxLogger.d(
                this,
                "${events.size} events were deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLogger.e(this, "Error deleting items from database", exception)
        }
    }.logOnException()

    fun saveConfigurations(configuration: Configuration) = runCatching {
        try {
            mindboxDb.configurationDao().insert(configuration)
        } catch (exception: RuntimeException) {
            MindboxLogger.e(
                this,
                "Error writing object configuration to the database",
                exception
            )
        }
    }.returnOnException { }

    fun getConfigurations(): Configuration? = runCatching {
        try {
            mindboxDb.configurationDao().get()
        } catch (exception: RuntimeException) {
            // invalid data in case of exception
            MindboxLogger.e(this, "Error reading from database", exception)
            null
        }
    }.returnOnException { null }

    private fun getEvents(): List<Event> = runCatching {
        synchronized(this) { mindboxDb.eventsDao().getAll() }
    }.returnOnException { emptyList() }

    private fun filterEvents(events: List<Event>): List<Event> {
        val time = System.currentTimeMillis()
        val filteredEvents = events.filterNot { it.isTooOld(time) }

        return if (filteredEvents.size <= MAX_EVENT_LIST_SIZE) {
            filteredEvents
        } else {
            filteredEvents.subList(0, MAX_EVENT_LIST_SIZE)
        }
    }

    private fun Event.isTooOld(timeNow: Long): Boolean = runCatching {
        timeNow - this.enqueueTimestamp >= HALF_YEAR_IN_MILLISECONDS
    }.returnOnException { false }

}
