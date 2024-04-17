package cloud.mindbox.mobile_sdk.inapp.presentation.actions

internal data class InAppActionResult(
    val redirectUrl: String,
    val payload: String,
    val shouldDismiss: Boolean,
    val shouldCallRequestPermission: Boolean = false
)