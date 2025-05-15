package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.OperationResponseBaseInternal
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal object MindboxEventManager {

    private const val EMPTY_JSON_OBJECT = "{}"
    private const val NULL_JSON = "null"

    const val IN_APP_OPERATION_VIEW_TYPE = "Inapp.Show"
    const val IN_APP_OPERATION_CLICK_TYPE = "Inapp.Click"
    const val IN_APP_OPERATION_TARGETING_TYPE = "Inapp.Targeting"

    private val gson = Gson()

    val eventFlow = MutableSharedFlow<InAppEventType>(replay = 20)

    private val poolDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val timeProvider by mindboxInject { timeProvider }

    fun appInstalled(
        context: Context,
        initData: InitData,
        shouldCreateCustomer: Boolean,
    ): Unit = loggingRunCatching {
        val eventType = if (shouldCreateCustomer) {
            EventType.AppInstalled
        } else {
            EventType.AppInstalledWithoutCustomer
        }
        asyncOperation(context, Event(eventType = eventType, body = gson.toJson(initData)))
        updateLastInfoUpdateTime()
    }

    fun appInfoUpdate(
        context: Context,
        initData: UpdateData,
    ): Unit = loggingRunCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.AppInfoUpdated, body = gson.toJson(initData)),
        )
        updateLastInfoUpdateTime()
    }

    fun appKeepAlive(
        context: Context,
        initData: UpdateData,
    ): Unit = loggingRunCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.AppKeepALive, body = gson.toJson(initData)),
        )
        updateLastInfoUpdateTime()
    }

    fun inAppShown(context: Context, body: String) {
        asyncOperation(context, IN_APP_OPERATION_VIEW_TYPE, body)
    }

    fun inAppClicked(context: Context, body: String) {
        asyncOperation(context, IN_APP_OPERATION_CLICK_TYPE, body)
    }

    fun sendUserTargeted(context: Context, body: String) {
        asyncOperation(context, IN_APP_OPERATION_TARGETING_TYPE, body)
    }

    fun pushClicked(
        context: Context,
        clickData: TrackClickData,
    ): Unit = loggingRunCatching {
        asyncOperation(
            context,
            Event(eventType = EventType.PushClicked, body = gson.toJson(clickData)),
        )
    }

    fun appStarted(
        context: Context,
        trackVisitData: TrackVisitData,
    ): Unit = loggingRunCatching {
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
    ): Unit = loggingRunCatching {
        Mindbox.mindboxScope.launch(poolDispatcher) {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            DbManager.addEventToQueue(context, event)

            eventFlow.emit(InAppEventType.OrdinalEvent(event.eventType, event.body))
            loggingRunCatching {
                val configuration = DbManager.getConfigurations()
                val deviceUuid = MindboxPreferences.deviceUuid
                val isInstallEvent = event.eventType is EventType.AppInstalled ||
                    event.eventType is EventType.AppInstalledWithoutCustomer
                val isInitialized = !MindboxPreferences.isFirstInitialize || isInstallEvent
                if (!isInitialized || configuration == null) {
                    this@MindboxEventManager.mindboxLogW(
                        "Event ${event.eventType.operation} will be sent later, " +
                            "because configuration was not initialized"
                    )
                    this@MindboxEventManager.mindboxLogI(
                        "isFirstInitialize: ${MindboxPreferences.isFirstInitialize}, " +
                            "isInstallEvent: $isInstallEvent, configuration is null: ${configuration == null}"
                    )
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
        name: String,
        body: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit,
    ): Unit = loggingRunCatching {
        val configuration = checkConfiguration(onError) ?: return@loggingRunCatching

        val json = gson.toJson(body)
        MindboxLoggerImpl.d(this, "syncOperation. json: $json")
        val jsonBody = if (json.isNotBlank() && json != NULL_JSON) json else EMPTY_JSON_OBJECT
        val event = createSyncEvent(name, jsonBody)
        val deviceUuid = MindboxPreferences.deviceUuid
        MindboxDI.appModule.gatewayManager.sendEvent(
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
        name: String,
        bodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ): Unit = loggingRunCatching {
        val configuration = checkConfiguration(onError) ?: return@loggingRunCatching

        val event = createSyncEvent(name, bodyJson)
        val deviceUuid = MindboxPreferences.deviceUuid

        MindboxDI.appModule.gatewayManager.sendEvent(
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
            eventFlow.emit(InAppEventType.OrdinalEvent(eventType, bodyJson))
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

    fun sendEventsIfExist(context: Context): Unit = loggingRunCatching {
        if (DbManager.getFilteredEventsForBackgroundSend().isNotEmpty()) {
            BackgroundWorkManager.startOneTimeService(context)
        }
    }

    fun <T> operationBodyJson(body: T): String = gson.toJson(body)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun resetEventFlowCache() {
        eventFlow.resetReplayCache()
    }

    private fun updateLastInfoUpdateTime() {
        MindboxPreferences.lastInfoUpdateTime = timeProvider.currentTimeMillis()
    }
}
