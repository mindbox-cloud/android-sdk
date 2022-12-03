package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.*

internal class InAppStub {

    companion object {
        fun getInApp(): InApp = InApp(id = "",
            minVersion = null,
            maxVersion = null,
            targeting = null,
            form = Form(variants = listOf(Payload.SimpleImage(type = "",
                imageUrl = "",
                redirectUrl = "",
                intentPayload = ""))))

        fun getInAppDto(): InAppDto = InAppDto(id = "",
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = null,
            form = FormDto(variants = listOf(PayloadDto.SimpleImage(type = null,
                imageUrl = null,
                redirectUrl = null,
                intentPayload = null))))

        fun getSimpleImageDto() = PayloadDto.SimpleImage(type = null,
            imageUrl = null,
            redirectUrl = null,
            intentPayload = null)
    }
}