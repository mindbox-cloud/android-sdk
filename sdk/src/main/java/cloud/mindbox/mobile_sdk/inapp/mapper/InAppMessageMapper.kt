package cloud.mindbox.mobile_sdk.inapp.mapper

import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.models.operation.response.SegmentationCheckResponse
import cloud.mindbox.mobile_sdk.models.operation.response.TargetingDto

internal class InAppMessageMapper {

    fun mapInAppConfigResponseToInAppConfig(inAppConfigResponse: InAppConfigResponse): InAppConfig {
        return InAppConfig(
            inAppConfigResponse.inApps?.map { inAppDto ->
                InApp(
                    id = inAppDto.id,
                    targeting = mapTargetingDtoToTargeting(inAppDto.targeting),
                    form = Form(
                        variants = inAppDto.form?.variants?.map { payloadDto ->
                            when (payloadDto) {
                                is PayloadDto.SimpleImage -> {
                                    Payload.SimpleImage(
                                        type = "",
                                        imageUrl = payloadDto.imageUrl ?: "",
                                        redirectUrl = payloadDto.redirectUrl ?: "",
                                        intentPayload = payloadDto.intentPayload ?: ""
                                    )
                                }
                            }
                        } ?: emptyList()
                    ),
                    minVersion = inAppDto.sdkVersion?.minVersion,
                    maxVersion = inAppDto.sdkVersion?.maxVersion
                )
            } ?: emptyList()
        )
    }

    private fun mapTargetingDtoToTargeting(targetingDto: TargetingDto?): Targeting? {
        return if (targetingDto != null) Targeting(
            type = targetingDto.type ?: "",
            segmentation = targetingDto.segmentation ?: "",
            segment = targetingDto.segment ?: ""
        ) else null
    }

    fun mapSegmentationCheckResponseToSegmentationCheck(segmentationCheckResponse: SegmentationCheckResponse): SegmentationCheckInApp {
        return SegmentationCheckInApp(
            status = segmentationCheckResponse.status ?: "",
            customerSegmentations = segmentationCheckResponse.customerSegmentations?.map { customerSegmentationInAppResponse ->
                CustomerSegmentationInApp(
                    segmentation = SegmentationInApp(
                        IdsInApp(customerSegmentationInAppResponse.segmentation?.ids?.externalId
                            ?: "")
                    ),
                    segment = SegmentInApp(
                        IdsInApp(customerSegmentationInAppResponse.segment?.ids?.externalId
                            ?: "")
                    )
                )
            } ?: emptyList()
        )
    }
}