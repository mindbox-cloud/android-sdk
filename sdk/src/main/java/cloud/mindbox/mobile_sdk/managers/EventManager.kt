package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import com.google.gson.Gson
import java.util.*

internal object EventManager {

    private val gson = Gson()

    fun appInstalled(initData: FullInitData) {
        DbManager.addEventToQueue(
            Event(
                UUID.randomUUID().toString(),
                -1,
                Date().time,
                EventType.APP_INSTALLED,
                gson.toJson(initData)
            )
        )
    }

    fun appInfoUpdate(initData: PartialInitData) {
        DbManager.addEventToQueue(
            Event(
                UUID.randomUUID().toString(),
                -1,
                Date().time,
                EventType.APP_INFO_UPDATED,
                gson.toJson(initData)
            )
        )
    }
}