package cloud.mindbox.mobile_sdk.inapp.mapper

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.*

internal class InAppMessageMapper {

    fun mapToInAppDto(
        inAppDtoBlank: InAppConfigResponseBlank.InAppDtoBlank,
        formDto: FormDto?,
        targetingDto: TreeTargetingDto?,
    ): InAppDto {
        return inAppDtoBlank.let { inApp ->
            InAppDto(
                id = inApp.id,
                sdkVersion = inApp.sdkVersion,
                targeting = targetingDto,
                form = formDto
            )
        }
    }

    fun mapToInAppConfig(inAppConfigResponse: InAppConfigResponse?): InAppConfig? {
        return inAppConfigResponse?.let { inAppConfigDto ->
            InAppConfig(
                inAppConfigDto.inApps?.map { inAppDto ->
                    InApp(
                        id = inAppDto.id,
                        targeting = mapTargetingDtoToTargeting(inAppDto.targeting),
                        form = Form(
                            variants = inAppDto.form?.variants?.map { payloadDto ->
                                when (payloadDto) {
                                    is PayloadDto.SimpleImage -> {
                                        Payload.SimpleImage(
                                            type = payloadDto.type ?: "",
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
    }


    private fun mapTargetingDtoToTargeting(targetingDto: TreeTargetingDto?): TreeTargeting? {
        return if (targetingDto != null)
            when (targetingDto) {
                is TreeTargetingDto.TrueNodeDto -> TreeTargeting.TrueNode(InAppRepositoryImpl.TRUE_JSON_NAME)
                is TreeTargetingDto.IntersectionNodeDto -> TreeTargeting.IntersectionNode(
                    InAppRepositoryImpl.AND_JSON_NAME,
                    mapNodesDtoToNodes(targetingDto.nodes))
                is TreeTargetingDto.SegmentNodeDto -> TreeTargeting.SegmentNode(InAppRepositoryImpl.SEGMENT_JSON_NAME,
                    Kind.POSITIVE,
                    targetingDto.segmentationExternalId,
                    targetingDto.segmentationInternalId,
                    targetingDto.segment_external_id)
                is TreeTargetingDto.UnionNodeDto -> TreeTargeting.UnionNode(InAppRepositoryImpl.OR_JSON_NAME,
                    mapNodesDtoToNodes(targetingDto.nodes))
            }
        else null
    }

    private fun mapNodesDtoToNodes(nodesDto: List<TreeTargetingDto>): List<TreeTargeting> {
        return nodesDto.map { treeTargetingDto ->
            when (treeTargetingDto) {
                is TreeTargetingDto.TrueNodeDto -> TreeTargeting.TrueNode(InAppRepositoryImpl.TRUE_JSON_NAME)
                is TreeTargetingDto.IntersectionNodeDto -> TreeTargeting.IntersectionNode(
                    InAppRepositoryImpl.AND_JSON_NAME,
                    mapNodesDtoToNodes(treeTargetingDto.nodes))
                is TreeTargetingDto.SegmentNodeDto -> TreeTargeting.SegmentNode(InAppRepositoryImpl.SEGMENT_JSON_NAME,
                    if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    treeTargetingDto.segmentationExternalId,
                    treeTargetingDto.segmentationInternalId,
                    treeTargetingDto.segment_external_id)
                is TreeTargetingDto.UnionNodeDto -> TreeTargeting.UnionNode(InAppRepositoryImpl.OR_JSON_NAME,
                    mapNodesDtoToNodes(treeTargetingDto.nodes))
            }
        }
    }

    fun mapToSegmentationCheck(segmentationCheckResponse: SegmentationCheckResponse): SegmentationCheckInApp {
        return SegmentationCheckInApp(
            status = segmentationCheckResponse.status ?: "",
            customerSegmentations = segmentationCheckResponse.customerSegmentations?.map { customerSegmentationInAppResponse ->
                CustomerSegmentationInApp(
                    segmentation = SegmentationInApp(
                        IdsInApp(customerSegmentationInAppResponse.segmentation?.ids?.externalId
                        )
                    ),
                    segment = SegmentInApp(
                        IdsInApp(customerSegmentationInAppResponse.segment?.ids?.externalId)
                    )
                )
            } ?: emptyList()
        )
    }

    fun mapToSegmentationCheckRequest(config: InAppConfig): SegmentationCheckRequest {
        return SegmentationCheckRequest(
            config.inApps.map { inAppDto ->
                //TODO починить
                SegmentationDataRequest(IdsRequest(""))
            })
    }
}