package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType

class InAppTypeStub {
    companion object {
        fun get() = InAppType.SimpleImage(inAppId = "",
            imageUrl = "",
            redirectUrl = "",
            intentData = "")
    }
}