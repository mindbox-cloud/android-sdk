package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import java.util.UUID

@InternalMindboxApi
public enum class WebViewAction {
    @SerializedName("init")
    INIT,

    @SerializedName("ready")
    READY,

    @SerializedName("click")
    CLICK,

    @SerializedName("close")
    CLOSE,

    @SerializedName("hide")
    HIDE,

    @SerializedName("back")
    BACK,

    @SerializedName("log")
    LOG,

    @SerializedName("alert")
    ALERT,

    @SerializedName("toast")
    TOAST,

    @SerializedName("syncOperation")
    SYNC_OPERATION,

    @SerializedName("asyncOperation")
    ASYNC_OPERATION,

    @SerializedName("openLink")
    OPEN_LINK,

    @SerializedName("navigationIntercepted")
    NAVIGATION_INTERCEPTED,

    @SerializedName("localState.get")
    LOCAL_STATE_GET,

    @SerializedName("localState.set")
    LOCAL_STATE_SET,

    @SerializedName("localState.init")
    LOCAL_STATE_INIT,

    @SerializedName("haptic")
    HAPTIC,

    @SerializedName(value = "permission.request")
    PERMISSION_REQUEST,

    @SerializedName(value = "settings.open")
    SETTINGS_OPEN,

    @SerializedName("motion.start")
    MOTION_START,

    @SerializedName("motion.stop")
    MOTION_STOP,

    @SerializedName("motion.event")
    MOTION_EVENT,

    @SerializedName("filterShowableInapps")
    FILTER_SHOWABLE_INAPPS,

    @SerializedName("localState.changed")
    LOCAL_STATE_CHANGED,

    @SerializedName("contentRendered")
    CONTENT_RENDERED,

    @SerializedName("showInApp")
    SHOW_IN_APP,

    @SerializedName("initDataUpdated")
    INIT_DATA_UPDATED,
}

@InternalMindboxApi
public sealed class BridgeMessage {
    public abstract val version: Int
    public abstract val type: String
    public abstract val action: WebViewAction
    public abstract val payload: String?
    public abstract val id: String
    public abstract val timestamp: Long

    public data class Request(
        @SerializedName("version") override val version: Int,
        @SerializedName("action") override val action: WebViewAction,
        @SerializedName("payload") override val payload: String?,
        @SerializedName("id") override val id: String,
        @SerializedName("timestamp") override val timestamp: Long,
        @SerializedName("type") override val type: String = TYPE_REQUEST,
    ) : BridgeMessage()

    public data class Response(
        @SerializedName("version") override val version: Int,
        @SerializedName("action") override val action: WebViewAction,
        @SerializedName("payload") override val payload: String?,
        @SerializedName("id") override val id: String,
        @SerializedName("timestamp") override val timestamp: Long,
        @SerializedName("type") override val type: String = TYPE_RESPONSE,
    ) : BridgeMessage()

    public data class Error(
        @SerializedName("version") override val version: Int,
        @SerializedName("action") override val action: WebViewAction,
        @SerializedName("payload") override val payload: String?,
        @SerializedName("id") override val id: String,
        @SerializedName("timestamp") override val timestamp: Long,
        @SerializedName("type") override val type: String = TYPE_ERROR,
    ) : BridgeMessage()

    public companion object {
        public const val VERSION: Int = 1
        public const val EMPTY_PAYLOAD: String = "{}"
        public const val SUCCESS_PAYLOAD: String = """{"success":true}"""
        public const val UNKNOWN_ERROR_PAYLOAD: String = """{"error":"Unknown error"}"""
        public const val TYPE_FIELD_NAME: String = "type"
        public const val TYPE_REQUEST: String = "request"
        public const val TYPE_RESPONSE: String = "response"
        public const val TYPE_ERROR: String = "error"

        public fun createAction(action: WebViewAction, payload: String): Request =
            Request(
                id = UUID.randomUUID().toString(),
                version = VERSION,
                action = action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )

        public fun createResponseAction(message: Request, payload: String?): Response =
            Response(
                id = message.id,
                version = message.version,
                action = message.action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )

        public fun createErrorAction(message: Request, payload: String?): Error =
            Error(
                id = message.id,
                version = message.version,
                action = message.action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )
    }
}

internal fun Gson.fromBridgeMessage(json: String): BridgeMessage? = fromJson<JsonObject>(json)
    .getOrNull()
    ?.also { envelope ->
        val payload = envelope.getOrNull("payload")
        if (payload != null && (payload.isJsonObject || payload.isJsonArray)) {
            envelope.addProperty("payload", payload.toString())
        }
    }
    ?.let { envelope -> fromJson<BridgeMessage>(envelope).getOrNull() }

/**
 * The one error-payload rule for every surface: a sync-operation failure already carries the
 * structural payload the page's tracker dispatches on and must pass through untouched — running
 * it through [Gson.toJson] again would double-encode it.
 */
internal fun Gson.toBridgeErrorPayload(error: Throwable): String = when (error) {
    is WebViewSyncOperationException -> error.payloadJson
    else -> runCatching { toJson(BridgeErrorPayload(error = requireNotNull(error.message))) }
        .getOrDefault(BridgeMessage.UNKNOWN_ERROR_PAYLOAD)
}

private data class BridgeErrorPayload(
    @SerializedName("error")
    val error: String,
)

@InternalMindboxApi
internal typealias BridgeMessageHandler = (BridgeMessage.Request) -> String

@InternalMindboxApi
internal typealias BridgeSuspendMessageHandler = suspend (BridgeMessage.Request) -> String

internal const val NOBODY_LOOKING_ERROR: String = "Nobody is looking at this page"

internal val WebViewAction.isAcknowledgedWhenUnserved: Boolean
    get() = when (this) {
        WebViewAction.INIT,
        WebViewAction.CLICK,
        WebViewAction.CLOSE,
        WebViewAction.HIDE,
        WebViewAction.BACK,
        WebViewAction.LOG,
        WebViewAction.ALERT,
        WebViewAction.TOAST,
        WebViewAction.MOTION_STOP,
        WebViewAction.CONTENT_RENDERED -> true

        else -> false
    }

internal val WebViewAction.requiresUserPresence: Boolean
    get() = when (this) {
        WebViewAction.OPEN_LINK,
        WebViewAction.SETTINGS_OPEN,
        WebViewAction.PERMISSION_REQUEST,
        WebViewAction.HAPTIC,
        WebViewAction.MOTION_START,
        WebViewAction.SHOW_IN_APP -> true

        else -> false
    }

@InternalMindboxApi
internal class WebViewActionHandlers {

    private val handlersByActionValue: MutableMap<WebViewAction, BridgeMessageHandler> = mutableMapOf()
    private val suspendHandlersByActionValue: MutableMap<WebViewAction, BridgeSuspendMessageHandler> = mutableMapOf()

    fun register(actionValue: WebViewAction, handler: BridgeMessageHandler) {
        if (handlersByActionValue.containsKey(actionValue)) {
            mindboxLogW("Handler for action $actionValue already registered")
        }
        handlersByActionValue[actionValue] = handler
    }

    fun registerSuspend(actionValue: WebViewAction, handler: BridgeSuspendMessageHandler) {
        if (suspendHandlersByActionValue.containsKey(actionValue)) {
            mindboxLogW("Suspend handler for action $actionValue already registered")
        }
        suspendHandlersByActionValue[actionValue] = handler
    }

    fun handler(actionValue: WebViewAction): BridgeMessageHandler? = handlersByActionValue[actionValue]

    fun suspendHandler(actionValue: WebViewAction): BridgeSuspendMessageHandler? =
        suspendHandlersByActionValue[actionValue]
}

internal fun WebViewActionHandlers.dispatch(
    message: BridgeMessage.Request,
    isUserPresent: Boolean,
    isAlive: () -> Boolean = { true },
    launchSuspending: (suspend () -> Unit) -> Unit,
    respond: (String) -> Unit,
    refuse: (Throwable) -> Unit,
) {
    val suspending = suspendHandler(message.action)
    val blocking = handler(message.action)
    if (suspending == null && blocking == null) {
        if (message.action.isAcknowledgedWhenUnserved) {
            mindboxLogI("[WebView] Bridge: '${message.action}' is not served on this surface, acknowledging it")
            respond(BridgeMessage.SUCCESS_PAYLOAD)
        } else {
            refuse(IllegalArgumentException("Action ${message.action} is not served on this surface"))
        }
        return
    }
    if (message.action.requiresUserPresence && !isUserPresent) {
        refuse(IllegalStateException(NOBODY_LOOKING_ERROR))
        return
    }
    when {
        suspending != null -> launchSuspending {
            if (!isAlive()) return@launchSuspending
            runCatching { suspending(message) }
                .onSuccess(respond)
                .onFailure { error ->
                    // A cancelled scope is teardown, not an answer the page is waiting for.
                    if (error is CancellationException) throw error
                    refuse(error)
                }
        }

        blocking != null -> runCatching { blocking(message) }.fold(respond, refuse)
    }
}
