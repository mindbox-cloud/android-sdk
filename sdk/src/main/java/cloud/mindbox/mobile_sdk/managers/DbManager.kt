package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object DbManager {

    const val EVENTS_TABLE_NAME = "mindbox_events_table"
    const val CONFIGURATION_TABLE_NAME = "mindbox_configuration_table"

    private const val MAX_EVENT_LIST_SIZE = 10000
    private const val HALF_YEAR_IN_MILLISECONDS: Long = 15552000000L

    private lateinit var mindboxDb: MindboxDatabase

    fun init(context: Context) = LoggingExceptionHandler.runCatching {
        if (!this::mindboxDb.isInitialized) {
            mindboxDb = MindboxDatabase.getInstance(context)
        }
    }

    fun addEventToQueue(context: Context, event: Event) = LoggingExceptionHandler.runCatching {
        try {
            mindboxDb.eventsDao().insert(event)
            MindboxLoggerImpl.d(this, "Event ${event.eventType.operation} was added to queue")
        } catch (exception: RuntimeException) {
            MindboxLoggerImpl.e(
                this,
                "Error writing object to the database: ${event.body}",
                exception
            )
        }

        BackgroundWorkManager.startOneTimeService(context)
    }
    fun setNotSending(event: Event) = runCatching {
        mindboxDb.eventsDao().update(event.copy(isSending = false))
    }.logOnException()

    fun getFilteredEvents(): List<Event> = LoggingExceptionHandler.runCatching(
        defaultValue = listOf()
    ) {
        val events = getEvents().sortedBy(Event::enqueueTimestamp)
        val resultEvents = filterEvents(events)

        if (events.size > resultEvents.size) {
            CoroutineScope(Dispatchers.IO).launch { removeEventsFromQueue(events - resultEvents) }
        }

        val time = System.currentTimeMillis()
        resultEvents.filter { !it.isSending || time - it.enqueueTimestamp > 120_000 }
    }

    fun removeEventFromQueue(event: Event) = LoggingExceptionHandler.runCatching {
        try {
            synchronized(this) { mindboxDb.eventsDao().delete(event.transactionId) }
            MindboxLoggerImpl.d(
                this,
                "Event ${event.eventType};${event.transactionId} was deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLoggerImpl.e(this, "Error deleting item from database", exception)
        }
    }

    private fun removeEventsFromQueue(events: List<Event>) = LoggingExceptionHandler.runCatching {
        try {
            synchronized(this) { mindboxDb.eventsDao().deleteEvents(events) }
            MindboxLoggerImpl.d(
                this,
                "${events.size} events were deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLoggerImpl.e(this, "Error deleting items from database", exception)
        }
    }

    fun saveConfigurations(configuration: Configuration) = LoggingExceptionHandler.runCatching {
        try {
            mindboxDb.configurationDao().insert(configuration)
        } catch (exception: RuntimeException) {
            MindboxLoggerImpl.e(
                this,
                "Error writing object configuration to the database",
                exception
            )
        }
    }

    fun getConfigurations(): Configuration? = LoggingExceptionHandler.runCatching(
        defaultValue = null
    ) {
        try {
            mindboxDb.configurationDao().get()
        } catch (exception: RuntimeException) {
            // invalid data in case of exception
            MindboxLoggerImpl.e(this, "Error reading from database", exception)
            null
        }
    }

    private fun getEvents(): List<Event> = LoggingExceptionHandler.runCatching(
        defaultValue = listOf()
    ) {
        synchronized(this) { mindboxDb.eventsDao().getAll() }
    }

    private fun filterEvents(events: List<Event>): List<Event> {
        val time = System.currentTimeMillis()
        val filteredEvents = events.filterNot { it.isTooOld(time) }

        return filteredEvents.takeLast(MAX_EVENT_LIST_SIZE)
    }

    private fun Event.isTooOld(timeNow: Long): Boolean = LoggingExceptionHandler.runCatching(
        defaultValue = false
    ) {
        timeNow - this.enqueueTimestamp >= HALF_YEAR_IN_MILLISECONDS
    }

}
