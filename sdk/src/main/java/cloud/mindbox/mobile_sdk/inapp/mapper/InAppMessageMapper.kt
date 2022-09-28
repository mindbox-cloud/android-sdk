package cloud.mindbox.mobile_sdk.inapp.mapper

import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.models.operation.response.SegmentationCheckResponse

internal class InAppMessageMapper {

    fun mapInAppConfigResponseToInAppConfig(inAppConfigResponse: InAppConfigResponse): InAppConfig {
        return InAppConfig(
            inAppConfigResponse.inApps?.map { inAppDto ->
                InApp(
                    id = inAppDto.id,
                    targeting = Targeting(
                        type = inAppDto.targeting?.type ?: "",
                        segmentation = inAppDto.targeting?.segmentation ?: "",
                        segment = inAppDto.targeting?.segment ?: ""
                    ),
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
                    )
                )
            } ?: emptyList()
        )
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