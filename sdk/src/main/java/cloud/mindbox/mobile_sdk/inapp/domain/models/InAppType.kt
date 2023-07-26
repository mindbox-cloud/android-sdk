package cloud.mindbox.mobile_sdk.inapp.domain.models

sealed class InAppType(open val inAppId: String) {

    data class SimpleImage(
        override val inAppId: String,
        val imageUrl: String,
        val redirectUrl: String,
        val intentData: String,
    ) : InAppType(inAppId)

    data class ModalWindow(
        override val inAppId: String,
        val imageUrl: String,
        val redirectUrl: String,
        val intentData: String
    ): InAppType(inAppId)
}