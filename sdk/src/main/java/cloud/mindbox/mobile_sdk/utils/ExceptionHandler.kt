package cloud.mindbox.mobile_sdk.utils

/**
 * A class for internal sdk work only. Do not extend or use it
 * */
public abstract class ExceptionHandler {

    public fun <T> runCatching(block: () -> T) {
        runCatching(Unit, block)
    }

    public suspend fun <T> runCatchingSuspending(block: suspend () -> T) {
        runCatchingSuspending(Unit, block)
    }

    public fun <T> runCatching(
        defaultValue: T,
        block: () -> T,
    ): T = runCatching(block = block) { defaultValue }

    public suspend fun <T> runCatchingSuspending(
        defaultValue: T,
        block: suspend () -> T,
    ): T = runCatchingSuspending(block = block) { defaultValue }

    public fun <T> runCatching(
        block: () -> T,
        defaultValue: (Throwable) -> T,
    ): T = kotlin.runCatching { block.invoke() }.getOrElse { exception ->
        handle(exception)
        defaultValue.invoke(exception)
    }

    public suspend fun <T> runCatchingSuspending(
        block: suspend () -> T,
        defaultValue: (Throwable) -> T,
    ): T = kotlin.runCatching { block.invoke() }.getOrElse { exception ->
        handle(exception)
        defaultValue.invoke(exception)
    }

    protected abstract fun handle(exception: Throwable)
}
