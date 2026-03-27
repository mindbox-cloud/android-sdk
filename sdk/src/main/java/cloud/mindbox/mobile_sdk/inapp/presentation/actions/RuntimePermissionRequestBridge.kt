package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

internal object RuntimePermissionRequestBridge {

    private val pendingRequestsById: MutableMap<String, CompletableDeferred<PermissionRequest>> = ConcurrentHashMap()

    fun register(requestId: String): CompletableDeferred<PermissionRequest> {
        val deferred: CompletableDeferred<PermissionRequest> = CompletableDeferred()
        pendingRequestsById[requestId] = deferred
        return deferred
    }

    fun resolve(requestId: String, isGranted: Boolean, isDialogShown: Boolean) {
        val deferred: CompletableDeferred<PermissionRequest> = pendingRequestsById.remove(requestId) ?: return
        if (!deferred.isCompleted) {
            deferred.complete(PermissionRequest(isGranted, isDialogShown))
        }
    }

    data class PermissionRequest(
        val isGranted: Boolean,
        val dialogShown: Boolean,
    )
}
