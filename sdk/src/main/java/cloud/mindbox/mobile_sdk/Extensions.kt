package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl

fun <T> Result<T>.returnOnException(block: (exception: Throwable) -> T): T {
    return this.getOrElse { exception ->
        exception.handle()
        return block.invoke(exception)
    }
}

fun Result<Unit>.logOnException() {
    this.exceptionOrNull()?.handle()
}

private fun Throwable.handle() {
    try {
        MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", this)
        // todo log crash
    } catch (e: Throwable) {
    }
}

internal fun Map<String, String>.toUrlQueryString() = runCatching {
    return this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}.returnOnException { "" }
