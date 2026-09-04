package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.firstOverlayVariant
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppNotShown
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.millisToTimeSpan
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.VolleyError
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

internal class InAppMessageManagerImpl(
    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer,
    private val inAppInteractor: InAppInteractor,
    private val defaultDispatcher: CoroutineDispatcher,
    private val monitoringInteractor: MonitoringInteractor,
    private val sessionStorageManager: SessionStorageManager,
    private val userVisitManager: UserVisitManager,
    private val inAppMessageDelayedManager: InAppMessageDelayedManager,
    private val timeProvider: TimeProvider,
    private val featureToggleManager: FeatureToggleManager,
) : InAppMessageManager {

    init {
        sessionStorageManager.addSessionExpirationListener {
            mindboxLogI("Start a new session now!")
            handleSessionExpiration()
        }
    }

    private var processingJob: Job? = null

    private val inAppScope =
        CoroutineScope(defaultDispatcher + SupervisorJob() + Mindbox.coroutineExceptionHandler)

    override fun listenEventAndInApp() {
        processingJob = inAppScope.launch {
            launch {
                inAppInteractor.listenToTargetingEvents()
            }
            launch {
                handleInAppFromInteractor()
            }
            launch {
                handleInAppFromDelayedManager()
            }
        }
    }

    private suspend fun handleInAppFromInteractor() {
        inAppInteractor.processEventAndConfig()
            .onEach { (inApp, preparedTimeMs) ->
                mindboxLogI("Got in-app from interactor: ${inApp.id}. Processing with DelayedManager.")
                inAppMessageDelayedManager.process(inApp, preparedTimeMs)
            }
            .collect()
    }

    private suspend fun handleInAppFromDelayedManager() {
        inAppMessageDelayedManager.inAppToShowFlow.collect { (inApp, preparedTimeMs) ->
            mindboxLogI("Got in-app from DelayedManager: ${inApp.id}")
            withContext(Dispatchers.Main) {
                if (inAppMessageViewDisplayer.isInAppActive()) {
                    mindboxLogI("InApp is active. Skip ${inApp.id}")
                    return@withContext
                }

                val inAppMessage = inApp.firstOverlayVariant()
                if (inAppMessage == null) {
                    mindboxLogI("InApp ${inApp.id} has no variant an overlay can show. Skipping.")
                    return@withContext
                }

                val hold = inAppInteractor.reserveOverlayShow(inApp)
                if (hold == ShowReservationOutcome.REFUSED) {
                    mindboxLogI("InApp ${inApp.id} failed final show-limits and frequency check. Skipping.")
                    return@withContext
                }

                val tags = inApp.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))
                val callbacks = ShowCallbacks(inApp, inAppMessage, tags, preparedTimeMs, holdsBudget = hold == ShowReservationOutcome.GRANTED)

                inAppMessageViewDisplayer.tryShowInAppMessage(
                    inAppType = inAppMessage,
                    onRenderStart = callbacks.onRenderStart,
                    tags = tags,
                    inAppActionCallbacks = callbacks
                )
            }
        }
    }

    override fun showInAppById(inAppId: String, extraParams: Map<String, JsonElement>) {
        val tapTick = timeProvider.monotonicMillis()
        inAppScope.launch {
            val inAppToShow = inAppInteractor.getInAppToShowById(inAppId) ?: run {
                mindboxLogI("Nothing to show for in-app $inAppId")
                return@launch
            }
            val (inApp, variant) = inAppToShow
            val tags = inApp.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))
            val callbacks = ShowCallbacks(inApp, variant, tags, preparedTime = timeProvider.monotonicElapsedSince(tapTick))
            withContext(Dispatchers.Main) {
                inAppMessageViewDisplayer.showInAppMessageNow(
                    inAppType = variant,
                    onRenderStart = callbacks.onRenderStart,
                    tags = tags,
                    extraParams = extraParams,
                    inAppActionCallbacks = callbacks
                )
            }
        }
    }

    /**
     * The one set of show/dismiss callbacks both overlay paths share: the queue and the page's
     * tap must never drift apart in what a show writes down. The tap path carries no processing
     * delay, so it passes a zero [preparedTime].
     */
    private inner class ShowCallbacks(
        inApp: InApp,
        private val variant: InAppType,
        tags: Map<String, String>?,
        preparedTime: Milliseconds,
        private val holdsBudget: Boolean = false,
    ) : InAppActionCallbacks {

        private var renderStartTime = Timestamp(0L)

        @Volatile private var isShown = false

        val onRenderStart: () -> Unit = { renderStartTime = timeProvider.currentTimestamp() }

        override val onInAppClick = OnInAppClick {
            inAppInteractor.sendInAppClicked(variant.inAppId, tags)
        }
        override val onInAppShown = OnInAppShown {
            isShown = true
            handleInAppShown(renderStartTime, preparedTime, variant, tags)
        }
        override val onInAppDismiss = OnInAppDismiss {
            giveBackHoldIfNotShown()
            inAppInteractor.saveInAppDismissTime(inApp)
        }
        override val onInAppNotShown = OnInAppNotShown { giveBackHoldIfNotShown() }

        private fun giveBackHoldIfNotShown() {
            if (holdsBudget && !isShown) inAppInteractor.releaseOverlayShow(variant.inAppId)
        }
    }

    /**
     * In case of 404 clear config
     * In case of other network error use cached version
     * Otherwise do nothing
     **/
    override fun requestConfig(): Job {
        return inAppScope.launch(CoroutineExceptionHandler { _, error ->
            if (error is VolleyError) {
                when (error.networkResponse?.statusCode) {
                    CONFIG_NOT_FOUND -> {
                        MindboxLoggerImpl.w(InAppMessageManagerImpl, "Config not found", error)
                        MindboxPreferences.inAppConfig = ""
                    }

                    else -> {
                        sessionStorageManager.configFetchingError = true
                        // needed to trigger flow event
                        MindboxPreferences.inAppConfig = MindboxPreferences.inAppConfig
                        MindboxLoggerImpl.e(InAppMessageManagerImpl, "Failed to get config", error)
                    }
                }
            } else {
                MindboxPreferences.inAppConfig = MindboxPreferences.inAppConfig
                MindboxLoggerImpl.e(
                    this@InAppMessageManagerImpl::class,
                    "Failed to get config",
                    error
                )
            }
        }) {
            inAppInteractor.fetchMobileConfig()
        }
    }

    override fun initLogs() {
        monitoringInteractor.processLogs()
    }

    override fun registerInAppCallback(inAppCallback: InAppCallback) = loggingRunCatching {
        inAppMessageViewDisplayer.registerInAppCallback(inAppCallback)
    }

    override fun unregisterInAppCallback(): Unit = loggingRunCatching {
        inAppMessageViewDisplayer.unregisterInAppCallback()
    }

    override fun registerCurrentActivity(activity: Activity): Unit = loggingRunCatching {
        inAppMessageViewDisplayer.registerCurrentActivity(activity)
    }

    override fun onPauseCurrentActivity(activity: Activity): Unit = loggingRunCatching {
        inAppMessageViewDisplayer.onPauseCurrentActivity(activity)
    }

    override fun onStopCurrentActivity(activity: Activity): Unit = loggingRunCatching {
        inAppMessageViewDisplayer.onStopCurrentActivity(activity)
    }

    override fun onResumeCurrentActivity(activity: Activity): Unit = loggingRunCatching {
        inAppMessageViewDisplayer.onResumeCurrentActivity(
            activity = activity,
            isNeedToShow = { !sessionStorageManager.isSessionExpiredOnLastCheck() },
            onAppResumed = { inAppMessageDelayedManager.onAppResumed() }
        )
    }

    override fun handleSessionExpiration() {
        inAppScope.launch {
            withContext(Dispatchers.Main) {
                inAppMessageViewDisplayer.dismissCurrentInApp()
            }
            processingJob?.cancel()
            inAppInteractor.resetInAppConfigAndEvents()
            sessionStorageManager.clearSessionData()
            userVisitManager.saveUserVisit()
            inAppMessageDelayedManager.clearSession()
            InitializeLock.reset(InitializeLock.State.APP_STARTED)
            listenEventAndInApp()
            initLogs()
            MindboxEventManager.eventFlow.emit(MindboxEventManager.appStarted())
            requestConfig().join()
        }
    }

    private fun handleInAppShown(
        renderStartTime: Timestamp,
        preparedTimeMs: Milliseconds,
        inAppMessage: InAppType,
        tags: Map<String, String>?
    ) {
        val shownTime = timeProvider.currentTimestamp()
        val renderTime = shownTime - renderStartTime
        mindboxLogI("Render time is ${renderTime.ms}ms, prepared time is ${preparedTimeMs.interval}ms")
        val timeToDisplay = (preparedTimeMs.interval + renderTime.ms).millisToTimeSpan()
        inAppInteractor.saveShownInApp(inAppMessage.inAppId, shownTime.ms, timeToDisplay, tags)
    }

    companion object {
        const val CONFIG_NOT_FOUND = 404
    }
}
