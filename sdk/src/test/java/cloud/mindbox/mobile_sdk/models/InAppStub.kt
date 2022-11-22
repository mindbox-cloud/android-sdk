package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl

internal class InAppStub {

    companion object {
        fun get() = InApp(id = "",
            minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1,
            maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1,
            targeting = Targeting(type = "", segmentation = "12345", segment = "12345"),
            form = Form(variants = listOf(Payload.SimpleImage(type = "",
                imageUrl = "",
                redirectUrl = "",
                intentPayload = ""))))
    }
}