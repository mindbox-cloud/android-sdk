package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventParameters
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.gson.Gson

internal object EventManager {

    private val gson = Gson()

    fun appInstalled(context: Context, initData: InitData) {
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.APP_INSTALLED,
                additionalFields = null,
                body = gson.toJson(initData)
            )
        )
    }

    fun appInfoUpdate(context: Context, initData: UpdateData) {
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.APP_INFO_UPDATED,
                additionalFields = null,
                body = gson.toJson(initData)
            )
        )
    }

    fun pushDelivered(context: Context, uniqKey: String) {
        val fields = hashMapOf(
            EventParameters.UNIQ_KEY.fieldName to uniqKey
        )
        DbManager.addEventToQueue(
            context, Event(
                eventType = EventType.PUSH_DELIVERED,
                additionalFields = fields,
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