package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import androidx.lifecycle.ProcessLifecycleOwner
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.enumValue
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionGesture
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionService
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionServiceProtocol
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionStartResult
import cloud.mindbox.mobile_sdk.inapp.data.validators.HapticRequestValidator
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.annotations.SerializedName
import org.json.JSONObject

internal interface WebViewBridgeHost {

    val hostActivity: Activity?
    val hostTags: Map<String, String>?
    val hostPage: MindboxWebPage
    val isUserPresent: Boolean

    fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit)

    val closeCapability: ((BridgeMessage.Request) -> String)?

    val hideCapability: (() -> String)?
}

internal class WebViewCommonBridgeActions(
    private val host: WebViewBridgeHost,
) {

    private val appContext by mindboxInject { appContext }
    private val gson by mindboxInject { this.gson }
    private val timeProvider by mindboxInject { timeProvider }
    private val permissionManager by mindboxInject { permissionManager }
    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }
    private val webPageRegistry by mindboxInject { webPageRegistry }

    private val operationExecutor: WebViewOperationExecutor by lazy { MindboxWebViewOperationExecutor(gson) }
    private val linkRouter: WebViewLinkRouter by lazy { MindboxWebViewLinkRouter(appContext) }
    private val localStateStore: WebViewLocalStateStore by lazy { WebViewLocalStateStore(appContext) }
    private val hapticRequestValidator: HapticRequestValidator by lazy { HapticRequestValidator() }
    private val hapticFeedbackExecutorLazy = lazy { HapticFeedbackExecutorImpl(appContext) }
    private val hapticFeedbackExecutor: HapticFeedbackExecutor by hapticFeedbackExecutorLazy
    private val webViewPermissionRequester: WebViewPermissionRequester by lazy {
        WebViewPermissionRequesterImpl(context = appContext, permissionManager = permissionManager)
    }
    private var motionService: MotionServiceProtocol? = null

    fun register(handlers: WebViewActionHandlers) {
        handlers.apply {
            register(WebViewAction.LOG) { message ->
                mindboxLogI("JS: ${message.payload}")
                BridgeMessage.SUCCESS_PAYLOAD
            }
            register(WebViewAction.ASYNC_OPERATION, ::handleAsyncOperationAction)
            registerSuspend(WebViewAction.SYNC_OPERATION, ::handleSyncOperationAction)
            register(WebViewAction.OPEN_LINK, ::handleOpenLinkAction)
            register(WebViewAction.SETTINGS_OPEN, ::handleSettingsOpenAction)
            registerSuspend(WebViewAction.PERMISSION_REQUEST, ::handlePermissionAction)
            register(WebViewAction.HAPTIC, ::handleHapticAction)
            register(WebViewAction.MOTION_START, ::handleMotionStartAction)
            register(WebViewAction.MOTION_STOP) { handleMotionStopAction() }
            registerSuspend(WebViewAction.LOCAL_STATE_GET) { message ->
                localStateStore.getState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
            }
            registerSuspend(WebViewAction.LOCAL_STATE_SET, ::handleLocalStateSetAction)
            registerSuspend(WebViewAction.LOCAL_STATE_INIT) { message ->
                localStateStore.initState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
            }
            register(WebViewAction.CLOSE) { message ->
                host.closeCapability?.invoke(message) ?: run {
                    mindboxLogI("[WebView] Bridge: 'close' has no window to reach here, ignoring")
                    BridgeMessage.SUCCESS_PAYLOAD
                }
            }
            register(WebViewAction.HIDE) {
                host.hideCapability?.invoke() ?: run {
                    mindboxLogI("[WebView] Bridge: 'hide' has no window to reach here, ignoring")
                    BridgeMessage.SUCCESS_PAYLOAD
                }
            }
        }
    }

    fun tearDown() {
        if (hapticFeedbackExecutorLazy.isInitialized()) {
            hapticFeedbackExecutor.cancel()
        }
        motionService?.stopMonitoring()
    }

    private fun handleAsyncOperationAction(message: BridgeMessage.Request): String {
        operationExecutor.executeAsyncOperation(appContext, message.payload, host.hostTags)
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handleSyncOperationAction(message: BridgeMessage.Request): String {
        return operationExecutor.executeSyncOperation(message.payload, host.hostTags)
    }

    private fun handleOpenLinkAction(message: BridgeMessage.Request): String {
        linkRouter.executeOpenLink(message.payload)
            .getOrElse { error: Throwable ->
                throw IllegalStateException(error.message ?: "Navigation error")
            }
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handlePermissionAction(message: BridgeMessage.Request): String {
        val payload: String = message.payload ?: BridgeMessage.EMPTY_PAYLOAD
        val typeString: String? = JSONObject(payload).getString(PERMISSION_PAYLOAD_TYPE_FIELD_NAME)
        val type: PermissionType? = runCatching { typeString.enumValue<PermissionType>() }.getOrNull()
        requireNotNull(type) { "Unknown permission type: $typeString" }

        val activity: Activity? = host.hostActivity
        checkNotNull(activity) { "Not found activity for permission request" }

        val permissionRequestResult: PermissionActionResponse = webViewPermissionRequester.requestPermission(
            activity,
            type
        )
        return gson.toJson(permissionRequestResult)
    }

    private fun handleSettingsOpenAction(message: BridgeMessage.Request): String {
        val payload: String = message.payload ?: BridgeMessage.EMPTY_PAYLOAD
        val settingsOpenRequest: SettingsOpenRequest? = gson.fromJson<SettingsOpenRequest>(payload).getOrNull()
        requireNotNull(settingsOpenRequest)

        val targetType = settingsOpenRequest.target.enumValue<SettingsOpenTargetType>()
        val activity: Activity? = host.hostActivity
        checkNotNull(activity) { "Not found activity for open settings" }

        when (targetType) {
            SettingsOpenTargetType.NOTIFICATIONS ->
                mindboxNotificationManager.openNotificationSettings(activity, settingsOpenRequest.channelId)
            SettingsOpenTargetType.APPLICATION ->
                mindboxNotificationManager.openApplicationSettings(activity)
        }
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private fun handleHapticAction(message: BridgeMessage.Request): String {
        val request = parseHapticRequest(message.payload)
        if (!hapticRequestValidator.isValid(request)) return BridgeMessage.SUCCESS_PAYLOAD
        hapticFeedbackExecutor.execute(request = request)
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handleLocalStateSetAction(message: BridgeMessage.Request): String {
        val answer = localStateStore.setState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
        webPageRegistry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, answer, excludingAuthor = host.hostPage)
        return answer
    }

    private fun handleMotionStartAction(message: BridgeMessage.Request): String {
        val payload = requireNotNull(message.payload) { "Missing payload" }
        val gestures = parseMotionGestures(payload)
        require(gestures.isNotEmpty()) { "No valid gestures provided. Available: shake, flip" }
        val result = getOrCreateMotionService().startMonitoring(gestures)
        require(!result.allUnavailable) {
            "No sensors available for: ${result.unavailable.joinToString { it.value }}"
        }
        return buildMotionStartPayload(result)
    }

    private fun handleMotionStopAction(): String {
        motionService?.stopMonitoring()
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private fun buildMotionStartPayload(result: MotionStartResult): String {
        if (result.unavailable.isEmpty()) return BridgeMessage.SUCCESS_PAYLOAD
        return gson.toJson(
            MotionStartPayload(unavailable = result.unavailable.map { it.value })
        )
    }

    private fun parseMotionGestures(payload: String): Set<MotionGesture> {
        return loggingRunCatching(defaultValue = emptySet()) {
            val array = JSONObject(payload).optJSONArray(MOTION_GESTURES_KEY)
                ?: return@loggingRunCatching emptySet()
            (0 until array.length())
                .mapNotNull { i -> array.optString(i).enumValue<MotionGesture>() }
                .toSet()
        }
    }

    private fun sendMotionEvent(gesture: MotionGesture, data: Map<String, String>) {
        val payload = JSONObject()
            .apply {
                put(MOTION_GESTURE_KEY, gesture.value)
                data.forEach { (key, value) -> put(key, value) }
            }
            .toString()
        val message: BridgeMessage.Request = BridgeMessage.createAction(
            action = WebViewAction.MOTION_EVENT,
            payload = payload,
        )
        host.sendToPage(message) { error ->
            mindboxLogW("[WebView] Motion: failed to send motion.event to JS: $error")
            motionService?.stopMonitoring()
        }
    }

    private fun getOrCreateMotionService(): MotionServiceProtocol =
        motionService ?: MotionService(
            context = appContext,
            lifecycle = ProcessLifecycleOwner.get().lifecycle,
            timeProvider = timeProvider,
        ).also { service ->
            service.onGestureDetected = { gesture, data ->
                sendMotionEvent(gesture = gesture, data = data)
            }
            motionService = service
        }

    private data class MotionStartPayload(
        @SerializedName("success")
        val success: Boolean = true,
        @SerializedName("unavailable")
        val unavailable: List<String>? = null,
    )

    private data class SettingsOpenRequest(
        @SerializedName("target")
        val target: String,
        @SerializedName("channelId")
        val channelId: String?
    )

    private enum class SettingsOpenTargetType {
        NOTIFICATIONS,
        APPLICATION
    }

    private companion object {
        private const val MOTION_GESTURE_KEY = "gesture"
        private const val MOTION_GESTURES_KEY = "gestures"
    }
}
