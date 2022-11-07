package cloud.mindbox.mobile_sdk.inapp.domain

sealed class InAppType(open val inAppId: String) {


    object NoInAppType : InAppType("")

    class SimpleImage(
        override val inAppId: String,
        val imageUrl: String,
        val redirectUrl: String,
        val intentData: String,
    ) :
        InAppType(inAppId)
}