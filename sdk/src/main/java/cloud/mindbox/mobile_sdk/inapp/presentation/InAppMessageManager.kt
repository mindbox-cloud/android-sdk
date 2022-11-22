package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.java.KoinJavaComponent.inject

internal class InAppMessageManager {

    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer by inject(
        InAppMessageViewDisplayerImpl::class.java)
    private val inAppInteractor: InAppInteractor by inject(InAppInteractor::class.java)

    fun registerCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerCurrentActivity(activity, true)
        }
    }

    fun initInAppMessages(context: Context, configuration: MindboxConfiguration) {
        Mindbox.mindboxScope.launch {
            inAppInteractor.processEventAndConfig(context, configuration)
                .collect { inAppMessage ->
                    withContext(Dispatchers.Main)
                    {
                        if (InAppMessageViewDisplayerImpl.isInAppMessageActive.not() && IS_IN_APP_SHOWN.not()) {
                            IS_IN_APP_SHOWN = true
                            inAppMessageViewDisplayer.showInAppMessage(inAppType = inAppMessage,
                                onInAppClick = {
                                    sendInAppClicked(context, inAppMessage.inAppId)
                                },
                                onInAppShown = {
                                    inAppInteractor.saveShownInApp(inAppMessage.inAppId)
                                    sendInAppShown(context, inAppMessage.inAppId)
                                })
                        }
                    }
                }
        }
        Mindbox.mindboxScope.launch(CoroutineExceptionHandler { _, error ->
            if (error is VolleyError) {
                when (error.networkResponse?.statusCode) {
                    CONFIG_NOT_FOUND -> {
                        MindboxLoggerImpl.w(this, "Config not found", error)
                        MindboxPreferences.inAppConfig = ""
                    }
                    else -> {
                        // needed to trigger flow event
                        MindboxPreferences.inAppConfig = MindboxPreferences.inAppConfig
                        MindboxLoggerImpl.e(this, "Failed to get config", error)
                    }
                }
            } else {
                MindboxLoggerImpl.e(this, "Failed to get config", error)
            }
        }) {
            inAppInteractor.fetchInAppConfig(context, configuration)
        }

    }

    fun registerInAppCallback(inAppCallback: InAppCallback) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.registerInAppCallback(inAppCallback)
        }
    }

    private fun sendInAppShown(context: Context, inAppId: String) {
        inAppInteractor.sendInAppShown(context, inAppId)
    }

    private fun sendInAppClicked(context: Context, inAppId: String) {
        inAppInteractor.sendInAppClicked(context, inAppId)
    }

    fun onPauseCurrentActivity(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onPauseCurrentActivity(activity)
        }
    }

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        LoggingExceptionHandler.runCatching {
            inAppMessageViewDisplayer.onResumeCurrentActivity(activity, shouldUseBlur)
        }
    }


    companion object {
        const val CURRENT_IN_APP_VERSION = 1
        private var IS_IN_APP_SHOWN = false
        const val CONFIG_NOT_FOUND = 404
        private const val ERROR_TAG = "InAppMessageManager"
    }

}