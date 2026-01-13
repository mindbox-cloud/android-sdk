package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.addUnique
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.*
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.ModalWindowInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.SnackbarInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewInAppViewHolder
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.postDelayedAnimation
import cloud.mindbox.mobile_sdk.root
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.LinkedList

internal interface MindboxView {

    val container: ViewGroup

    fun requestPermission()
}

internal class InAppMessageViewDisplayerImpl(private val inAppImageSizeStorage: InAppImageSizeStorage) :
    InAppMessageViewDisplayer {

    companion object {
        internal var isActionExecuted: Boolean = false
    }

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
    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }
    private val gson by mindboxInject { gson }

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
                                pausedHolder?.hide()
                            }
                        }
                        )
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getWebViewFromPayload(inAppType: InAppType, inAppId: String): InAppType.WebView? {
        val layer = when (inAppType) {
            is InAppType.Snackbar -> inAppType.layers.firstOrNull()
            is InAppType.ModalWindow -> inAppType.layers.firstOrNull()
            is InAppType.WebView -> return inAppType
        }
        if (layer !is Layer.ImageLayer) {
            return null
        }

        val payload = when (layer.action) {
            is Layer.ImageLayer.Action.RedirectUrlAction -> layer.action.payload
            is Layer.ImageLayer.Action.PushPermissionAction -> layer.action.payload
        }
        runCatching {
            val layerDto = gson.fromJson<BackgroundDto.LayerDto.WebViewLayerDto>(payload).getOrThrow()
            requireNotNull(layerDto.type)
            requireNotNull(layerDto.contentUrl)
            requireNotNull(layerDto.baseUrl)
            Layer.WebViewLayer(
                baseUrl = layerDto.baseUrl,
                contentUrl = layerDto.contentUrl,
                type = layerDto.type,
                params = layerDto.params ?: emptyMap()
            )
        }.getOrNull()?.let { webView ->
            return InAppType.WebView(
                inAppId = inAppId,
                type = PayloadDto.WebViewDto.WEBVIEW_JSON_NAME,
                layers = listOf(webView),
            )
        }

        return null
    }

    override fun tryShowInAppMessage(
        inAppType: InAppType,
        inAppActionCallbacks: InAppActionCallbacks
    ) {
        val wrapper = getWebViewFromPayload(inAppType, inAppType.inAppId)?.let {
            InAppTypeWrapper(it, inAppActionCallbacks)
        } ?: InAppTypeWrapper(inAppType, inAppActionCallbacks)

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
        if (!isRestored) isActionExecuted = false
        val callbackWrapper = InAppCallbackWrapper(inAppCallback) {
            wrapper.inAppActionCallbacks.onInAppDismiss.onDismiss()
            pausedHolder?.hide()
            pausedHolder = null
            currentHolder = null
        }

        @Suppress("UNCHECKED_CAST")
        currentHolder = when (wrapper.inAppType) {
            is InAppType.WebView -> WebViewInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.WebView>,
                inAppCallback = callbackWrapper
            )

            is InAppType.ModalWindow -> ModalWindowInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.ModalWindow>,
                inAppCallback = callbackWrapper
            )

            is InAppType.Snackbar -> SnackbarInAppViewHolder(
                wrapper = wrapper as InAppTypeWrapper<InAppType.Snackbar>,
                inAppCallback = callbackWrapper,
                inAppImageSizeStorage = inAppImageSizeStorage,
                isFirstShow = !isRestored
            )
        }

        currentActivity?.root?.let { root ->
            currentHolder?.show(object : MindboxView {
                override val container: ViewGroup
                    get() = root

                override fun requestPermission() {
                    currentActivity?.let { activity ->
                        mindboxNotificationManager.requestPermission(activity = activity)
                    }
                }
            })
        } ?: run {
            mindboxLogE("failed to show inApp: currentRoot is null")
        }
    }

    override fun hideCurrentInApp() {
        loggingRunCatching {
            if (isInAppActive()) {
                currentHolder?.wrapper?.inAppActionCallbacks
                    ?.copy(onInAppDismiss = { mindboxLogI("Do not save the closing timestamp for in-app as it's restored automatically when the session is reopened") })
                    ?.onInAppDismiss
                    ?.onDismiss()
            }
            currentHolder?.apply {
                hide()
                release()
            }
            currentHolder = null
            pausedHolder?.apply {
                hide()
                release()
            }
            pausedHolder = null
            inAppQueue.clear()
            isActionExecuted = false
        }
    }
}
