package cloud.mindbox.mobile_sdk_core.managers

import android.content.Context
import cloud.mindbox.mobile_sdk_core.logOnException
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import cloud.mindbox.mobile_sdk_core.models.Configuration
import cloud.mindbox.mobile_sdk_core.models.Event
import cloud.mindbox.mobile_sdk_core.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk_core.returnOnException
import cloud.mindbox.mobile_sdk_core.services.BackgroundWorkManager
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
            MindboxLoggerInternal.d(this, "Event ${event.eventType.operation} was added to queue")
        } catch (exception: RuntimeException) {
            MindboxLoggerInternal.e(
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
            MindboxLoggerInternal.d(
                this,
                "Event ${event.eventType};${event.transactionId} was deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLoggerInternal.e(this, "Error deleting item from database", exception)
        }
    }.logOnException()

    private fun removeEventsFromQueue(events: List<Event>) = runCatching {
        try {
            synchronized(this) { mindboxDb.eventsDao().deleteEvents(events) }
            MindboxLoggerInternal.d(
                this,
                "${events.size} events were deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLoggerInternal.e(this, "Error deleting items from database", exception)
        }
    }.logOnException()

    fun saveConfigurations(configuration: Configuration) = runCatching {
        try {
            mindboxDb.configurationDao().insert(configuration)
        } catch (exception: RuntimeException) {
            MindboxLoggerInternal.e(
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
            MindboxLoggerInternal.e(this, "Error reading from database", exception)
            null
        }
    }.returnOnException { null }

    private fun getEvents(): List<Event> = runCatching {
        synchronized(this) { mindboxDb.eventsDao().getAll() }
    }.returnOnException { emptyList() }

    private fun filterEvents(events: List<Event>): List<Event> {
        val time = System.currentTimeMillis()
        val filteredEvents = events.filterNot { it.isTooOld(time) }

        return filteredEvents.takeLast(MAX_EVENT_LIST_SIZE)
    }

    private fun Event.isTooOld(timeNow: Long): Boolean = runCatching {
        timeNow - this.enqueueTimestamp >= HALF_YEAR_IN_MILLISECONDS
    }.returnOnException { false }

}
