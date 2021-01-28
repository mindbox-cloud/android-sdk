package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.PartialInitData
import com.google.gson.Gson
import java.util.*

internal object EventManager {

    private val gson = Gson()

    fun appInfoUpdate(initData: PartialInitData) {
        DbManager.addEventToQueue(
            Event(
                UUID.randomUUID().toString(),
                -1,
                Date().time,
                gson.toJson(initData)
            )
        )
    }
}