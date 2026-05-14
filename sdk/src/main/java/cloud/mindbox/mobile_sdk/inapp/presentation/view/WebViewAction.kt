package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import com.google.gson.annotations.SerializedName
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

@InternalMindboxApi
internal typealias BridgeMessageHandler = (BridgeMessage.Request) -> String

@InternalMindboxApi
internal typealias BridgeSuspendMessageHandler = suspend (BridgeMessage.Request) -> String

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

    fun hasSuspendHandler(actionValue: WebViewAction): Boolean {
        return suspendHandlersByActionValue.containsKey(actionValue)
    }

    fun handleRequest(message: BridgeMessage.Request): Result<String> {
        return runCatching {
            handlersByActionValue[message.action]?.invoke(message)
                ?: throw IllegalArgumentException("No handler for action ${message.action}")
        }
    }

    suspend fun handleRequestSuspend(message: BridgeMessage.Request): Result<String> {
        return runCatching {
            suspendHandlersByActionValue[message.action]?.invoke(message)
                ?: throw IllegalArgumentException("No suspend handler for action ${message.action}")
        }
    }
}
