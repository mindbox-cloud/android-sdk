package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import androidx.lifecycle.ProcessLifecycleOwner
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.enumValue
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.inapp.presentation.ShowInAppOutcome
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionGesture
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionService
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionServiceProtocol
import cloud.mindbox.mobile_sdk.inapp.presentation.view.motion.MotionStartResult
import cloud.mindbox.mobile_sdk.inapp.data.validators.HapticRequestValidator
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.safeAs
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

internal interface WebViewBridgeHost {

    val hostActivity: Activity?
    val hostTags: Map<String, String>?
    val hostPage: MindboxWebPage
    val hostInAppId: String
    val isAskerAlive: Boolean

    fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit)

    val closeCapability: ((BridgeMessage.Request) -> String)?

    val hideCapability: (() -> String)?

    fun requireCanShowInApp()
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
    private val inAppInteractor by mindboxInject { inAppInteractor }
    private val inAppMessageManager by mindboxInject { inAppMessageManager }

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

    private val pendingShowInAppOutcomes = mutableSetOf<CompletableDeferred<ShowInAppOutcome>>()
    private var isTornDown = false

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
            registerSuspend(WebViewAction.SHOW_IN_APP, ::handleShowInAppAction)
            registerSuspend(WebViewAction.FILTER_SHOWABLE_INAPPS, ::handleFilterShowableInappsAction)
        }
    }

    fun tearDown() {
        cancelPendingShowInAppOutcomes()
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
        linkRouter.executeOpenLink(message.payload).getOrThrow()
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handlePermissionAction(message: BridgeMessage.Request): String {
        val payload: String = message.payload ?: BridgeMessage.EMPTY_PAYLOAD
        val typeValue: Any? = readBridgePayload { JSONObject(payload).get(PERMISSION_PAYLOAD_TYPE_FIELD_NAME) }
        val typeString: String = requireBridgeNotNull((typeValue as? String)?.takeIf { it.isNotEmpty() }, BridgeErrorCode.INVALID_PAYLOAD) {
            "Permission type must be a non-empty string, got $typeValue"
        }
        val type: PermissionType = requireBridgeNotNull(runCatching { typeString.enumValue<PermissionType>() }.getOrNull(), BridgeErrorCode.UNSUPPORTED_VALUE) {
            "Unknown permission type: $typeString"
        }

        val activity: Activity = requireBridgeNotNull(host.hostActivity, BridgeErrorCode.PERMISSION_FAILED) {
            "Not found activity for permission request"
        }

        val permissionRequestResult: PermissionActionResponse = try {
            webViewPermissionRequester.requestPermission(activity, type)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw BridgeRefusalException(BridgeErrorCode.PERMISSION_FAILED, error.message ?: "Permission request failed", error)
        }
        return gson.toJson(permissionRequestResult)
    }

    private fun handleSettingsOpenAction(message: BridgeMessage.Request): String {
        val payload: String = message.payload ?: BridgeMessage.EMPTY_PAYLOAD
        val settingsOpenRequest: SettingsOpenRequest = requireBridgeNotNull(gson.fromJson<SettingsOpenRequest>(payload).getOrNull(), BridgeErrorCode.INVALID_PAYLOAD) {
            "settings.open payload is not an object: $payload"
        }

        val target: String = requireBridgeNotNull(settingsOpenRequest.target?.asNonEmptyStringOrNull(), BridgeErrorCode.INVALID_PAYLOAD) {
            "Settings target must be a non-empty string, got ${settingsOpenRequest.target}"
        }
        val targetType: SettingsOpenTargetType = requireBridgeNotNull(runCatching { target.enumValue<SettingsOpenTargetType>() }.getOrNull(), BridgeErrorCode.UNSUPPORTED_VALUE) {
            "Unknown settings target: $target"
        }
        val activity: Activity = requireBridgeNotNull(host.hostActivity, BridgeErrorCode.OPEN_FAILED) {
            "Not found activity for open settings"
        }

        when (targetType) {
            SettingsOpenTargetType.NOTIFICATIONS ->
                mindboxNotificationManager.openNotificationSettings(activity, settingsOpenRequest.channelId)
            SettingsOpenTargetType.APPLICATION ->
                mindboxNotificationManager.openApplicationSettings(activity)
        }
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handleShowInAppAction(message: BridgeMessage.Request): String {
        val payload = gson.fromJson<JsonObject>(message.payload).getOrNull()
            ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, SHOW_IN_APP_INVALID_PAYLOAD)
        val requestedId: String = requireBridgeNotNull(
            payload.getOrNull(SHOW_IN_APP_ID_FIELD)
                ?.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf { id -> id.isNotEmpty() },
            BridgeErrorCode.INVALID_PAYLOAD,
        ) { SHOW_IN_APP_INVALID_PAYLOAD }
        val extraParams: Map<String, JsonElement> =
            payload.getOrNull(SHOW_IN_APP_PARAMS_FIELD).safeAs<JsonObject>()
                ?.entrySet()?.associate { (key, value) -> key to value }
                ?: emptyMap()
        mindboxLogI("[WebView] Bridge: showInApp from ${host.hostInAppId}: inappId=$requestedId with ${extraParams.size} param(s)")
        host.requireCanShowInApp()
        val outcome = registerShowInAppOutcome()
            ?: throw CancellationException("The page of ${host.hostInAppId} is torn down")
        try {
            inAppMessageManager.showInAppById(requestedId, extraParams, askerAlive = { host.isAskerAlive }) { result ->
                outcome.complete(result)
            }
            return when (val result = outcome.await()) {
                ShowInAppOutcome.Shown -> BridgeMessage.SUCCESS_PAYLOAD
                is ShowInAppOutcome.NotShown -> {
                    mindboxLogI("[WebView] Bridge: showInApp for $requestedId ended without a show: ${result.code}")
                    throw BridgeRefusalException(result.code, "showInApp for $requestedId ended without a show")
                }
            }
        } finally {
            synchronized(pendingShowInAppOutcomes) { pendingShowInAppOutcomes.remove(outcome) }
        }
    }

    private fun registerShowInAppOutcome(): CompletableDeferred<ShowInAppOutcome>? =
        synchronized(pendingShowInAppOutcomes) {
            if (isTornDown) return null
            CompletableDeferred<ShowInAppOutcome>().also(pendingShowInAppOutcomes::add)
        }

    private fun cancelPendingShowInAppOutcomes() {
        val abandoned = synchronized(pendingShowInAppOutcomes) {
            isTornDown = true
            pendingShowInAppOutcomes.toList().also { pendingShowInAppOutcomes.clear() }
        }
        abandoned.forEach { outcome -> outcome.cancel() }
    }

    private suspend fun handleFilterShowableInappsAction(message: BridgeMessage.Request): String {
        val askedIds = runCatching {
            gson.fromJson(message.payload, JsonObject::class.java)?.get(INAPP_IDS_FIELD) as? JsonArray
        }.getOrNull() ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "no '$INAPP_IDS_FIELD' array in the payload")
        val requestedIds = askedIds.mapNotNull { element ->
            runCatching { element.asJsonPrimitive.takeIf { primitive -> primitive.isString }?.asString }
                .getOrNull()
        }
        if (requestedIds.size != askedIds.size()) {
            mindboxLogE(
                "[WebView] Bridge: ${askedIds.size() - requestedIds.size} of ${askedIds.size()} " +
                    "asked ids are not strings, skipping them"
            )
        }
        val showableIds = inAppInteractor.filterShowableInAppIds(host.hostInAppId, requestedIds)
        mindboxLogI(
            "[WebView] Bridge: filterShowableInapps from ${host.hostInAppId}: ${requestedIds.size} id(s) asked, " +
                "${showableIds.size} allowed"
        )
        return gson.toJson(InAppIdsPayload(showableIds))
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
        val payload = requireBridgeNotNull(message.payload, BridgeErrorCode.INVALID_PAYLOAD) { "Missing payload" }
        val gestures = parseMotionGestures(payload)
        val result = getOrCreateMotionService().startMonitoring(gestures)
        if (result.allUnavailable) {
            throw BridgeRefusalException(
                BridgeErrorCode.GESTURES_UNAVAILABLE,
                "No sensors available for: ${result.unavailable.joinToString { it.value }}",
            )
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
        val entries: JSONArray = readBridgePayload { JSONObject(payload).optJSONArray(MOTION_GESTURES_KEY) }
            ?.takeIf { array -> array.length() > 0 }
            ?: throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, "No gestures provided. Available: shake, flip")
        val names: List<String> = (0 until entries.length()).map { i ->
            requireBridgeNotNull((entries.opt(i) as? String)?.takeIf { it.isNotEmpty() }, BridgeErrorCode.INVALID_PAYLOAD) {
                "Gesture #$i is not a non-empty string. Available: shake, flip"
            }
        }
        return names.map { name ->
            requireBridgeNotNull(runCatching { name.enumValue<MotionGesture>() }.getOrNull(), BridgeErrorCode.UNSUPPORTED_VALUE) {
                "Unknown gesture '$name'. Available: shake, flip"
            }
        }.toSet()
    }

    private fun JsonElement.asNonEmptyStringOrNull(): String? =
        takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotEmpty() }

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

    private data class InAppIdsPayload(
        @SerializedName(INAPP_IDS_FIELD)
        val inappIds: List<String>?,
    )

    private data class SettingsOpenRequest(
        @SerializedName("target")
        val target: JsonElement?,
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
        private const val INAPP_IDS_FIELD = "inappIds"
        private const val SHOW_IN_APP_ID_FIELD = "inappId"
        private const val SHOW_IN_APP_PARAMS_FIELD = "params"
        private const val SHOW_IN_APP_INVALID_PAYLOAD = "Invalid payload: missing or empty 'inappId'"
    }
}
