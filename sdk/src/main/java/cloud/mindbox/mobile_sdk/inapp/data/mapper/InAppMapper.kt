package cloud.mindbox.mobile_sdk.inapp.data.mapper

import cloud.mindbox.mobile_sdk.SessionDelay
import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.enumValue
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductResponse
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyOnceDto.Companion.FREQUENCY_KIND_LIFETIME
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyOnceDto.Companion.FREQUENCY_KIND_SESSION
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import kotlin.math.roundToInt

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
        frequencyDto: FrequencyDto,
        targetingDto: TreeTargetingDto?,
    ): InAppDto {
        return inAppDtoBlank.let { inApp ->
            InAppDto(
                id = inApp.id,
                sdkVersion = inApp.sdkVersion,
                targeting = targetingDto,
                frequency = frequencyDto,
                form = formDto
            )
        }
    }

    fun mapToLogRequestDto(
        logRequestDtoBlank: LogRequestDtoBlank,
    ): LogRequestDto {
        return LogRequestDto(
            requestId = logRequestDtoBlank.requestId,
            deviceId = logRequestDtoBlank.deviceId,
            from = logRequestDtoBlank.from,
            to = logRequestDtoBlank.to
        )
    }

    private fun mapModalWindowLayers(layers: List<BackgroundDto.LayerDto?>?): List<Layer> {
        return layers?.map { layerDto ->
            when (layerDto) {
                is BackgroundDto.LayerDto.ImageLayerDto -> {
                    Layer.ImageLayer(
                        action = when (layerDto.action) {
                            is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto -> {
                                Layer.ImageLayer.Action.RedirectUrlAction(
                                    url = layerDto.action.value!!,
                                    payload = layerDto.action.intentPayload!!
                                )
                            }

                            is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto -> {
                                Layer.ImageLayer.Action.PushPermissionAction(
                                    payload = layerDto.action.intentPayload!!
                                )
                            }

                            else -> {
                                error("Unknown action cannot be mapped. Should never happen because of validators")
                            }
                        },
                        source = when (layerDto.source) {
                            is BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto -> {
                                Layer.ImageLayer.Source.UrlSource(
                                    url = layerDto.source.value!!
                                )
                            }

                            else -> {
                                error("Unknown source cannot be mapped. Should never happen because of validators")
                            }
                        }
                    )
                }

                else -> {
                    error("Unknown layer cannot be mapped. Should never happen because of validators")
                }
            }
        }!!
    }

    private fun mapElements(elements: List<ElementDto?>?): List<Element> {
        return elements?.map { elementDto ->
            when (elementDto) {
                is ElementDto.CloseButtonElementDto -> {
                    Element.CloseButton(
                        color = elementDto.color!!,
                        lineWidth = elementDto.lineWidth.toString()
                            .toDouble(),
                        size = Element.CloseButton.Size(
                            width = elementDto.size?.width!!,
                            height = elementDto.size.height!!,
                            kind = when (elementDto.size.kind) {
                                "dp" -> {
                                    Element.CloseButton.Size.Kind.DP
                                }

                                else -> {
                                    error("Unknown size cannot be mapped. Should never happen because of validators")
                                }
                            }
                        ),
                        position = Element.CloseButton.Position(
                            top = elementDto.position?.margin?.top!!,
                            right = elementDto.position.margin.right!!,
                            left = elementDto.position.margin.left!!,
                            bottom = elementDto.position.margin.bottom!!,
                            kind = when (elementDto.position.margin.kind) {
                                "proportion" -> {
                                    Element.CloseButton.Position.Kind.PROPORTION
                                }

                                else -> {
                                    error("Unknown margin cannot be mapped. Should never happen because of validators")
                                }
                            }
                        )
                    )
                }

                else -> {
                    error("Unknown element cannot be mapped. Should never happen because of validators")
                }
            }
        } ?: emptyList()
    }

    private fun getDelay(item: FrequencyDto): Frequency.Delay {
        return when (item) {
            is FrequencyDto.FrequencyOnceDto -> {
                when {
                    item.kind.equals(
                        other = FREQUENCY_KIND_LIFETIME,
                        ignoreCase = true
                    ) -> Frequency.Delay.LifetimeDelay

                    item.kind.equals(
                        other = FREQUENCY_KIND_SESSION,
                        ignoreCase = true
                    ) -> SessionDelay()

                    else -> error("Unknown kind cannot be mapped. Should never happen because of validators")
                }
            }

            is FrequencyDto.FrequencyPeriodicDto -> {
                when {
                    item.unit.equals(
                        other = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_SECONDS,
                        ignoreCase = true
                    ) -> {
                        Frequency.Delay.TimeDelay(item.value, InAppTime.SECONDS)
                    }

                    item.unit.equals(
                        other = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_HOURS,
                        ignoreCase = true
                    ) -> {
                        Frequency.Delay.TimeDelay(item.value, InAppTime.HOURS)
                    }

                    item.unit.equals(
                        other = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_DAYS,
                        ignoreCase = true
                    ) -> {
                        Frequency.Delay.TimeDelay(item.value, InAppTime.DAYS)
                    }

                    item.unit.equals(
                        other = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_MINUTES,
                        ignoreCase = true
                    ) -> {
                        Frequency.Delay.TimeDelay(item.value, InAppTime.MINUTES)
                    }

                    else -> error("Unknown time unit cannot be mapped. Should never happen because of validators")
                }
            }
        }
    }

    fun mapToInAppConfig(
        inAppConfigResponse: InAppConfigResponse?,
    ): InAppConfig {
        return inAppConfigResponse?.let {
            InAppConfig(
                inApps = inAppConfigResponse.inApps?.map { inAppDto ->
                    InApp(
                        id = inAppDto.id,
                        targeting = mapNodesDtoToNodes(listOf(inAppDto.targeting!!)).first(),
                        form = Form(
                            variants = inAppDto.form?.variants?.map { payloadDto ->
                                when (payloadDto) {
                                    is PayloadDto.ModalWindowDto -> {
                                        InAppType.ModalWindow(
                                            type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME,
                                            layers = mapModalWindowLayers(payloadDto.content?.background?.layers),
                                            inAppId = inAppDto.id,
                                            elements = mapElements(payloadDto.content?.elements)
                                        )
                                    }

                                    is PayloadDto.SnackbarDto -> {
                                        InAppType.Snackbar(
                                            inAppId = inAppDto.id,
                                            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
                                            layers = mapModalWindowLayers(payloadDto.content?.background?.layers),
                                            elements = mapElements(payloadDto.content?.elements),
                                            position = InAppType.Snackbar.Position(
                                                gravity = InAppType.Snackbar.Position.Gravity(
                                                    horizontal = InAppType.Snackbar.Position.Gravity.HorizontalGravity.CENTER,
                                                    vertical = if (payloadDto.content?.position?.gravity?.vertical!! == "top") InAppType.Snackbar.Position.Gravity.VerticalGravity.TOP else InAppType.Snackbar.Position.Gravity.VerticalGravity.BOTTOM
                                                ),
                                                margin = InAppType.Snackbar.Position.Margin(
                                                    kind = when (payloadDto.content.position.margin.kind) {
                                                        "dp" -> {
                                                            InAppType.Snackbar.Position.Margin.MarginKind.DP
                                                        }

                                                        else -> {
                                                            error("Unknown margin cannot be mapped. Should never happen because of validators")
                                                        }
                                                    },
                                                    top = payloadDto.content.position.margin.top!!.roundToInt(),
                                                    left = payloadDto.content.position.margin.left!!.roundToInt(),
                                                    right = payloadDto.content.position.margin.right!!.roundToInt(),
                                                    bottom = payloadDto.content.position.margin.bottom!!.roundToInt()
                                                ),
                                            )
                                        )
                                    }
                                    null -> {
                                        return InAppConfig(
                                            listOf(),
                                            listOf(),
                                            mapOf(),
                                            listOf()
                                        ) // should never trigger because of validator
                                    }
                                }
                            } ?: emptyList()
                        ),
                        minVersion = inAppDto.sdkVersion?.minVersion,
                        maxVersion = inAppDto.sdkVersion?.maxVersion,
                        frequency = Frequency(getDelay(inAppDto.frequency))
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
                operations = inAppConfigResponse.settings?.operations?.map { (key, value) ->
                    key.enumValue<OperationName>() to OperationSystemName(value.systemName.lowercase())
                }?.toMap() ?: emptyMap(),
                abtests = inAppConfigResponse.abtests?.map { dto ->
                    ABTest(
                        id = dto.id,
                        minVersion = dto.sdkVersion?.minVersion,
                        maxVersion = dto.sdkVersion?.maxVersion,
                        salt = dto.salt,
                        variants = dto.variants?.map { variantDto ->
                            ABTest.Variant(
                                id = variantDto.id,
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
        } ?: InAppConfig(listOf(), listOf(), mapOf(), listOf())
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

                is TreeTargetingDto.VisitNodeDto -> TreeTargeting.VisitNode(
                    TreeTargetingDto.VisitNodeDto.VISIT_JSON_NAME,
                    treeTargetingDto.kind.enumValue(),
                    treeTargetingDto.value!!
                )

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
                    ids = treeTargetingDto.ids!!.map { it.toString() }
                )

                is TreeTargetingDto.CountryNodeDto -> TreeTargeting.CountryNode(
                    type = TreeTargetingDto.CountryNodeDto.COUNTRY_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    ids = treeTargetingDto.ids!!.map { it.toString() }
                )

                is TreeTargetingDto.RegionNodeDto -> TreeTargeting.RegionNode(
                    type = TreeTargetingDto.RegionNodeDto.REGION_JSON_NAME,
                    kind = if (treeTargetingDto.kind == "positive") Kind.POSITIVE else Kind.NEGATIVE,
                    ids = treeTargetingDto.ids!!.map { it.toString() }
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

                is TreeTargetingDto.PushPermissionDto -> TreeTargeting.PushPermissionNode(
                    type = TreeTargetingDto.PushPermissionDto.PUSH_PERMISSION_JSON_NAME,
                    value = treeTargetingDto.value!!
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

    fun mapToTtlDto(inAppTtlDtoBlank: SettingsDtoBlank.TtlDtoBlank) = TtlDto(
        inApps = inAppTtlDtoBlank.inApps
    )

    fun mapToSlidingExpiration(slidingExpirationDtoBlank: SettingsDtoBlank.SlidingExpirationDtoBlank) = SlidingExpirationDto(
        inappSession = slidingExpirationDtoBlank.inappSession
    )

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
