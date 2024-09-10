package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal class AwaitSync {
    private val deferred = CompletableDeferred<Unit>()

    fun complete() {
        deferred.complete(Unit)
    }

    suspend fun waitForAwait(timeoutMillis: Long = 1_000) {
        withTimeout(timeoutMillis) {
            deferred.await()
        }
    }
}