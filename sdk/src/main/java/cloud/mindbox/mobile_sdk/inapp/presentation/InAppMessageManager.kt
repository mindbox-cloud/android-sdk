package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

internal class InAppMessageManager {

    private val inAppMessageViewDisplayer: InAppMessageViewDisplayer by inject(
        InAppMessageViewDisplayerImpl::class.java)
    private val inAppInteractor: InAppInteractor by inject(InAppInteractor::class.java)

    fun registerCurrentActivity(activity: Activity) {
        inAppMessageViewDisplayer.registerCurrentActivity(activity, true)
    }

    fun initInAppMessages(context: Context, configuration: MindboxConfiguration) {

        Mindbox.mindboxScope.launch {
            inAppInteractor.processEventAndConfig(context, configuration).collect { inAppMessage ->
                withContext(Dispatchers.Main)
                {
                    if (InAppMessageViewDisplayerImpl.isInAppMessageActive.not() && IS_IN_APP_SHOWN.not()) {
                        IS_IN_APP_SHOWN = true
                        inAppMessageViewDisplayer.showInAppMessage(inAppType = inAppMessage,
                            onInAppClick = {
                                sendInAppClicked(context, inAppMessage.inAppId)
                            },
                            onInAppShown = {
                                sendInAppShown(context, inAppMessage.inAppId)
                            })
                    }
                }
            }
        }
        inAppInteractor.fetchInAppConfig(context, configuration)
    }

    fun registerInAppCallback(inAppCallback: InAppCallback)
    {
        inAppMessageViewDisplayer
    }

    private fun sendInAppShown(context: Context, inAppId: String) {
        inAppInteractor.sendInAppShown(context, inAppId)
    }

    private fun sendInAppClicked(context: Context, inAppId: String) {
        inAppInteractor.sendInAppClicked(context, inAppId)
    }

    fun onPauseCurrentActivity(activity: Activity) {
        inAppMessageViewDisplayer.onPauseCurrentActivity(activity)
    }

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        inAppMessageViewDisplayer.onResumeCurrentActivity(activity, shouldUseBlur)
    }


    companion object {
        const val CURRENT_IN_APP_VERSION = 1
        private var IS_IN_APP_SHOWN = false
    }

}