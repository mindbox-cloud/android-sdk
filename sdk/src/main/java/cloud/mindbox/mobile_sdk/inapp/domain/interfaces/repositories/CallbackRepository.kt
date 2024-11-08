package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

internal interface CallbackRepository {

    fun validateUserString(userString: String): Boolean

    fun isValidUrl(url: String): Boolean
}
