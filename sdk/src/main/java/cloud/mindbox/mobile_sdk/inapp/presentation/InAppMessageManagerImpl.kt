package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class InAppMessageManagerImpl(
    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer,
    private val inAppInteractorImpl: InAppInteractor,
) : InAppMessageManager {

    override fun registerCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerCurrentActivity(activity, true)
        }
    }

    override fun listenEventAndInApp(configuration: MindboxConfiguration) {
        Mindbox.mindboxScope.launch {
            inAppInteractorImpl.processEventAndConfig(configuration)
                .collect { inAppMessage ->
                    withContext(Dispatchers.Main)
                    {
                        if (InAppMessageViewDisplayerImpl.isInAppMessageActive.not() && IS_IN_APP_SHOWN.not()) {
                            IS_IN_APP_SHOWN = true
                            inAppMessageViewDisplayer.showInAppMessage(inAppType = inAppMessage,
                                onInAppClick = {
                                    sendInAppClicked(inAppMessage.inAppId)
                                },
                                onInAppShown = {
                                    inAppInteractorImpl.saveShownInApp(inAppMessage.inAppId)
                                    sendInAppShown(inAppMessage.inAppId)
                                })
                        }
                    }
                }
        }
    }

    override fun requestConfig(configuration: MindboxConfiguration) {
        Mindbox.mindboxScope.launch(CoroutineExceptionHandler { _, error ->
            if (error is VolleyError) {
                when (error.networkResponse?.statusCode) {
                    CONFIG_NOT_FOUND -> {
                        MindboxLoggerImpl.w(ERROR_TAG, error.message ?: "", error)
                        MindboxPreferences.inAppConfig = ""
                    }
                    else -> {
                        MindboxPreferences.inAppConfig = MindboxPreferences.inAppConfig
                        MindboxLoggerImpl.e(ERROR_TAG, error.message ?: "", error)
                    }
                }
            } else {
                LoggingExceptionHandler.runCatching {

                }
            }
        }) {
            inAppInteractorImpl.fetchInAppConfig(configuration)
        }
    }

    override fun initInAppMessages(configuration: MindboxConfiguration) {
        listenEventAndInApp(configuration)
        requestConfig(configuration)
    }

    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerInAppCallback(inAppCallback)
        }
    }

    private fun sendInAppShown(inAppId: String) {
        inAppInteractorImpl.sendInAppShown(inAppId)
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
        const val CURRENT_IN_APP_VERSION = 1
        var IS_IN_APP_SHOWN = false
        const val CONFIG_NOT_FOUND = 404
        private const val ERROR_TAG = "InAppMessageManager"
    }

}