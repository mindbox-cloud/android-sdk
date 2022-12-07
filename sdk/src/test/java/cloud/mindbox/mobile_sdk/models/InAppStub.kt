package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion

internal class InAppStub {

    companion object {
        fun getInApp(): InApp = InApp(id = "",
            minVersion = null,
            maxVersion = null,
            targeting = getTargetingUnionNode().copy(type = "", nodes = listOf(
                getTargetingTrueNode(), getTargetingSegmentNode())),
            form = Form(variants = listOf(getSimpleImage())))

        fun getInAppDto(): InAppDto = InAppDto(id = "",
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = (TreeTargetingDto.TrueNodeDto("")),
            form = FormDto(variants = listOf(getSimpleImageDto())))

        fun getSimpleImageDto() = PayloadDto.SimpleImage(type = null,
            imageUrl = null,
            redirectUrl = null,
            intentPayload = null)

        fun getTargetingTrueNode(): TreeTargeting.TrueNode {
            return TreeTargeting.TrueNode(type = "")
        }

        fun getTargetingSegmentNode(): TreeTargeting.SegmentNode {
            return TreeTargeting.SegmentNode(type = "",
                kind = Kind.NEGATIVE,
                segmentationExternalId = "",
                segmentationInternalId = "",
                segment_external_id = "")
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
                segment_external_id = "",
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
            return TreeTargeting.CountryNode(type = "", kind = Kind.POSITIVE, ids = emptyList(), "")
        }

        fun getTargetingCityNode(): TreeTargeting.CityNode {
            return TreeTargeting.CityNode(type = "", kind = Kind.POSITIVE, ids = emptyList(), "")
        }

        fun getTargetingRegionNode(): TreeTargeting.RegionNode {
            return TreeTargeting.RegionNode(type = "", kind = Kind.POSITIVE, ids = emptyList(), "")
        }

        fun getSimpleImage() = Payload.SimpleImage(
            type = "",
            imageUrl = "",
            redirectUrl = "",
            intentPayload = ""
        )
    }
}