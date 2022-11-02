package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.OperationResponseBaseInternal
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

internal object MindboxEventManager {

    private const val EMPTY_JSON_OBJECT = "{}"
    private const val NULL_JSON = "null"

    private val gson = Gson()

    val eventFlow = MutableSharedFlow<InAppEventType>(replay = 1)


    private val poolDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    fun appInstalled(
        context: Context,
        initData: InitData,
        shouldCreateCustomer: Boolean,
    ) = LoggingExceptionHandler.runCatching {
        val eventType = if (shouldCreateCustomer) {
            EventType.AppInstalled
        } else {
            EventType.AppInstalledWithoutCustomer
        }
        asyncOperation(context, Event(eventType = eventType, body = gson.toJson(initData)))
    }

    fun appInfoUpdate(
        context: Context,
        initData: UpdateData,
    ) = LoggingExceptionHandler.runCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.AppInfoUpdated, body = gson.toJson(initData)),
        )
    }

    fun pushDelivered(context: Context, uniqKey: String) {
        asyncOperation(
            context,
            Event(
                eventType = EventType.PushDelivered,
                additionalFields = hashMapOf(EventParameters.UNIQ_KEY.fieldName to uniqKey),
            ),
        )
    }

    fun inAppShown(context: Context, operationName: String, body: String) {
        asyncOperation(context, operationName, body)
    }

    fun inAppClicked(context: Context, operationName: String, body: String) {
        asyncOperation(context, operationName, body)
    }

    fun pushClicked(
        context: Context,
        clickData: TrackClickData,
    ) = LoggingExceptionHandler.runCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.PushClicked, body = gson.toJson(clickData)),
        )
    }

    fun appStarted(
        context: Context,
        trackVisitData: TrackVisitData,
    ) = LoggingExceptionHandler.runCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.TrackVisit, body = gson.toJson(trackVisitData)),
        )
    }

    fun asyncOperation(context: Context, name: String, body: String) =
        asyncOperation(
            context,
            Event(
                eventType = EventType.AsyncOperation(name),
                body = if (body.isNotBlank() && body != NULL_JSON) body else EMPTY_JSON_OBJECT,
            ),
        )

    fun appStarted(): InAppEventType.AppStartup {
        return InAppEventType.AppStartup
    }


    private fun asyncOperation(
        context: Context,
        event: Event,
    ) = LoggingExceptionHandler.runCatching {
        runBlocking(Dispatchers.IO) { DbManager.addEventToQueue(context, event) }
        Mindbox.mindboxScope.launch(poolDispatcher) {
            eventFlow.emit(InAppEventType.OrdinalEvent(event.eventType))
            LoggingExceptionHandler.runCatching {
                val configuration = DbManager.getConfigurations()
                val deviceUuid = MindboxPreferences.deviceUuid
                val isInstallEvent = event.eventType is EventType.AppInstalled
                        || event.eventType is EventType.AppInstalledWithoutCustomer
                val isInitialized = !MindboxPreferences.isFirstInitialize || isInstallEvent
                if (!isInitialized || configuration == null) {
                    MindboxLoggerImpl.e(this, "Configuration was not initialized")
                } else {
                    WorkerDelegate().sendEvent(
                        context = context,
                        configuration = configuration,
                        deviceUuid = deviceUuid,
                        event = event,
                        parent = this@MindboxEventManager,
                        shouldStartWorker = true,
                        shouldCountOffset = false
                    )
                    if (isInstallEvent) MindboxPreferences.isFirstInitialize = false
                }
            }
        }
    }

    fun <T, V : OperationResponseBaseInternal> syncOperation(
        context: Context,
        name: String,
        body: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit,
    ) = LoggingExceptionHandler.runCatching {
        val configuration = checkConfiguration(onError) ?: return@runCatching

        val json = gson.toJson(body)
        val jsonBody = if (json.isNotBlank() && json != NULL_JSON) json else EMPTY_JSON_OBJECT
        val event = createSyncEvent(name, jsonBody)
        val deviceUuid = MindboxPreferences.deviceUuid
        GatewayManager.sendEvent(
            context = context,
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            classOfT = classOfV,
            shouldCountOffset = false,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun syncOperation(
        context: Context,
        name: String,
        bodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) = LoggingExceptionHandler.runCatching {
        val configuration = checkConfiguration(onError) ?: return@runCatching

        val event = createSyncEvent(name, bodyJson)
        val deviceUuid = MindboxPreferences.deviceUuid

        GatewayManager.sendEvent(
            context = context,
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            shouldCountOffset = false,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    private fun createSyncEvent(
        name: String,
        bodyJson: String,
    ): Event {
        val eventType = EventType.SyncOperation(name)
        Mindbox.mindboxScope.launch {
            eventFlow.emit(InAppEventType.OrdinalEvent(eventType))
        }
        return Event(eventType = eventType, body = bodyJson)
    }

    private fun checkConfiguration(onError: (MindboxError) -> Unit): Configuration? {
        val configuration = DbManager.getConfigurations()
        if (MindboxPreferences.isFirstInitialize || configuration == null) {
            MindboxLoggerImpl.e(this, "Configuration was not initialized")
            onError.invoke(MindboxError.Unknown())
            return null
        }
        return configuration
    }

    fun sendEventsIfExist(context: Context) = LoggingExceptionHandler.runCatching {
        if (DbManager.getFilteredEventsForBackgroundSend().isNotEmpty()) {
            BackgroundWorkManager.startOneTimeService(context)
        }
    }

    fun <T> operationBodyJson(body: T): String = gson.toJson(body)

}
