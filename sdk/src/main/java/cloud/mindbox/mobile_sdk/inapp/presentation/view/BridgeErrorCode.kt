package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import org.json.JSONException

/** Error codes of the JS bridge contract. */
internal enum class BridgeErrorCode(val wireValue: String) {
    UNKNOWN_ACTION("unknown_action"),
    NOT_SERVED("not_served"),
    NOT_VISIBLE("not_visible"),
    INVALID_PAYLOAD("invalid_payload"),
    UNSUPPORTED_VALUE("unsupported_value"),
    INVALID_URL("invalid_url"),
    BLOCKED_SCHEME("blocked_scheme"),
    OPEN_FAILED("open_failed"),
    PERMISSION_FAILED("permission_failed"),
    GESTURES_UNAVAILABLE("gestures_unavailable"),
    OPERATION_FAILED("operation_failed"),
    UNKNOWN_INAPP("unknown_inapp"),
    SHOW_FAILED("show_failed"),
    INTERNAL_ERROR("internal_error"),
}

internal class BridgeRefusalException(
    val code: BridgeErrorCode,
    detail: String,
    cause: Throwable? = null,
) : Exception(detail, cause)

internal val Throwable.bridgeErrorCode: BridgeErrorCode
    get() = (this as? BridgeRefusalException)?.code ?: BridgeErrorCode.INTERNAL_ERROR

internal inline fun <T : Any> requireBridgeNotNull(value: T?, code: BridgeErrorCode, detail: () -> String): T =
    value ?: throw BridgeRefusalException(code, detail())

internal inline fun <T> readBridgePayload(read: () -> T): T = try {
    read()
} catch (error: JSONException) {
    throw BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, error.message.orEmpty(), error)
}

@OptIn(InternalMindboxApi::class)
internal fun Any.logBridgeRefusal(request: BridgeMessage.Request, contentId: String, error: Throwable) {
    val prefix = "[WebView] Bridge: '${request.action}' ${request.id}"
    if (error is WebViewSyncOperationException) {
        mindboxLogW("$prefix for '$contentId' answered with the sync operation error: ${error.payloadJson}")
        return
    }
    mindboxLogE(
        "$prefix refused with ${error.bridgeErrorCode.wireValue} for '$contentId': ${error.message}",
        if (error is BridgeRefusalException) error.cause else error,
    )
}
