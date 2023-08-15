package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.*
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.ModalWindowInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.SnackbarInAppViewHolder
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import java.util.*


internal class InAppMessageViewDisplayerImpl :
    InAppMessageViewDisplayer {

    private var currentActivity: Activity? = null
    private var inAppCallback: InAppCallback = ComposableInAppCallback(
        UrlInAppCallback(),
        DeepLinkInAppCallback(),
        CopyPayloadInAppCallback(),
        LoggingInAppCallback()
    )
    private val inAppQueue = LinkedList<InAppTypeWrapper<InAppType>>()

    private var currentHolder: InAppViewHolder<*>? = null
    private var pausedHolder: InAppViewHolder<*>? = null


    private fun isUiPresent(): Boolean = currentActivity?.isFinishing?.not() ?: false

    override fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        mindboxLogD("onResumeCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        if (pausedHolder?.isActive == true) {
            pausedHolder?.wrapper?.let { wrapper ->
                mindboxLogD("trying to restore in-app with id $pausedHolder")
                showInAppMessage(wrapper)
            }
        } else {
            tryShowInAppFromQueue()
        }
    }

    override fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        mindboxLogD("registerCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        tryShowInAppFromQueue()
    }

    private fun tryShowInAppFromQueue() {
        if (inAppQueue.isNotEmpty() && !isInAppActive()) {
            with(inAppQueue.pop()) {
                mindboxLogD("trying to show in-app with id ${inAppType.inAppId} from queue")
                showInAppMessage(this)
            }
        }
    }

    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        this.inAppCallback = inAppCallback
    }

    override fun isInAppActive(): Boolean = currentHolder?.isActive ?: false

    override fun onStopCurrentActivity(activity: Activity) {
        mindboxLogD("onStopCurrentActivity: ${activity.hashCode()}")
        pausedHolder?.hide()
    }

    override fun onPauseCurrentActivity(activity: Activity) {
        mindboxLogD("onPauseCurrentActivity: ${activity.hashCode()}")
        if (currentActivity == activity) {
            currentActivity = null
        }
        pausedHolder = currentHolder
        currentHolder = null
    }

    override fun tryShowInAppMessage(
        inAppType: InAppType,
        onInAppClick: OnInAppClick,
        onInAppShown: OnInAppShown,
    ) {
        val wrapper = when (inAppType) {
            is InAppType.ModalWindow -> {
                InAppTypeWrapper(inAppType, onInAppClick, onInAppShown)
            }

            is InAppType.Snackbar ->  {
                InAppTypeWrapper(inAppType, onInAppClick, onInAppShown)
            }
        }
        WindowCompat.setDecorFitsSystemWindows(currentActivity!!.window, false)
        if (isUiPresent()) {
            mindboxLogD("In-app with id ${inAppType.inAppId} is going to be shown immediately")
            showInAppMessage(wrapper)
        } else {
            inAppQueue.add(wrapper)
            mindboxLogD(
                "In-app with id ${inAppType.inAppId} is added to showing queue and will be shown later"
            )
        }
    }

    private fun showInAppMessage(wrapper: InAppTypeWrapper<InAppType>) {
        when (wrapper.inAppType) {
            is InAppType.ModalWindow -> {
                currentActivity?.root?.let { root ->
                    @Suppress("UNCHECKED_CAST")
                    currentHolder = ModalWindowInAppViewHolder(wrapper as InAppTypeWrapper<InAppType.ModalWindow>,
                        inAppCallback = InAppCallbackWrapper(inAppCallback) {
                            pausedHolder?.hide()
                            pausedHolder = null
                            currentHolder = null
                        }
                    ).apply {
                        show(root)
                    }
                } ?: run {
                    mindboxLogE("failed to show inApp: currentRoot is null")
                }
            }
            is InAppType.Snackbar ->  {
                currentActivity?.root?.let { root ->
                    @Suppress("UNCHECKED_CAST")
                    currentHolder = SnackbarInAppViewHolder(wrapper as InAppTypeWrapper<InAppType.Snackbar>,
                        inAppCallback = InAppCallbackWrapper(inAppCallback) {
                            pausedHolder?.hide()
                            pausedHolder = null
                            currentHolder = null
                        }
                    ).apply {
                        show(root)
                    }
                } ?: run {
                    mindboxLogE("failed to show inApp: currentRoot is null")
                }
            }
        }
    }

    private val Activity?.root: ViewGroup?
        get() = this?.window?.decorView?.rootView as ViewGroup?
}

