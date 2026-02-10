package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingErrorKey

internal interface InAppTargetingErrorRepository {
    fun saveError(key: TargetingErrorKey, error: Throwable)

    fun getError(key: TargetingErrorKey): String?

    fun clearErrors()
}
