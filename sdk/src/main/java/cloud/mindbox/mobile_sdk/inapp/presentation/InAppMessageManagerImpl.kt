package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
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

internal class InAppMessageManagerImpl(
    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer,
    private val inAppInteractor: InAppInteractor,
    private val defaultDispatcher: CoroutineDispatcher,
    private val monitoringInteractor: MonitoringInteractor,
    private val sessionStorageManager: SessionStorageManager,
    private val userVisitManager: UserVisitManager
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
            inAppMessageViewDisplayer.registerCurrentActivity(activity, true)
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
                inAppInteractor.processEventAndConfig()
                    .collect { inAppMessage ->
                        withContext(Dispatchers.Main) {
                            if (inAppMessageViewDisplayer.isInAppActive()) {
                                this@InAppMessageManagerImpl.mindboxLogD("Inapp is active. Skip ${inAppMessage.inAppId}")
                                return@withContext
                            }

                            if (inAppInteractor.isInAppShown()) {
                                this@InAppMessageManagerImpl.mindboxLogD("Inapp already shown. Skip ${inAppMessage.inAppId}")
                                return@withContext
                            }
                            inAppMessageViewDisplayer.tryShowInAppMessage(
                                inAppType = inAppMessage,
                                onInAppClick = {
                                    inAppInteractor.sendInAppClicked(inAppMessage.inAppId)
                                },
                                onInAppShown = {
                                    inAppInteractor.saveShownInApp(inAppMessage.inAppId, System.currentTimeMillis())
                                }
                            )
                        }
                    }
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

    override fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onResumeCurrentActivity(activity, shouldUseBlur)
        }
    }

    override fun handleSessionExpiration() {
        inAppScope.launch {
            inAppMessageViewDisplayer.hideCurrentInApp()
            processingJob?.cancel()
            inAppInteractor.resetInAppConfigAndEvents()
            sessionStorageManager.clearSessionData()
            userVisitManager.saveUserVisit()
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
