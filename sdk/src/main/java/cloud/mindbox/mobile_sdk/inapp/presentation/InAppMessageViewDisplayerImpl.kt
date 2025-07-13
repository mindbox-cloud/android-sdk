package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.addUnique
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
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
import cloud.mindbox.mobile_sdk.utils.Stopwatch
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

    override fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        mindboxLogI("onResumeCurrentActivity: ${activity.hashCode()}")
        currentActivity = activity

        val holder = pausedHolder ?: currentHolder
        if (holder != null) {
            pausedHolder?.wrapper?.let { wrapper ->
                mindboxLogI("trying to restore in-app with id ${pausedHolder?.wrapper?.inAppType?.inAppId}")
                showInAppMessage(
                    wrapper.copy(
                        onInAppShown = {
                            mindboxLogI("Skip InApp.Show for restored inApp")
                            currentActivity?.postDelayedAnimation {
                                pausedHolder?.hide()
                            }
                        },
                    ),
                    isRestored = true
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
            inAppQueue.pop().let {
                val duration = Stopwatch.track(Stopwatch.INIT_SDK)
                mindboxLogI("trying to show in-app with id ${it.inAppType.inAppId} from queue $duration after init")
                showInAppMessage(it)
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

    private fun getWebViewFromPayload(inAppType: InAppType, inAppId: String): InAppType.WebView? {
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
        onInAppClick: OnInAppClick,
        onInAppShown: OnInAppShown,
    ) {
        val wrapper = getWebViewFromPayload(inAppType, inAppType.inAppId)?.let {
            InAppTypeWrapper(it, onInAppClick, onInAppShown)
        } ?: InAppTypeWrapper(inAppType, onInAppClick, onInAppShown)

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
        val callbackWrapper = InAppCallbackWrapper(inAppCallback) {
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
            currentHolder?.hide()
            currentHolder = null
            pausedHolder?.hide()
            pausedHolder = null
            inAppQueue.clear()
            isActionExecuted = false
        }
    }
}
