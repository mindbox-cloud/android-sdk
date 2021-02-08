package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import com.google.gson.Gson
import java.util.*
import java.util.concurrent.CountDownLatch

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

    fun sendEvents(context: Context, eventKeys: List<String>) {
        eventKeys.forEach { eventKey ->
            val countDownLatch = CountDownLatch(1)

            val event = DbManager.getEvent(eventKey) ?: return@forEach

            GatewayManager.sendEvent(context, event) { isSended ->
                if (isSended) {
                    DbManager.removeEventFromQueue(event.transactionId)
                }
                countDownLatch.countDown()
            }

            try {
                countDownLatch.await()
            } catch (e: InterruptedException) {
                Logger.e(this, "doWork -> sending was interrupted", e)
            }
        }
    }
}