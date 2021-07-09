package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponseBase
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object MindboxEventManager {

    private const val EMPTY_JSON_OBJECT = "{}"
    private const val NULL_JSON = "null"

    private val gson = Gson()

    fun appInstalled(context: Context, initData: InitData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.AppInstalled,
                    body = gson.toJson(initData)
                )
            )
        }.logOnException()
    }

    fun appInfoUpdate(context: Context, initData: UpdateData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.AppInfoUpdated,
                    body = gson.toJson(initData)
                )
            )
        }.logOnException()
    }

    fun pushDelivered(context: Context, uniqKey: String) {
        runCatching {
            runBlocking(Dispatchers.IO) {
                val fields = hashMapOf(
                    EventParameters.UNIQ_KEY.fieldName to uniqKey
                )
                DbManager.addEventToQueue(
                    context, Event(
                        eventType = EventType.PushDelivered,
                        additionalFields = fields
                    )
                )
            }
        }.logOnException()
    }

    fun pushClicked(context: Context, clickData: TrackClickData) {
        runCatching {
            runBlocking(Dispatchers.IO) {
                DbManager.addEventToQueue(
                    context, Event(
                        eventType = EventType.PushClicked,
                        body = gson.toJson(clickData)
                    )
                )
            }
        }.logOnException()
    }

    fun appStarted(context: Context, trackVisitData: TrackVisitData) {
        runCatching {
            DbManager.addEventToQueue(
                context, Event(
                    eventType = EventType.TrackVisit,
                    body = gson.toJson(trackVisitData)
                )
            )
        }.logOnException()
    }

    fun <T> asyncOperation(context: Context, name: String, body: T) {
        runCatching {
            runBlocking(Dispatchers.IO) {
                val json = gson.toJson(body)
                DbManager.addEventToQueue(
                    context, Event(
                        eventType = EventType.AsyncOperation(name),
                        body = if (json.isNotBlank() && json != NULL_JSON) json else EMPTY_JSON_OBJECT
                    )
                )
            }
        }.logOnException()
    }

    fun <T, V : OperationResponseBase> syncOperation(
        context: Context,
        name: String,
        body: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit
    ) = runCatching {
        val configuration = DbManager.getConfigurations()
        if (MindboxPreferences.isFirstInitialize || configuration == null) {
            MindboxLogger.e(this, "Configuration was not initialized")
            onError.invoke(MindboxError.Unknown())
            return
        }

        val json = gson.toJson(body)
        val event = Event(
            eventType = EventType.SyncOperation(name),
            body = if (json.isNotBlank() && json != NULL_JSON) json else EMPTY_JSON_OBJECT
        )
        val deviceUuid = MindboxPreferences.deviceUuid

        GatewayManager.sendSyncEvent(
            context = context,
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            classOfT = classOfV,
            onSuccess = onSuccess,
            onError = onError
        )
    }.logOnException()

    fun sendEventsIfExist(context: Context) {
        runCatching {
            if (DbManager.getFilteredEvents().isNotEmpty()) {
                BackgroundWorkManager.startOneTimeService(context)
            }
        }.logOnException()
    }

}
