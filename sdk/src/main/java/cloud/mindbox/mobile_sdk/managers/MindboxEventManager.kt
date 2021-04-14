package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventParameters
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.gson.Gson

internal object MindboxEventManager {

    private val gson = Gson()

    fun appInstalled(context: Context, initData: InitData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.APP_INSTALLED,
                    body = gson.toJson(initData)
                )
            )
        }.logOnException()
    }

    fun appInfoUpdate(context: Context, initData: UpdateData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.APP_INFO_UPDATED,
                    body = gson.toJson(initData)
                )
            )
        }.logOnException()
    }

    fun pushDelivered(context: Context, uniqKey: String) {
        runCatching {
            val fields = hashMapOf(
                EventParameters.UNIQ_KEY.fieldName to uniqKey
            )
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.PUSH_DELIVERED,
                    additionalFields = fields
                )
            )
        }.logOnException()
    }

    fun pushClicked(context: Context, clickData: TrackClickData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.PUSH_CLICKED,
                    body = gson.toJson(clickData)
                )
            )
        }.logOnException()
    }

    fun appStarted(context: Context, trackVisitData: TrackVisitData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.TRACK_VISIT,
                    body = gson.toJson(trackVisitData)
                )
            )
        }.logOnException()
    }

    fun sendEventsIfExist(context: Context) {
        runCatching {
            val keys = DbManager.getFilteredEventsKeys()

            if (keys.isNotEmpty()) {
                BackgroundWorkManager.startOneTimeService(context)
            }
        }.logOnException()
    }
}