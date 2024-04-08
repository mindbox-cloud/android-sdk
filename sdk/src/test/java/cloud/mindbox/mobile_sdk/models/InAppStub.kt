package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion

internal class InAppStub {

    companion object {
        fun getInApp(): InApp = InApp(
            id = "",
            minVersion = null,
            frequency = getFrequency(),
            maxVersion = null,
            targeting = getTargetingUnionNode().copy(
                type = "", nodes = listOf(
                    getTargetingTrueNode(), getTargetingSegmentNode()
                )
            ),
            form = Form(variants = listOf(getModalWindow()))
        )

        fun getInAppDto(): InAppDto = InAppDto(
            id = "",
            frequency = getFrequencyOnceDto(),
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = (TreeTargetingDto.TrueNodeDto("")),
            form = FormDto(variants = listOf(getModalWindowDto()))
        )

        fun getFrequencyOnceDto(): FrequencyDto.FrequencyOnceDto = FrequencyDto.FrequencyOnceDto(
            type = "", kind = ""
        )

        fun getFrequencyPeriodicDto(): FrequencyDto.FrequencyPeriodicDto=  FrequencyDto.FrequencyPeriodicDto("", "", 0)

        fun getFrequency(): Frequency = Frequency(Frequency.Delay.LifetimeDelay)

        fun getSnackbarContentDto(): PayloadDto.SnackbarDto.ContentDto =
            PayloadDto.SnackbarDto.ContentDto(
                background = getBackgroundDto(),
                elements = listOf(getCloseButtonElementDto()),
                position = PayloadDto.SnackbarDto.ContentDto.PositionDto(
                    gravity = getGravityDto(),
                    margin = getMarginDto()
                )
            )

        fun getGravityDto() = PayloadDto.SnackbarDto.ContentDto.PositionDto.GravityDto(
            horizontal = "",
            vertical = ""
        )

        fun getMarginDto() = PayloadDto.SnackbarDto.ContentDto.PositionDto.MarginDto(
            bottom = 0.0,
            kind = "",
            left = 0.0,
            right = 0.0,
            top = 0.0
        )

        fun getModalWindowContentDto(): PayloadDto.ModalWindowDto.ContentDto =
            PayloadDto.ModalWindowDto.ContentDto(
                background = getBackgroundDto(),
                elements = listOf(getCloseButtonElementDto())
            )

        fun getBackgroundDto(): BackgroundDto =
            BackgroundDto(
                layers = listOf(getImageLayerDto())
            )

        fun getImageLayerDto(): BackgroundDto.LayerDto.ImageLayerDto =
            BackgroundDto.LayerDto.ImageLayerDto(
                action = getRedirectUrlActionDto(),
                source = getUrlSourceDto(),
                type = "image"
            )

        fun getRedirectUrlActionDto(): BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto(
                intentPayload = "",
                type = "redirectUrl",
                value = ""
            )

        fun getPushPermissionActionDto(): BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto(
                intentPayload = "",
                type = "pushPermission",
            )

        fun getRedirectUrlAction(): Layer.ImageLayer.Action.RedirectUrlAction =
            Layer.ImageLayer.Action.RedirectUrlAction(
                payload = "",
                url = ""
            )

        fun getPushPermissionAction(): Layer.ImageLayer.Action.PushPermissionAction=
            Layer.ImageLayer.Action.PushPermissionAction(
                payload = ""
            )
        fun getUrlSourceDto(): BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto =
            BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto(
                type = "url",
                value = ""
            )
        fun getUrlSource(): Layer.ImageLayer.Source.UrlSource =
            Layer.ImageLayer.Source.UrlSource(url = "")

        fun getCloseButtonElementDto(): ElementDto.CloseButtonElementDto =
            ElementDto.CloseButtonElementDto(
                color = "null",
                lineWidth = 0.0,
                position = getElementPositionDto(),
                size = getElementSizeDto(),
                type = "closeButton"
            )

        fun getElementSizeDto(): ElementDto.CloseButtonElementDto.SizeDto =
            ElementDto.CloseButtonElementDto.SizeDto(
                height = 0.0,
                kind = "",
                width = 0.0
            )

        fun getElementPositionDto(): ElementDto.CloseButtonElementDto.PositionDto =
            ElementDto.CloseButtonElementDto.PositionDto(
                margin = getElementMarginDto()
            )

        fun getElementMarginDto(): ElementDto.CloseButtonElementDto.PositionDto.MarginDto =
            ElementDto.CloseButtonElementDto.PositionDto.MarginDto(
                bottom = null,
                kind = null,
                left = null,
                right = null,
                top = null
            )

        fun getInAppDtoBlank(): InAppConfigResponseBlank.InAppDtoBlank {
            return InAppConfigResponseBlank.InAppDtoBlank(
                id = "",
                sdkVersion = null,
                targeting = null,
                frequency = null,
                form = null
            )
        }

        fun getFormDto(): FormDto {
            return FormDto(emptyList())
        }

        fun getSdkVersion(): SdkVersion {
            return SdkVersion(minVersion = null, maxVersion = null)
        }

        fun getModalWindowDto() =
            PayloadDto.ModalWindowDto(content = getModalWindowContentDto(), type = "modal")

        fun getSnackbarDto() =
            PayloadDto.SnackbarDto(content = getSnackbarContentDto(), type = "snackbar")

        fun getTargetingTrueNode(): TreeTargeting.TrueNode {
            return TreeTargeting.TrueNode(type = "")
        }

        fun getTargetingSegmentNode(): TreeTargeting.SegmentNode {
            return TreeTargeting.SegmentNode(
                type = "",
                kind = Kind.NEGATIVE,
                segmentationExternalId = "",
                segmentExternalId = ""
            )
        }

        fun getTargetingOperationNode(): OperationNode {
            return OperationNode(
                type = "",
                systemName = ""
            )
        }

        fun getTargetingOperationNodeDto(): TreeTargetingDto.OperationNodeDto {
            return TreeTargetingDto.OperationNodeDto(
                type = "",
                systemName = ""
            )
        }

        fun getTargetingViewProductNodeDto(): TreeTargetingDto.ViewProductNodeDto {
            return TreeTargetingDto.ViewProductNodeDto(
                type = "",
                kind = "",
                value = ""
            )
        }

        fun getTargetingViewProductSegmentNodeDto(): TreeTargetingDto.ViewProductSegmentNodeDto {
            return TreeTargetingDto.ViewProductSegmentNodeDto(
                type = "",
                kind = "",
                segmentExternalId = "",
                segmentationExternalId = "",
                segmentationInternalId = ""
            )
        }

        fun getTargetingViewProductSegmentNode(): ViewProductSegmentNode {
            return ViewProductSegmentNode(
                type = "",
                kind = Kind.NEGATIVE,
                segmentationExternalId = "",
                segmentExternalId = "",
            )
        }

        fun getTargetingViewProductNode(): ViewProductNode {
            return ViewProductNode(
                type = "",
                kind = KindSubstring.SUBSTRING,
                value = ""
            )
        }

        fun getTargetingUnionNode(): TreeTargeting.UnionNode {
            return TreeTargeting.UnionNode(type = "", nodes = emptyList())
        }

        fun getTargetingIntersectionNode(): TreeTargeting.IntersectionNode {
            return TreeTargeting.IntersectionNode(type = "", nodes = emptyList())
        }

        fun getTargetingIntersectionNodeDto(): TreeTargetingDto.IntersectionNodeDto {
            return TreeTargetingDto.IntersectionNodeDto(type = "", nodes = emptyList())
        }

        fun getTargetingTrueNodeDto(): TreeTargetingDto.TrueNodeDto {
            return TreeTargetingDto.TrueNodeDto(type = "")
        }

        fun getTargetingUnionNodeDto(): TreeTargetingDto.UnionNodeDto {
            return TreeTargetingDto.UnionNodeDto(type = "", nodes = emptyList())
        }

        fun getTargetingSegmentNodeDto(): TreeTargetingDto.SegmentNodeDto {
            return TreeTargetingDto.SegmentNodeDto(
                type = "",
                kind = "",
                segmentExternalId = "",
                segmentationExternalId = "",
                segmentationInternalId = ""
            )
        }

        fun getTargetingCountryNodeDto(): TreeTargetingDto.CountryNodeDto {
            return TreeTargetingDto.CountryNodeDto(type = "", kind = "", ids = emptyList())
        }

        fun getTargetingCityNodeDto(): TreeTargetingDto.CityNodeDto {
            return TreeTargetingDto.CityNodeDto(type = "", kind = "", ids = emptyList())
        }

        fun getTargetingRegionNodeDto(): TreeTargetingDto.RegionNodeDto {
            return TreeTargetingDto.RegionNodeDto(type = "", kind = "", ids = emptyList())
        }

        fun getTargetingCountryNode(): TreeTargeting.CountryNode {
            return TreeTargeting.CountryNode(type = "", kind = Kind.POSITIVE, ids = emptyList())
        }

        fun getTargetingCityNode(): TreeTargeting.CityNode {
            return TreeTargeting.CityNode(type = "", kind = Kind.POSITIVE, ids = emptyList())
        }

        fun getTargetingPushPermissionNodeDto(): TreeTargetingDto.PushPermissionDto {
            return TreeTargetingDto.PushPermissionDto(type = null, value = null)
        }

        fun getTargetingPushPermissionNode(): TreeTargeting.PushPermissionNode {
            return TreeTargeting.PushPermissionNode(type = "", value = false)
        }

        fun getTargetingVisitNodeDto(): TreeTargetingDto.VisitNodeDto {
            return TreeTargetingDto.VisitNodeDto(type = null, kind = null, value = null)
        }

        fun getTargetingVisitNode(): TreeTargeting.VisitNode {
            return TreeTargeting.VisitNode(type = "", kind = KindVisit.GTE, value = 0L)
        }

        fun getTargetingRegionNode(): TreeTargeting.RegionNode {
            return TreeTargeting.RegionNode(type = "", kind = Kind.POSITIVE, ids = emptyList())
        }

        fun getModalWindow() = InAppType.ModalWindow(
            type = "", inAppId = "", layers = listOf(), elements = listOf(),
        )

        val viewProductNode: ViewProductNode = ViewProductNode(
            type = "", kind = KindSubstring.SUBSTRING, value = ""
        )

        val viewProductSegmentNode: ViewProductSegmentNode = ViewProductSegmentNode(
            type = "", kind = Kind.POSITIVE, segmentExternalId = "", segmentationExternalId = ""
        )

        val viewProductCategoryNode: ViewProductCategoryNode
            get() = ViewProductCategoryNode(
                type = "viewProductCategoryId",
                kind = KindSubstring.SUBSTRING,
                value = ""
            )

        val viewProductCategoryInNode: ViewProductCategoryInNode
            get() = ViewProductCategoryInNode(
                type = "viewProductCategoryIdIn",
                kind = KindAny.ANY,
                values = listOf()
            )

        val viewProductCategoryNodeDto: TreeTargetingDto.ViewProductCategoryNodeDto
            get() = TreeTargetingDto.ViewProductCategoryNodeDto(
                type = "",
                kind = "",
                value = ""
            )

        val viewProductCategoryInNodeDto: TreeTargetingDto.ViewProductCategoryInNodeDto
            get() = TreeTargetingDto.ViewProductCategoryInNodeDto(
                type = "",
                kind = "",
                values = listOf()
            )

        val viewProductCategoryInValueDto: TreeTargetingDto.ViewProductCategoryInNodeDto.ValueDto
            get() = TreeTargetingDto.ViewProductCategoryInNodeDto.ValueDto(
                id = "",
                externalSystemName = "",
                externalId = "",
            )

    }
}