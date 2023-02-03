package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.inapp.domain.InAppGeoRepository
import org.koin.core.component.inject

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

internal sealed class TreeTargeting(open val type: String) : ITargeting, PreCheckTargeting,
    MindboxKoin.MindboxKoinComponent {

    protected val inAppGeoRepositoryImpl: InAppGeoRepository by inject()

    internal data class TrueNode(override val type: String) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            return true
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.IMMEDIATE
        }
    }

    internal data class CountryNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {


        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            val countryId = inAppGeoRepositoryImpl.geoGeo().countryId
            return if (kind == Kind.POSITIVE) ids.contains(countryId) else ids.contains(countryId)
                .not()
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.IMMEDIATE
        }
    }

    internal data class CityNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {

        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            val cityId = inAppGeoRepositoryImpl.geoGeo().cityId
            return if (kind == Kind.POSITIVE) ids.contains(cityId) else ids.contains(cityId)
                .not()
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.IMMEDIATE
        }
    }

    internal data class RegionNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            val regionId = inAppGeoRepositoryImpl.geoGeo().regionId
            return if (kind == Kind.POSITIVE) ids.contains(regionId) else ids.contains(regionId)
                .not()
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            return SegmentationCheckResult.IMMEDIATE
        }
    }

    internal data class IntersectionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
        override fun getCustomerIsInTargeting(csiaList: List<CustomerSegmentationInApp>): Boolean {
            var rez = true
            for (node in nodes) {
                if ((node is SegmentNode)) {
                    for (csia in csiaList) {
                        if (node.shouldProcessSegmentation(csia.segmentation) && node.getCustomerIsInTargeting(
                                csiaList).not()
                        ) {
                            rez = false
                        }
                    }
                } else {
                    if (node.getCustomerIsInTargeting(csiaList).not()) {
                        rez = false
                    }
                }
            }
            return rez
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            var rez: SegmentationCheckResult = SegmentationCheckResult.IMMEDIATE
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
                if (node is SegmentNode) {
                    for (csia in csiaList) {
                        if (node.shouldProcessSegmentation(csia.segmentation) && node.getCustomerIsInTargeting(
                                csiaList)
                        ) {
                            rez = true
                        }
                    }
                } else {
                    if (node.getCustomerIsInTargeting(csiaList)) {
                        rez = true
                    }
                }
            }
            return rez
        }

        override fun preCheckTargeting(): SegmentationCheckResult {
            var rez: SegmentationCheckResult = SegmentationCheckResult.IMMEDIATE
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
                Kind.POSITIVE -> csiaList.find { csia -> csia.segmentation == segmentationExternalId }?.segment == segmentExternalId
                Kind.NEGATIVE -> csiaList.find { it.segmentation == segmentationExternalId }
                    ?.segment
                    ?.let { it != segmentExternalId } == true
            }

        }

        fun shouldProcessSegmentation(segmentation: String?): Boolean {
            return (segmentation == segmentationExternalId)
        }
    }

    override fun preCheckTargeting(): SegmentationCheckResult {
        return SegmentationCheckResult.PENDING
    }
}

enum class SegmentationCheckResult {
    IMMEDIATE,
    PENDING
}