package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName


internal sealed class TreeTargetingDto {

    internal data class TrueNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
    ) : TreeTargetingDto()

    internal data class IntersectionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("nodes")
        val nodes: List<TreeTargetingDto?>?,
    ) : TreeTargetingDto()

    internal data class UnionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("nodes")
        val nodes: List<TreeTargetingDto?>?,
    ) : TreeTargetingDto()

    internal data class SegmentNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("segmentation_external_id")
        val segmentationExternalId: String?,
        @SerializedName("segmentation_internal_id")
        val segmentationInternalId: String?,
        @SerializedName("segment_external_id")
        val segment_external_id: String?,
    ) : TreeTargetingDto()
}