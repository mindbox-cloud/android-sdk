package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

internal class InAppMessageManagerImpl(
    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer,
    private val inAppInteractorImpl: InAppInteractor,
    private val defaultDispatcher: CoroutineDispatcher,
    private val monitoringRepository: MonitoringInteractor,
) : InAppMessageManager {

    override fun registerCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerCurrentActivity(activity, true)
        }
    }

    private val inAppScope =
        CoroutineScope(defaultDispatcher + SupervisorJob() + Mindbox.coroutineExceptionHandler)

    override fun listenEventAndInApp() {
        inAppScope.launch {
            inAppInteractorImpl.processEventAndConfig()
                .collect { inAppMessage ->
                    withContext(Dispatchers.Main)
                    {
                        if ((InAppMessageViewDisplayerImpl.isInAppMessageActive || isInAppShown()).not()) {
                            inAppMessageViewDisplayer.tryShowInAppMessage(inAppType = inAppMessage,
                                onInAppClick = {
                                    sendInAppClicked(inAppMessage.inAppId)
                                },
                                onInAppShown = {
                                    inAppInteractorImpl.saveShownInApp(inAppMessage.inAppId)
                                    sendInAppShown(inAppMessage.inAppId)
                                    setInAppShown()
                                })
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
    override fun requestConfig() {
        inAppScope.launch(CoroutineExceptionHandler { _, error ->
            if (error is VolleyError) {
                when (error.networkResponse?.statusCode) {
                    CONFIG_NOT_FOUND -> {
                        MindboxLoggerImpl.w(InAppMessageManagerImpl, "Config not found", error)
                        MindboxPreferences.inAppConfig = ""
                    }
                    else -> {
                        // needed to trigger flow event
                        MindboxPreferences.inAppConfig = MindboxPreferences.inAppConfig
                        MindboxLoggerImpl.e(InAppMessageManagerImpl, "Failed to get config", error)
                    }
                }
            } else {
                MindboxLoggerImpl.e(
                    this@InAppMessageManagerImpl::class,
                    "Failed to get config",
                    error
                )
            }
        }) {
            inAppInteractorImpl.fetchMobileConfig()
        }
    }

    override fun initInAppMessages() {
        listenEventAndInApp()
        requestConfig()
        initMonitoring()
    }

    private fun initMonitoring() {
        monitoringRepository.processLogs()
    }


    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerInAppCallback(inAppCallback)
        }
    }

    private fun sendInAppShown(inAppId: String) {
        inAppInteractorImpl.sendInAppShown(inAppId)
    }

    private fun setInAppShown() {
        inAppInteractorImpl.setInAppShown()
    }

    private fun isInAppShown(): Boolean {
        return inAppInteractorImpl.isInAppShown()
    }

    private fun sendInAppClicked(inAppId: String) {
        inAppInteractorImpl.sendInAppClicked(inAppId)
    }

    override fun onPauseCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onPauseCurrentActivity(activity)
        }
    }

    override fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onResumeCurrentActivity(activity, shouldUseBlur)
        }
    }


    companion object {
        const val CURRENT_IN_APP_VERSION = 4
        const val CONFIG_NOT_FOUND = 404
    }

}