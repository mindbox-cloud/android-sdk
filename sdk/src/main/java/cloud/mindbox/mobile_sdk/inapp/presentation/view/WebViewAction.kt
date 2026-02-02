package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import com.google.gson.annotations.SerializedName
import java.util.UUID

internal enum class WebViewAction(val value: String) {
    @SerializedName("init")
    INIT("init"),

    @SerializedName("ready")
    READY("ready"),

    @SerializedName("click")
    CLICK("click"),

    @SerializedName("close")
    CLOSE("close"),

    @SerializedName("hide")
    HIDE("hide"),

    @SerializedName("show")
    SHOW("show"),

    @SerializedName("log")
    LOG("log"),

    @SerializedName("alert")
    ALERT("alert"),

    @SerializedName("toast")
    TOAST("toast"),
    UNKNOWN("unknown"),
}

internal sealed class BridgeMessage {
    abstract val version: Int
    abstract val type: String
    abstract val action: WebViewAction
    abstract val payload: String?
    abstract val id: String
    abstract val timestamp: Long

    internal data class Request(
        override val version: Int,
        override val action: WebViewAction,
        override val payload: String?,
        override val id: String,
        override val timestamp: Long,
        override val type: String = TYPE_REQUEST,
    ) : BridgeMessage()

    internal data class Response(
        override val version: Int,
        override val action: WebViewAction,
        override val payload: String?,
        override val id: String,
        override val timestamp: Long,
        override val type: String = TYPE_RESPONSE,
    ) : BridgeMessage()

    internal data class Error(
        override val version: Int,
        override val action: WebViewAction,
        override val payload: String?,
        override val id: String,
        override val timestamp: Long,
        override val type: String = TYPE_ERROR,
    ) : BridgeMessage()

    companion object {
        const val VERSION = 1
        const val EMPTY_PAYLOAD = "{}"
        const val TYPE_FIELD_NAME = "type"
        const val TYPE_REQUEST = "request"
        const val TYPE_RESPONSE = "response"
        const val TYPE_ERROR = "error"

        fun createAction(action: WebViewAction, payload: String): Request =
            Request(
                id = UUID.randomUUID().toString(),
                version = VERSION,
                action = action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )

        fun createResponseAction(message: Request, payload: String?): Response =
            Response(
                id = message.id,
                version = message.version,
                action = message.action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )

        fun createErrorAction(message: Request, payload: String?): Error =
            Error(
                id = message.id,
                version = message.version,
                action = message.action,
                payload = payload,
                timestamp = System.currentTimeMillis(),
            )
    }
}

internal typealias WebViewActionHandler = (BridgeMessage.Request) -> String
internal typealias WebViewSuspendActionHandler = suspend (BridgeMessage.Request) -> String

internal class WebViewActionHandlers {

    private val handlersByActionValue: MutableMap<WebViewAction, WebViewActionHandler> = mutableMapOf()
    private val suspendHandlersByActionValue: MutableMap<WebViewAction, WebViewSuspendActionHandler> = mutableMapOf()

    fun register(actionValue: WebViewAction, handler: WebViewActionHandler) {
        if (handlersByActionValue.containsKey(actionValue)) {
            mindboxLogW("Handler for action $actionValue already registered")
        }
        handlersByActionValue[actionValue] = handler
    }

    fun registerSuspend(actionValue: WebViewAction, handler: WebViewSuspendActionHandler) {
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
