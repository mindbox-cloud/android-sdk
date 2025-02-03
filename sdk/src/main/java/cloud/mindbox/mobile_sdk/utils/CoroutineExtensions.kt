package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun <T> Collection<Deferred<T>>.awaitAllWithTimeout(timeMillis: Long): List<T> =
    withTimeoutOrNull(timeMillis) {
        awaitAll()
    } ?: filter { it.isCompleted && !it.isCancelled }
        .map { it.getCompleted() }
