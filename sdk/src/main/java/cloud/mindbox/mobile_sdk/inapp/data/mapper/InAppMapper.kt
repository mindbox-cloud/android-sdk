package cloud.mindbox.mobile_sdk.inapp.data.mapper

import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.enumValue
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductResponse
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest

internal class InAppMapper {
    fun mapToProductSegmentationResponse(productSegmentationResponseDto: ProductSegmentationResponseDto): ProductSegmentationResponseWrapper {
        return ProductSegmentationResponseWrapper(
            productSegmentationResponseDto.products?.map { productResponseDto ->
                ProductResponse(
                    productList = productResponseDto?.segmentations?.map { productSegmentations ->
                        ProductSegmentationResponse(
                            segmentationExternalId = productSegmentations?.ids?.ids?.values?.first()
                                ?: "",
                            segmentExternalId = productSegmentations?.segment?.ids?.ids?.values?.first()
                                ?: ""
                        )
                    } ?: emptyList()

                )
            } ?: emptyList()
        )
    }

    fun mapGeoTargetingDtoToGeoTargeting(geoTargetingDto: GeoTargetingDto): GeoTargeting {
        return GeoTargeting(
            geoTargetingDto.cityId ?: "",
            geoTargetingDto.regionId ?: "",
            geoTargetingDto.countryId ?: ""
        )
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

    fun mapToLogRequestDto(
        logRequestDtoBlank: LogRequestDtoBlank,
    ): LogRequestDto {
        return LogRequestDto(
            requestId = logRequestDtoBlank.requestId!!,
            deviceId = logRequestDtoBlank.deviceId!!,
            from = logRequestDtoBlank.from!!,
            to = logRequestDtoBlank.to!!
        )
    }

    fun mapToInAppConfig(
        inAppConfigResponse: InAppConfigResponse?,
    ): InAppConfig? {
        return inAppConfigResponse?.let {
            InAppConfig(
                inApps = inAppConfigResponse.inApps?.map { inAppDto ->
                    InApp(
                        id = inAppDto.id,
                        targeting = mapNodesDtoToNodes(listOf(inAppDto.targeting!!)).first(),
                        form = Form(
                            variants = inAppDto.form?.variants?.map { payloadDto ->
                                when (payloadDto) {
                                    is PayloadDto.SimpleImage -> {
                                        Payload.SimpleImage(
                                            type = PayloadDto.SimpleImage.SIMPLE_IMAGE_JSON_NAME,
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
                } ?: emptyList(),
                monitoring = inAppConfigResponse.monitoring?.map {
                    LogRequest(
                        requestId = it.requestId,
                        deviceId = it.deviceId,
                        from = it.from.convertToZonedDateTime(),
                        to = it.to.convertToZonedDateTime()
                    )
                } ?: emptyList(),
                operations = inAppConfigResponse.settings?.map { (key, value) ->
                    key.enumValue<OperationName>() to OperationSystemName(value.systemName)
                }?.toMap() ?: emptyMap(),
                abtests = inAppConfigResponse.abtests?.map { dto ->
                    ABTest(
                        id = dto.id,
                        minVersion = dto.sdkVersion?.minVersion,
                        maxVersion = dto.sdkVersion?.maxVersion,
                        salt = dto.salt!!,
                        variants = dto.variants?.map { variantDto ->
                            ABTest.Variant(
                                type = variantDto.objects!!.first().type!!,
                                kind = variantDto.objects.first().kind.enumValue(),
                                inapps = variantDto.objects.first().inapps ?: listOf(),
                                lower = variantDto.modulus!!.lower!!,
                                upper = variantDto.modulus.upper!!,
                            )
                        } ?: listOf()
                    )
                } ?: listOf()
            )
        }
    }

    /**
     * Cast is ok as long as validator removes all the in-apps with null values
     * **/

    @Suppress("UNCHECKED_CAST")
    private fun mapNodesDtoToNodes(
        nodesDto: List<TreeTargetingDto>,
    ): List<TreeTargeting> {
        return nodesDto.map { treeTargetingDto ->
            when (treeTargetingDto) {
                is TreeTargetingDto.OperationNodeDto -> {
                    OperationNode(
                        TreeTargetingDto.OperationNodeDto.API_METHOD_CALL_JSON_NAME,
                        treeTargetingDto.systemName!!.lowercase()
                    )
                }
                is TreeTargetingDto.TrueNodeDto -> TreeTargeting.TrueNode(TreeTargetingDto.TrueNodeDto.TRUE_JSON_NAME)
                is TreeTargetingDto.IntersectionNodeDto -> TreeTargeting.IntersectionNode(
                    type = TreeTargetingDto.IntersectionNodeDto.AND_JSON_NAME,
                    nodes = mapNodesDtoToNodes(treeTargetingDto.nodes as List<TreeTargetingDto>)
                )
                is TreeTargetingDto.SegmentNodeDto -> TreeTargeting.SegmentNode(
                    type = TreeTargetingDto.SegmentNodeDto.SEGMENT_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    segmentationExternalId = treeTargetingDto.segmentationExternalId!!,
                    segmentExternalId = treeTargetingDto.segmentExternalId!!
                )
                is TreeTargetingDto.UnionNodeDto -> TreeTargeting.UnionNode(
                    type = TreeTargetingDto.UnionNodeDto.OR_JSON_NAME,
                    nodes = mapNodesDtoToNodes(treeTargetingDto.nodes as List<TreeTargetingDto>)
                )
                is TreeTargetingDto.CityNodeDto -> TreeTargeting.CityNode(
                    type = TreeTargetingDto.CityNodeDto.CITY_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    ids = treeTargetingDto.ids as List<String>
                )
                is TreeTargetingDto.CountryNodeDto -> TreeTargeting.CountryNode(
                    type = TreeTargetingDto.CountryNodeDto.COUNTRY_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    ids = treeTargetingDto.ids as List<String>
                )
                is TreeTargetingDto.RegionNodeDto -> TreeTargeting.RegionNode(
                    type = TreeTargetingDto.RegionNodeDto.REGION_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    ids = treeTargetingDto.ids as List<String>
                )
                is TreeTargetingDto.ViewProductCategoryNodeDto -> ViewProductCategoryNode(
                    type = TreeTargetingDto.ViewProductCategoryNodeDto.VIEW_PRODUCT_CATEGORY_ID_JSON_NAME,
                    kind = treeTargetingDto.kind.enumValue(),
                    value = treeTargetingDto.value!!
                )
                is TreeTargetingDto.ViewProductCategoryInNodeDto -> ViewProductCategoryInNode(
                    type = TreeTargetingDto.ViewProductCategoryNodeDto.VIEW_PRODUCT_CATEGORY_ID_JSON_NAME,
                    kind = treeTargetingDto.kind.enumValue(),
                    values = treeTargetingDto.values?.map { dto ->
                        ViewProductCategoryInNode.Value(
                            id = dto.id!!,
                            externalId = dto.externalId!!,
                            externalSystemName = dto.externalSystemName!!
                        )
                    } ?: listOf()
                )
                is TreeTargetingDto.ViewProductNodeDto -> ViewProductNode(
                    type = TreeTargetingDto.ViewProductNodeDto.VIEW_PRODUCT_ID_JSON_NAME,
                    kind = treeTargetingDto.kind.enumValue(),
                    value = treeTargetingDto.value!!
                )
                is TreeTargetingDto.ViewProductSegmentNodeDto -> ViewProductSegmentNode(
                    type = TreeTargetingDto.ViewProductSegmentNodeDto.VIEW_PRODUCT_SEGMENT_JSON_NAME,
                    kind = treeTargetingDto.kind.enumValue(),
                    segmentationExternalId = treeTargetingDto.segmentationExternalId!!,
                    segmentExternalId = treeTargetingDto.segmentExternalId!!
                )
            }
        }
    }

    fun mapToSegmentationCheck(segmentationCheckResponse: SegmentationCheckResponse): SegmentationCheckWrapper {
        return SegmentationCheckWrapper(
            status = segmentationCheckResponse.status ?: "",
            customerSegmentations = segmentationCheckResponse.customerSegmentations?.filter { customerSegmentationInAppResponse ->
                customerSegmentationInAppResponse.segmentation?.ids?.externalId != null
            }?.map { customerSegmentationInAppResponse ->
                CustomerSegmentationInApp(
                    customerSegmentationInAppResponse.segmentation?.ids?.externalId!!,
                    customerSegmentationInAppResponse.segment?.ids?.externalId ?: ""
                )
            } ?: emptyList()
        )
    }

    fun mapToCustomerSegmentationCheckRequest(inApps: List<InApp>): SegmentationCheckRequest {
        return SegmentationCheckRequest(
            inApps.flatMap { inAppDto ->
                getTargetingCustomerSegmentationsList(inAppDto.targeting).map { segment ->
                    SegmentationDataRequest(IdsRequest(segment))
                }
            }.distinctBy {
                it.ids?.externalId
            })
    }

    fun mapToProductSegmentationCheckRequest(
        product: Pair<String, String>,
        inApps: List<InApp>,
    ): ProductSegmentationRequestDto {
        return ProductSegmentationRequestDto(
            products = listOf(
                ProductRequestDto(
                    Ids(product)
                )
            ),
            segmentations = inApps.flatMap { inApp ->
                getTargetingProductSegmentationsList(inApp.targeting).map { segmentation ->
                    SegmentationRequestDto(
                        SegmentationRequestIds(segmentation)
                    )
                }
            }.distinctBy {
                it.ids.externalId
            }
        )
    }

    private fun getTargetingProductSegmentationsList(targeting: TreeTargeting): List<String> {
        return when (targeting) {
            is TreeTargeting.IntersectionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingProductSegmentationsList(treeTargeting)
                }
            }
            is ViewProductSegmentNode -> {
                listOf(targeting.segmentationExternalId)
            }

            is TreeTargeting.UnionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingProductSegmentationsList(treeTargeting)
                }
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun getTargetingCustomerSegmentationsList(targeting: TreeTargeting): List<String> {
        return when (targeting) {
            is TreeTargeting.IntersectionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingCustomerSegmentationsList(treeTargeting)
                }
            }
            is TreeTargeting.SegmentNode -> {
                listOf(targeting.segmentationExternalId)
            }

            is TreeTargeting.UnionNode -> {
                targeting.nodes.flatMap { treeTargeting ->
                    getTargetingCustomerSegmentationsList(treeTargeting)
                }
            }
            else -> {
                emptyList()
            }
        }
    }
}