package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.*

internal class InAppStub {

    companion object {
        fun getInApp(): InApp = InApp(
            id = "",
            minVersion = null,
            maxVersion = null,
            targeting = getTargetingUnionNode().copy(
                type = "", nodes = listOf(
                    getTargetingTrueNode(), getTargetingSegmentNode()
                )
            ),
            form = Form(variants = listOf(getSimpleImage()))
        )

        fun getInAppDto(): InAppDto = InAppDto(
            id = "",
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = (TreeTargetingDto.TrueNodeDto("")),
            form = FormDto(variants = listOf(getSimpleImageDto()))
        )

        fun getInAppDtoBlank(): InAppConfigResponseBlank.InAppDtoBlank {
            return InAppConfigResponseBlank.InAppDtoBlank(
                id = "",
                sdkVersion = null,
                targeting = null,
                form = null
            )
        }

        fun getFormDto(): FormDto {
            return FormDto(emptyList())
        }

        fun getPayloadSimpleImage(): PayloadDto.SimpleImage {
            return PayloadDto.SimpleImage(
                type = null,
                imageUrl = null,
                redirectUrl = null,
                intentPayload = null
            )
        }

        fun getSdkVersion(): SdkVersion {
            return SdkVersion(minVersion = null, maxVersion = null)
        }

        fun getSimpleImageDto() = PayloadDto.SimpleImage(
            type = null,
            imageUrl = null,
            redirectUrl = null,
            intentPayload = null
        )

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

        fun getTargetingRegionNode(): TreeTargeting.RegionNode {
            return TreeTargeting.RegionNode(type = "", kind = Kind.POSITIVE, ids = emptyList())
        }

        fun getSimpleImage() = Payload.SimpleImage(
            type = "",
            imageUrl = "",
            redirectUrl = "",
            intentPayload = ""
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