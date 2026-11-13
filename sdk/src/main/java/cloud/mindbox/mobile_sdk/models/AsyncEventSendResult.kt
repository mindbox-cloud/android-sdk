package cloud.mindbox.mobile_sdk.models

internal enum class AsyncEventSendResult(val isRemovedFromQueue: Boolean) {
    DELIVERED(isRemovedFromQueue = true),
    DISCARDED(isRemovedFromQueue = true),
    THROTTLED(isRemovedFromQueue = false),
    RETRY_LATER(isRemovedFromQueue = false),
}
