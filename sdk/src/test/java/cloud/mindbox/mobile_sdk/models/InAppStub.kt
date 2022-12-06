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
            targeting = getTargetingUnionNode().copy(type = "or", nodes = listOf(
                getTargetingTrueNode(), getTargetingSegmentNode())),
            form = Form(variants = listOf(getSimpleImage())))

        fun getInAppDto(): InAppDto = InAppDto(id = "",
            sdkVersion = SdkVersion(minVersion = null, maxVersion = null),
            targeting = (TreeTargetingDto.TrueNodeDto("true")),
            form = FormDto(variants = listOf(getSimpleImageDto())))

        fun getSimpleImageDto() = PayloadDto.SimpleImage(type = null,
            imageUrl = null,
            redirectUrl = null,
            intentPayload = null)

        fun getTargetingTrueNode(): TreeTargeting.TrueNode {
            return TreeTargeting.TrueNode(type = "true")
        }

        fun getTargetingSegmentNode(): TreeTargeting.SegmentNode {
            return TreeTargeting.SegmentNode(type = "segment",
                kind = Kind.NEGATIVE,
                segmentationExternalId = "",
                segmentationInternalId = "",
                segment_external_id = "")
        }

        fun getTargetingUnionNode(): TreeTargeting.UnionNode {
            return TreeTargeting.UnionNode(type = "or", nodes = emptyList())
        }

        fun getTargetingIntersectionNode(): TreeTargeting.IntersectionNode {
            return TreeTargeting.IntersectionNode(type = "and", nodes = emptyList())
        }

        fun getTargetingIntersectionNodeDto(): TreeTargetingDto.IntersectionNodeDto {
            return TreeTargetingDto.IntersectionNodeDto(type = "and", nodes = emptyList())
        }

        fun getTargetingTrueNodeDto(): TreeTargetingDto.TrueNodeDto {
            return TreeTargetingDto.TrueNodeDto(type = "true")
        }

        fun getTargetingUnionNodeDto(): TreeTargetingDto.UnionNodeDto {
            return TreeTargetingDto.UnionNodeDto(type = "or", nodes = emptyList())
        }

        fun getTargetingSegmentNodeDto(): TreeTargetingDto.SegmentNodeDto {
            return TreeTargetingDto.SegmentNodeDto(
                type = "segment",
                kind = "",
                segment_external_id = "",
                segmentationExternalId = "",
                segmentationInternalId = ""
            )
        }

        fun getSimpleImage() = Payload.SimpleImage(
            type = "",
            imageUrl = "",
            redirectUrl = "",
            intentPayload = ""
        )
    }
}