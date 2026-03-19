package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

internal object RuntimePermissionRequestBridge {

    private val pendingRequestsById: MutableMap<String, CompletableDeferred<Boolean>> = ConcurrentHashMap()

    fun register(requestId: String): CompletableDeferred<Boolean> {
        val deferred: CompletableDeferred<Boolean> = CompletableDeferred()
        pendingRequestsById[requestId] = deferred
        return deferred
    }

    fun resolve(requestId: String, isGranted: Boolean) {
        val deferred: CompletableDeferred<Boolean> = pendingRequestsById.remove(requestId) ?: return
        if (!deferred.isCompleted) {
            deferred.complete(isGranted)
        }
    }
}
