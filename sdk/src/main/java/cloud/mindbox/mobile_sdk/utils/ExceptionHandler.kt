package cloud.mindbox.mobile_sdk.utils

/**
 * A class for internal sdk work only. Do not extend or use it
 * */
abstract class ExceptionHandler {

    fun <T> runCatching(block: () -> T) {
        runCatching(Unit, block)
    }

    suspend fun <T> runCatchingSuspending(block: suspend () -> T) {
        runCatchingSuspending(Unit, block)
    }

    fun <T> runCatching(
        defaultValue: T,
        block: () -> T,
    ): T = runCatching(block = block) { defaultValue }

    suspend fun <T> runCatchingSuspending(
        defaultValue: T,
        block: suspend () -> T,
    ): T = runCatchingSuspending(block = block) { defaultValue }

    fun <T> runCatching(
        block: () -> T,
        defaultValue: (Throwable) -> T,
    ): T = kotlin.runCatching { block.invoke() }.getOrElse { exception ->
        handle(exception)
        defaultValue.invoke(exception)
    }

    suspend fun <T> runCatchingSuspending(
        block: suspend () -> T,
        defaultValue: (Throwable) -> T,
    ): T = kotlin.runCatching { block.invoke() }.getOrElse { exception ->
        handle(exception)
        defaultValue.invoke(exception)
    }

    protected abstract fun handle(exception: Throwable)
}
