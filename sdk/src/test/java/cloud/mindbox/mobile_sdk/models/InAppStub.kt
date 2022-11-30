package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.Payload
import cloud.mindbox.mobile_sdk.inapp.domain.models.Targeting
import cloud.mindbox.mobile_sdk.models.operation.response.*

internal class InAppStub {

    companion object {
        fun getInApp(): InApp = InApp(id = "",
            minVersion = null,
            maxVersion = null,
            targeting = Targeting(type = "", segmentation = "", segment = ""),
            form = Form(variants = listOf(Payload.SimpleImage(type = "",
                imageUrl = "",
                redirectUrl = "",
                intentPayload = ""))))

        fun getInAppDto(): InAppDto = InAppDto(id = "",
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = TargetingDto(type = null, segmentation = null, segment = null),
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