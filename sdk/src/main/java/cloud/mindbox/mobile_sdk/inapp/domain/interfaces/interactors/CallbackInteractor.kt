package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

internal interface CallbackInteractor {

    fun shouldCopyString(userString: String): Boolean

    fun isValidUrl(url: String): Boolean
}
