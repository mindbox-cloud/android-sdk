package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.gson.Gson
import java.util.*

internal object EventManager {

    private val gson = Gson()

    fun appInstalled(context: Context, initData: FullInitData) {
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.APP_INSTALLED,
                uniqKey = null,
                body = gson.toJson(initData)
            )
        )
    }

    fun appInfoUpdate(context: Context, initData: PartialInitData) {
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.APP_INFO_UPDATED,
                uniqKey = null,
                body = gson.toJson(initData)
            )
        )
    }

    fun pushDelivered(context: Context, uniqKey: String) {
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.PUSH_DELIVERED,
                uniqKey = uniqKey,
                body = null
            )
        )
    }

    fun sendEventsIfExist(context: Context) {
        val keys = DbManager.getFilteredEventsKeys()

        if (keys.isNotEmpty()) {
            BackgroundWorkManager.startOneTimeService(context)
        }
    }
}