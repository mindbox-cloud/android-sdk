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
        @SerializedName("segmentationExternalId")
        val segmentationExternalId: String?,
        @SerializedName("segmentationInternalId")
        val segmentationInternalId: String?,
        @SerializedName("segmentExternalId")
        val segmentExternalId: String?,
    ) : TreeTargetingDto()

    internal data class CountryNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String?>?,
    ) : TreeTargetingDto()

    internal data class CityNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String>?,
    ) : TreeTargetingDto()

    internal data class RegionNodeDto(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("kind")
        val kind: String?,
        @SerializedName("ids")
        val ids: List<String?>?,
    ) : TreeTargetingDto()
}