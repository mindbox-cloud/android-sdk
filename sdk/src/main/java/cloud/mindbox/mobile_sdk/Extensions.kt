package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal fun Map<String, String>.toUrlQueryString() = LoggingExceptionHandler.runCatching(defaultValue = "") {
    this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}
