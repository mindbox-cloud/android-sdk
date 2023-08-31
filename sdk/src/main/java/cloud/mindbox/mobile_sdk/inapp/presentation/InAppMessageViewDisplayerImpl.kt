package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.*
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.ModalWindowInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.SnackbarInAppViewHolder
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import java.util.*


internal class InAppMessageViewDisplayerImpl(private val inAppImageSizeStorage: InAppImageSizeStorage) :
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
        mindboxLogI("onResumeCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        if (pausedHolder?.isActive == true) {
            pausedHolder?.wrapper?.let { wrapper ->
                mindboxLogI("trying to restore in-app with id ${pausedHolder?.wrapper?.inAppType?.inAppId}")
                showInAppMessage(
                    wrapper.copy(
                        onInAppShown = { mindboxLogI("Skip InApp.Show for restored inApp") },
                    )
                )
            }
        } else {
            tryShowInAppFromQueue()
        }
    }

    override fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        mindboxLogI("registerCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        tryShowInAppFromQueue()
    }

    private fun tryShowInAppFromQueue() {
        if (inAppQueue.isNotEmpty() && !isInAppActive()) {
            with(inAppQueue.pop()) {
                mindboxLogI("trying to show in-app with id ${inAppType.inAppId} from queue")
                showInAppMessage(this)
            }
        }
    }

    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        this.inAppCallback = inAppCallback
    }

    override fun isInAppActive(): Boolean = currentHolder?.isActive ?: false

    override fun onStopCurrentActivity(activity: Activity) {
        mindboxLogI("onStopCurrentActivity: ${activity.hashCode()}")
        pausedHolder?.hide()
    }

    override fun onPauseCurrentActivity(activity: Activity) {
        mindboxLogI("onPauseCurrentActivity: ${activity.hashCode()}")
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

            is InAppType.Snackbar -> {
                InAppTypeWrapper(inAppType, onInAppClick, onInAppShown)
            }
        }
        if (isUiPresent()) {
            mindboxLogI("In-app with id ${inAppType.inAppId} is going to be shown immediately")
            showInAppMessage(wrapper)
        } else {
            inAppQueue.add(wrapper)
            mindboxLogI(
                "In-app with id ${inAppType.inAppId} is added to showing queue and will be shown later"
            )
        }
    }

    private fun showInAppMessage(wrapper: InAppTypeWrapper<InAppType>) {
        when (wrapper.inAppType) {
            is InAppType.ModalWindow -> {
                currentActivity?.root?.let { root ->
                    @Suppress("UNCHECKED_CAST")
                    currentHolder =
                        ModalWindowInAppViewHolder(wrapper = wrapper as InAppTypeWrapper<InAppType.ModalWindow>,
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

            is InAppType.Snackbar -> {
                currentActivity?.root?.let { root ->
                    @Suppress("UNCHECKED_CAST")
                    currentHolder = SnackbarInAppViewHolder(
                        wrapper = wrapper as InAppTypeWrapper<InAppType.Snackbar>,
                        inAppCallback = InAppCallbackWrapper(inAppCallback) {
                            pausedHolder?.hide()
                            pausedHolder = null
                            currentHolder = null
                        },
                        inAppImageSizeStorage = inAppImageSizeStorage
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

