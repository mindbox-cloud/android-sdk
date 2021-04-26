package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk.returnOnException
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager

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

    fun updateEventInQueue(event: Event) = runCatching {
        try {
            mindboxDb.eventsDao().update(event)
            MindboxLogger.d(this, "Event ${event.eventType.operation} was updated")
        } catch (exception: RuntimeException) {
            MindboxLogger.e(
                this,
                "Error updating object to the database: ${event.body}",
                exception
            )
        }
    }.logOnException()

    fun getFilteredEvents(): List<Event> = runCatching {
        getEvents()
            .sortedBy { event -> event.retryTimeStamp ?: event.enqueueTimestamp }
            .filterNot(::isOldEvent)
            .filterIndexed(::isIndexLessMax)
    }.returnOnException { emptyList() }

    fun removeEventFromQueue(event: Event) = runCatching {
        try {
            mindboxDb.eventsDao().delete(event)
            MindboxLogger.d(
                this,
                "Event ${event.eventType};${event.transactionId} was deleted from queue"
            )
        } catch (exception: RuntimeException) {
            MindboxLogger.e(this, "Error deleting item from database", exception)
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
        mindboxDb.eventsDao().getAll()
    }.returnOnException { emptyList() }

    private fun isIndexLessMax(index: Int, event: Event): Boolean = runCatching {
        if (index >= MAX_EVENT_LIST_SIZE) {
            removeEventFromQueue(event)
            false
        } else {
            true
        }
    }.returnOnException { false }

    private fun isOldEvent(event: Event): Boolean = runCatching {
        if (event.isTooOld(System.currentTimeMillis())) {
            removeEventFromQueue(event)
            true
        } else {
            false
        }
    }.returnOnException { true }

    private fun Event.isTooOld(timeNow: Long): Boolean = runCatching {
        this.enqueueTimestamp - timeNow >= HALF_YEAR_IN_MILLISECONDS
    }.returnOnException { false }

}
