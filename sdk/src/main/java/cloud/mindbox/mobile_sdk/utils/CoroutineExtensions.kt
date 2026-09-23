package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun <T> Collection<Deferred<T>>.awaitAllWithTimeout(timeMillis: Long): List<T> =
    withTimeoutOrNull(timeMillis.milliseconds) {
        awaitAll()
    } ?: filter { it.isCompleted && !it.isCancelled }
        .map { it.getCompleted() }
        .also {
            forEach { deferred ->
                if (!deferred.isCompleted) {
                    deferred.cancel()
                }
            }
        }

internal inline fun CoroutineScope.launchWithLock(
    mutex: Mutex,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job {
    return launch {
        mutex.withLock {
            block()
        }
    }
}
