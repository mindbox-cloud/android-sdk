package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl

internal object LoggingExceptionHandler : ExceptionHandler() {

    override fun handle(exception: Throwable) {
        try {
            MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", exception)
            // todo log crash
        } catch (e: Throwable) {
            println(e.message)
        }
    }
}

internal fun <T> loggingRunCatching(
    defaultValue: T,
    block: () -> T,
): T = LoggingExceptionHandler.runCatching(defaultValue, block)

internal fun <T> loggingRunCatching(block: () -> T) = LoggingExceptionHandler.runCatching(block)

internal suspend fun <T> loggingRunCatchingSuspending(block: suspend () -> T) = LoggingExceptionHandler.runCatchingSuspending(block)

internal suspend fun <T> loggingRunCatchingSuspending(defaultValue: T, block: suspend () -> T) = LoggingExceptionHandler.runCatchingSuspending(defaultValue, block)
