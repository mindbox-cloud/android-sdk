package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

internal fun <T> CoroutineScope.suspendLazy(
    initializer: suspend CoroutineScope.() -> T
): SuspendLazy<T> = SuspendLazySuspendingImpl(this, initializer)

interface SuspendLazy<out T> {
    suspend operator fun invoke(): T
}

private class SuspendLazySuspendingImpl<out T>(
    coroutineScope: CoroutineScope,
    initializer: suspend CoroutineScope.() -> T
) : SuspendLazy<T> {
    private val deferred = coroutineScope.async(start =  CoroutineStart.LAZY, block = initializer)
    override suspend operator fun invoke(): T = deferred.await()
}