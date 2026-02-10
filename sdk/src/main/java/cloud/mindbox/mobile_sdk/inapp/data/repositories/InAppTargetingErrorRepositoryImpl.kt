package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.getVolleyErrorDetails
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppTargetingErrorRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingErrorKey

internal class InAppTargetingErrorRepositoryImpl(
    private val sessionStorageManager: SessionStorageManager,
) : InAppTargetingErrorRepository {
    override fun saveError(key: TargetingErrorKey, error: Throwable) {
        sessionStorageManager.lastTargetingErrors[key] = "${error.message}. ${error.cause?.getVolleyErrorDetails() ?: "volleyError = null"}"
    }

    override fun getError(key: TargetingErrorKey): String? {
        return sessionStorageManager.lastTargetingErrors[key]
    }

    override fun clearErrors() {
        sessionStorageManager.lastTargetingErrors.clear()
    }
}
