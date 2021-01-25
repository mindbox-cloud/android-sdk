package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import io.paperdb.Paper

internal object DbManager {

    private const val EVENTS_KEY = "events_stack"

    fun addEventToStack(event: Event) {
        //todo get all events
        //todo rewrite it
        //todo sync it
        Paper.book().write(EVENTS_KEY, event)
    }

    fun getEventsStack() {

    }

    fun removeEventFromStack() {

    }

    fun setConfiguration(configuration: Configuration) {

    }

    fun getConfiguration() {

    }
}