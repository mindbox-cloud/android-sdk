package cloud.mindbox.mobile_sdk.inapp.domain.models

internal interface ITargeting {
    fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean
}

internal interface PreCheckTargeting {
    fun preCheckTargeting(): SegmentationCheckResult
}

internal enum class Kind {
    POSITIVE,
    NEGATIVE
}

internal sealed class TreeTargeting(open val type: String) : ITargeting, PreCheckTargeting {

    internal data class TrueNode(override val type: String) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            return true
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.TRUE
        }
    }

    internal data class IntersectionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            var rez = true
            for (node in nodes) {
                for (csia in csiaList) {
                    if ((node is SegmentNode)) {
                        if (node.shouldProcessSegmentation(csia.segmentation) && node.getCustomerIsInTargeting(
                                csiaList).not()
                        ) {
                            rez = false
                        }
                    } else {
                        if (node.getCustomerIsInTargeting(csiaList).not()) {
                            rez = false
                        }
                    }
                }
            }
            return rez
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            var rez: SegmentationCheckResult = SegmentationCheckResult.TRUE
            for (node in nodes) {
                if (node.preCheckTargeting() == SegmentationCheckResult.PENDING) {
                    rez = SegmentationCheckResult.PENDING
                }
            }
            return rez
        }
    }

    internal data class UnionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            var rez = false
            for (node in nodes) {
                for (csia in csiaList) {
                    if (node is SegmentNode) {
                        if (node.shouldProcessSegmentation(csia.segmentation) && node.getCustomerIsInTargeting(
                                csiaList)
                        ) {
                            rez = true
                        }
                    } else {
                        if (node.getCustomerIsInTargeting(csiaList)) {
                            rez = true
                        }
                    }
                }

            }
            return rez
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            var rez: SegmentationCheckResult = SegmentationCheckResult.TRUE
            for (node in nodes) {
                if (node.preCheckTargeting() == SegmentationCheckResult.PENDING) {
                    rez = SegmentationCheckResult.PENDING
                }
            }
            return rez
        }
    }

    internal data class SegmentNode(
        override val type: String,
        val kind: Kind,
        val segmentationExternalId: String,
        val segmentExternalId: String,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            return when (kind) {
                Kind.POSITIVE -> csiaList.find { csia -> csia.segmentation == segmentationExternalId }
                    ?.segment == segmentExternalId
                Kind.NEGATIVE -> csiaList.find { csia -> csia.segmentation == segmentationExternalId }
                    ?.segment
                    ?.let { it != segmentExternalId } == true

            }
        }

        fun shouldProcessSegmentation(segmentation: String): Boolean {
            return (segmentation == segmentationExternalId)
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.PENDING
        }
    }
}

enum class SegmentationCheckResult {
    TRUE,
    PENDING
}