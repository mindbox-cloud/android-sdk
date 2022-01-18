package cloud.mindbox.mobile_sdk_core

import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import java.util.*

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
        MindboxLoggerInternal.e(MindboxInternalCore, "Mindbox caught unhandled error", this)
        // todo log crash
    } catch (e: Throwable) {
    }
}

internal fun Map<String, String>.toUrlQueryString() = runCatching {
    return this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}.returnOnException { "" }
