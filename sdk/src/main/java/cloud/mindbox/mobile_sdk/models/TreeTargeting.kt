package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName

internal interface ITargeting {
    fun getCustomerIsInTargeting(segment: String): Boolean
}


internal data class Node(
    @SerializedName("${"$"}type") val type: String,
    @SerializedName("kind") val kind: String,
    @SerializedName("segmentation_external_id") val segmentationExternalId: String,
    @SerializedName("segmentation_internal_id") val segmentationInternalId: String,
    @SerializedName("segment_external_id") val segment_external_id: String,
) : ITargeting {
    override fun getCustomerIsInTargeting(segment: String): Boolean {
        return if (kind == "positive")
            segment == segment_external_id
        else segment != segment_external_id
    }

}

internal sealed class TreeTargeting : ITargeting {

    data class TrueNode(@SerializedName("${"$"}type") val type: String) : TreeTargeting() {
        override fun getCustomerIsInTargeting(segment: String): Boolean {
            return true
        }
    }

    internal data class IntersectionNode(
        @SerializedName("${"$"}type") val type: String,
        @SerializedName("nodes") val nodes: List<Node>,
    ) : ITargeting {
        override fun getCustomerIsInTargeting(segment: String): Boolean {
            var rez = true
            for (node in nodes) {
                if (node.getCustomerIsInTargeting(segment).not()) {
                    rez = false
                }
            }
            return rez
        }
    }

    internal data class UnionNode(
        @SerializedName("${"$"}type") val type: String,
        @SerializedName("nodes") val nodes: List<Node>,
    ) : ITargeting {
        override fun getCustomerIsInTargeting(segment: String): Boolean {
            var rez = false
            for (node in nodes) {
                if (node.getCustomerIsInTargeting(segment)) {
                    rez = true
                }
            }
            return rez
        }
    }
}