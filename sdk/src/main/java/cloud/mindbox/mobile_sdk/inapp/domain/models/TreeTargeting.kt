package cloud.mindbox.mobile_sdk.inapp.domain.models

internal interface ITargeting {
    fun getCustomerIsInTargeting(segment: String): Boolean
}

internal enum class Kind {
    POSITIVE,
    NEGATIVE
}

internal sealed class TreeTargeting(open val type: String) : ITargeting {

    data class TrueNode(override val type: String) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(segment: String): Boolean {
            return true
        }
    }

    internal data class IntersectionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
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
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
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

    internal data class SegmentNode(
        override val type: String,
        val kind: Kind,
        val segmentationExternalId: String,
        val segmentationInternalId: String,
        val segment_external_id: String,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(segment: String): Boolean {
            return when (kind) {
                Kind.POSITIVE -> segment_external_id == segment
                Kind.NEGATIVE -> segment_external_id != segment
            }
        }
    }
}