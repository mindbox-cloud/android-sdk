package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
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
    private val inAppMessageDelayedManager: InAppMessageDelayedManager
) : InAppMessageManager {

    init {
        sessionStorageManager.addSessionExpirationListener {
            mindboxLogI("Start a new session now!")
            handleSessionExpiration()
        }
    }

    private var processingJob: Job? = null

    override fun registerCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerCurrentActivity(activity)
        }
    }

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
            .onEach { inApp ->
                mindboxLogI("Got in-app from interactor: ${inApp.id}. Processing with DelayedManager.")
                inAppMessageDelayedManager.process(inApp)
            }
            .collect()
    }

    private suspend fun handleInAppFromDelayedManager() {
        inAppMessageDelayedManager.inAppToShowFlow.collect { inApp ->
            mindboxLogI("Got in-app from DelayedManager: ${inApp.id}")
            withContext(Dispatchers.Main) {
                if (inAppMessageViewDisplayer.isInAppActive()) {
                    mindboxLogD("InApp is active. Skip ${inApp.id}")
                    return@withContext
                }

                if (!inAppInteractor.areShowAndFrequencyLimitsAllowed(inApp)) {
                    mindboxLogI("InApp ${inApp.id} failed final show-limits and frequency check. Skipping.")
                    return@withContext
                }

                val inAppMessage = inApp.form.variants.firstOrNull()
                if (inAppMessage == null) {
                    mindboxLogI("InApp ${inApp.id} has no variants to show. Skipping.")
                    return@withContext
                }

                inAppMessageViewDisplayer.tryShowInAppMessage(
                    inAppType = inAppMessage,
                    inAppActionCallbacks = object : InAppActionCallbacks {
                        override val onInAppClick = OnInAppClick {
                            inAppInteractor.sendInAppClicked(inAppMessage.inAppId)
                        }
                        override val onInAppShown = OnInAppShown {
                            inAppInteractor.saveShownInApp(inAppMessage.inAppId, System.currentTimeMillis())
                        }
                        override val onInAppDismiss = OnInAppDismiss {
                            inAppInteractor.saveInAppDismissTime()
                        }
                    }
                )
            }
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

    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerInAppCallback(inAppCallback)
        }
    }

    override fun onPauseCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onPauseCurrentActivity(activity)
        }
    }

    override fun onStopCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onStopCurrentActivity(activity)
        }
    }

    override fun onResumeCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onResumeCurrentActivity(
                activity = activity,
                isSessionActive = { sessionStorageManager.isSessionActive() },
                onAppResumed = { inAppMessageDelayedManager.onAppResumed() }
            )
        }
    }

    override fun handleSessionExpiration() {
        inAppScope.launch {
            withContext(Dispatchers.Main) {
                inAppMessageViewDisplayer.hideCurrentInApp()
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

    companion object {
        const val CONFIG_NOT_FOUND = 404
    }
}
