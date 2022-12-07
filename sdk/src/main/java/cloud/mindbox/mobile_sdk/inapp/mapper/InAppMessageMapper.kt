package cloud.mindbox.mobile_sdk.inapp.mapper

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.*

internal class InAppMessageMapper {

    fun mapGeoTargetingDtoToGeoTargeting(geoTargetingDto: GeoTargetingDto): GeoTargeting
    {
        return GeoTargeting(geoTargetingDto.cityId?: "", geoTargetingDto.regionId?: "", geoTargetingDto.countryId?: "")
    }

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

    fun mapToInAppConfig(
        inAppConfigResponse: InAppConfigResponse?,
        geoTargetingDto: GeoTargetingDto?,
    ): InAppConfig? {
        return inAppConfigResponse?.let { inAppConfigDto ->
            InAppConfig(
                inAppConfigDto.inApps?.map { inAppDto ->
                    InApp(
                        id = inAppDto.id,
                        targeting = mapNodesDtoToNodes(listOf(inAppDto.targeting!!), geoTargetingDto).first(),
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
                                    null -> {
                                        return null // should never trigger because of validator
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

    /**
     * Cast is ok as long as validator removes all the in-apps with null values
     * **/

    @Suppress("UNCHECKED_CAST")
    private fun mapNodesDtoToNodes(
        nodesDto: List<TreeTargetingDto>,
        geoTargetingDto: GeoTargetingDto?,
    ): List<TreeTargeting> {
        return nodesDto.map { treeTargetingDto ->
            when (treeTargetingDto) {
                is TreeTargetingDto.TrueNodeDto -> TreeTargeting.TrueNode(InAppRepositoryImpl.TRUE_JSON_NAME)
                is TreeTargetingDto.IntersectionNodeDto -> TreeTargeting.IntersectionNode(
                    InAppRepositoryImpl.AND_JSON_NAME,
                    mapNodesDtoToNodes(treeTargetingDto.nodes as List<TreeTargetingDto>,
                        geoTargetingDto))
                is TreeTargetingDto.SegmentNodeDto -> TreeTargeting.SegmentNode(InAppRepositoryImpl.SEGMENT_JSON_NAME,
                    if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    treeTargetingDto.segmentationExternalId!!,
                    treeTargetingDto.segment_external_id!!)
                is TreeTargetingDto.UnionNodeDto -> TreeTargeting.UnionNode(InAppRepositoryImpl.OR_JSON_NAME,
                    mapNodesDtoToNodes(treeTargetingDto.nodes as List<TreeTargetingDto>,
                        geoTargetingDto))
                is TreeTargetingDto.CityNodeDto -> TreeTargeting.CityNode(InAppRepositoryImpl.TYPE_JSON_NAME,
                    if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    treeTargetingDto.ids as List<String>, geoTargetingDto?.cityId ?: "")
                is TreeTargetingDto.CountryNodeDto -> TreeTargeting.CountryNode(InAppRepositoryImpl.TYPE_JSON_NAME,
                    if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    treeTargetingDto.ids as List<String>, geoTargetingDto?.countryId ?: "")
                is TreeTargetingDto.RegionNodeDto -> TreeTargeting.RegionNode(InAppRepositoryImpl.TYPE_JSON_NAME,
                    if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    treeTargetingDto.ids as List<String>, geoTargetingDto?.regionId ?: "")
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
            config.inApps.flatMap { inAppDto ->
                getTargetingSegmentList(inAppDto.targeting).map { segment ->
                    SegmentationDataRequest(IdsRequest(segment))
                }
            })
    }

    private fun getTargetingSegmentList(targeting: TreeTargeting): List<String> {
        return when (targeting) {
            is TreeTargeting.IntersectionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingSegmentList(treeTargeting)
                }
            }
            is TreeTargeting.SegmentNode -> {
                listOf(targeting.segmentationExternalId)
            }

            is TreeTargeting.UnionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingSegmentList(treeTargeting)
                }
            }
            else -> {
                emptyList()
            }
        }
    }
}