package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.addUnique
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.executeWithFailureTracking
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendPresentationFailure
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.*
import cloud.mindbox.mobile_sdk.inapp.presentation.view.ActivityBackPressRegistrar
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BackPressRegistrar
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.ModalWindowInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.SnackbarInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewInAppViewHolder
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.postDelayedAnimation
import cloud.mindbox.mobile_sdk.root
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.LinkedList

internal interface MindboxView {

    val container: ViewGroup

    val backPressRegistrar: BackPressRegistrar

    fun requestPermission()
}

internal class InAppMessageViewDisplayerImpl(
    private val inAppImageSizeStorage: InAppImageSizeStorage
) :
    InAppMessageViewDisplayer {

    companion object {
        internal var isActionExecuted: Boolean = false
    }

    private var currentActivity: Activity? = null

    private val defaultCallback: InAppCallback = ComposableInAppCallback(
        UrlInAppCallback(),
        DeepLinkInAppCallback(),
        CopyPayloadInAppCallback(),
        LoggingInAppCallback()
    )

    private var inAppCallback: InAppCallback = defaultCallback

    private val inAppQueue = LinkedList<InAppTypeWrapper<InAppType>>()

    private var currentHolder: InAppViewHolder<*>? = null
    private var pausedHolder: InAppViewHolder<*>? = null
    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }
    private val inAppFailureTracker: InAppFailureTracker by mindboxInject { inAppFailureTracker }

    private fun isUiPresent(): Boolean = currentActivity?.isFinishing?.not() ?: false

    override fun onResumeCurrentActivity(activity: Activity, isNeedToShow: () -> Boolean, onAppResumed: () -> Unit) {
        mindboxLogI("onResumeCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        val holder = pausedHolder ?: currentHolder
        if (holder != null) {
            pausedHolder?.wrapper?.let { wrapper ->
                mindboxLogI("trying to restore in-app with id ${pausedHolder?.wrapper?.inAppType?.inAppId}")
                showInAppMessage(
                    wrapper = wrapper.copy(
                        inAppActionCallbacks = wrapper.inAppActionCallbacks.copy(onInAppShown = {
                            mindboxLogI("Skip InApp.Show for restored inApp")
                            currentActivity?.postDelayedAnimation {
                                pausedHolder?.onClose()
                            }
                        })
                    ),
                    isRestored = true
                )
            }
        } else {
            tryShowInAppFromQueue(isNeedToShow)
        }
        onAppResumed()
    }

    override fun registerCurrentActivity(activity: Activity) {
        mindboxLogI("registerCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity
        tryShowInAppFromQueue { true }
    }

    private fun tryShowInAppFromQueue(isSessionActive: () -> Boolean) {
        if (inAppQueue.isNotEmpty() && !isInAppActive() && isSessionActive()) {
            inAppQueue.pop().let {
                val duration = Stopwatch.track(Stopwatch.INIT_SDK)
                mindboxLogI("trying to show in-app with id ${it.inAppType.inAppId} from queue $duration after init")
                showInAppMessage(it)
            }
        }
        inAppQueue.clear()
    }

    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        this.inAppCallback = inAppCallback
    }

    override fun unregisterInAppCallback() {
        this.inAppCallback = defaultCallback
    }

    override fun isInAppActive(): Boolean = currentHolder?.isActive ?: false

    override fun onStopCurrentActivity(activity: Activity) {
        mindboxLogI("onStopCurrentActivity: ${activity.hashCode()}")
        pausedHolder?.onStop()
    }

    override fun onPauseCurrentActivity(activity: Activity) {
        mindboxLogI("onPauseCurrentActivity: ${activity.hashCode()}")
        if (currentActivity == activity) {
            currentActivity = null
        }
        val holderToPause = currentHolder ?: return
        pausedHolder?.onClose()
        pausedHolder = holderToPause
        currentHolder = null
    }

    override fun tryShowInAppMessage(
        inAppType: InAppType,
        inAppActionCallbacks: InAppActionCallbacks,
        onRenderStart: () -> Unit,
    ) {
        val wrapper = InAppTypeWrapper(inAppType, inAppActionCallbacks, onRenderStart)

        if (isUiPresent() && currentHolder == null && pausedHolder == null) {
            val duration = Stopwatch.track(Stopwatch.INIT_SDK)
            mindboxLogI("In-app with id ${inAppType.inAppId} is going to be shown immediately $duration after init")
            showInAppMessage(wrapper)
        } else {
            if (currentHolder?.wrapper?.inAppType?.inAppId == wrapper.inAppType.inAppId) {
                mindboxLogI(
                    "In-app with id ${inAppType.inAppId} is not added to showing queue as duplicate"
                )
            } else if (inAppQueue.addUnique(wrapper) { it.inAppType.inAppId == wrapper.inAppType.inAppId }) {
                mindboxLogI(
                    "In-app with id ${inAppType.inAppId} is added to showing queue and will be shown later"
                )
            } else {
                mindboxLogW(
                    "In-app with id ${inAppType.inAppId} already exists in showing queue!"
                )
            }
        }
    }

    private fun showInAppMessage(
        wrapper: InAppTypeWrapper<InAppType>,
        isRestored: Boolean = false,
    ) {
        if (!isRestored) {
            wrapper.onRenderStart()
            isActionExecuted = false
        }
        if (isRestored && tryReattachRestoredInApp(wrapper.inAppType.inAppId)) return
        if (isRestored) {
            pausedHolder?.onClose()
            pausedHolder = null
        }

        val callbackWrapper = InAppCallbackWrapper({ inAppCallback }) {
            wrapper.inAppActionCallbacks.onInAppDismiss.onDismiss()
        }
        val controller = InAppViewHolder.InAppController { closeInApp() }

        @Suppress("UNCHECKED_CAST")
        currentHolder = when (wrapper.inAppType) {
            is InAppType.WebView -> WebViewInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.WebView>,
                controller = controller,
                inAppCallback = callbackWrapper
            )

            is InAppType.ModalWindow -> ModalWindowInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.ModalWindow>,
                controller = controller,
                inAppCallback = callbackWrapper
            )

            is InAppType.Snackbar -> SnackbarInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.Snackbar>,
                controller = controller,
                inAppCallback = callbackWrapper,
                inAppImageSizeStorage = inAppImageSizeStorage,
                isFirstShow = !isRestored
            )
        }

        currentActivity?.root?.let { root ->
            inAppFailureTracker.executeWithFailureTracking(
                inAppId = wrapper.inAppType.inAppId,
                failureReason = FailureReason.PRESENTATION_FAILED,
                errorDescription = "Error when trying draw inapp",
                onFailure = ::closeInApp
            ) {
                currentHolder?.show(createMindboxView(root))
            }
        } ?: run {
            inAppFailureTracker.sendPresentationFailure(
                inAppId = wrapper.inAppType.inAppId,
                errorDescription = "currentRoot is null"
            )
        }
    }

    private fun tryReattachRestoredInApp(inAppId: String): Boolean {
        val restoredHolder: InAppViewHolder<*> = pausedHolder
            ?.takeIf { it.canReuseOnRestore(inAppId) }
            ?: return false
        currentHolder = restoredHolder
        pausedHolder = null
        val root: ViewGroup = currentActivity?.root ?: run {
            inAppFailureTracker.sendPresentationFailure(
                inAppId = inAppId,
                errorDescription = "failed to reattach inApp: currentRoot is null"
            )
            return true
        }
        inAppFailureTracker.executeWithFailureTracking(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDescription = "Error when trying reattach InApp",
            onFailure = ::closeInApp,
        ) {
            restoredHolder.reattach(createMindboxView(root))
        }
        return true
    }

    private fun createMindboxView(root: ViewGroup): MindboxView =
        object : MindboxView {
            override val container: ViewGroup = root
            override val backPressRegistrar: BackPressRegistrar =
                ActivityBackPressRegistrar(activityProvider = { currentActivity })

            override fun requestPermission() {
                currentActivity?.let { mindboxNotificationManager.requestPermission(activity = it) }
            }
        }

    override fun dismissCurrentInApp() {
        loggingRunCatching {
            if (isInAppActive()) {
                currentHolder?.wrapper?.inAppActionCallbacks
                    ?.copy(onInAppDismiss = { mindboxLogI("Do not save the closing timestamp for in-app as it's restored automatically when the session is reopened") })
                    ?.onInAppDismiss
                    ?.onDismiss()
            }
        }
        closeInApp()
    }

    private fun closeInApp() {
        loggingRunCatching {
            currentHolder?.onClose()
            currentHolder = null
            pausedHolder?.onClose()
            pausedHolder = null
            inAppQueue.clear()
            isActionExecuted = false
        }
    }
}
