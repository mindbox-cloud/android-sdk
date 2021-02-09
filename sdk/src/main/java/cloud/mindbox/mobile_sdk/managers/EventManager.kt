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
                UUID.randomUUID().toString(),
                -1,
                Date().time,
                EventType.APP_INSTALLED,
                gson.toJson(initData)
            )
        )
    }

    fun appInfoUpdate(context: Context, initData: PartialInitData) {
        DbManager.addEventToQueue(
            context, Event(
                UUID.randomUUID().toString(),
                -1,
                Date().time,
                EventType.APP_INFO_UPDATED,
                gson.toJson(initData)
            )
        )
    }

    fun sendEventsIfExist(context: Context) {
        val keys = DbManager.getEventsKeys()

        if (keys.isNotEmpty()) {
            BackgroundWorkManager.startOneTimeService(context)
        }
    }
}