package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal interface ITargeting {
    fun checkTargeting(data: TargetingData): Boolean
}

internal sealed interface TargetingData {
    interface OperationName : TargetingData {
        val triggerEventName: String
    }

    interface OperationBody : TargetingData {
        val operationBody: String?
    }
}

internal interface TargetingInfo {
    suspend fun fetchTargetingInfo(data: TargetingData)

    fun hasSegmentationNode(): Boolean

    fun hasGeoNode(): Boolean

    fun hasOperationNode(): Boolean

    suspend fun getOperationsSet(): Set<String>
}

internal enum class Kind {
    POSITIVE,
    NEGATIVE
}

internal enum class KindVisit {
    GTE,
    LTE,
    EQUALS,
    NOT_EQUALS
}

internal enum class KindAny {
    ANY,
    NONE,
}

internal enum class KindSubstring {
    SUBSTRING,
    NOT_SUBSTRING,
    STARTS_WITH,
    ENDS_WITH
}

internal sealed class TreeTargeting(open val type: String) :
    ITargeting, TargetingInfo {

    internal data class TrueNode(override val type: String) : TreeTargeting(type) {

        override fun checkTargeting(data: TargetingData): Boolean {
            return true
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            return
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return false
        }

        override fun hasOperationNode(): Boolean {
            return false
        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }
    }

    internal data class CountryNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {

        private val inAppGeoRepositoryImpl by mindboxInject { inAppGeoRepository }

        override fun checkTargeting(data: TargetingData): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val countryId = inAppGeoRepositoryImpl.getGeo().countryId
            return if (kind == Kind.POSITIVE) ids.contains(countryId) else ids.contains(countryId)
                .not()
        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() == GeoFetchStatus.GEO_NOT_FETCHED) {
                inAppGeoRepositoryImpl.fetchGeo()
            }
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return true
        }

        override fun hasOperationNode(): Boolean {
            return false
        }
    }

    internal data class CityNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {

        private val inAppGeoRepositoryImpl by mindboxInject { inAppGeoRepository }

        override fun checkTargeting(data: TargetingData): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val cityId = inAppGeoRepositoryImpl.getGeo().cityId
            return if (kind == Kind.POSITIVE) ids.contains(cityId) else ids.contains(cityId)
                .not()
        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() == GeoFetchStatus.GEO_NOT_FETCHED) {
                inAppGeoRepositoryImpl.fetchGeo()
            }
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return true
        }

        override fun hasOperationNode(): Boolean {
            return false
        }
    }

    internal data class RegionNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {

        private val inAppGeoRepositoryImpl by mindboxInject { inAppGeoRepository }

        override fun checkTargeting(data: TargetingData): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val regionId = inAppGeoRepositoryImpl.getGeo().regionId
            return if (kind == Kind.POSITIVE) ids.contains(regionId) else ids.contains(regionId)
                .not()
        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() == GeoFetchStatus.GEO_NOT_FETCHED) {
                inAppGeoRepositoryImpl.fetchGeo()
            }
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return true
        }

        override fun hasOperationNode(): Boolean {
            return false
        }
    }

    internal data class IntersectionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
        override fun checkTargeting(data: TargetingData): Boolean {
            var rez = true
            for (node in nodes) {
                if (node.checkTargeting(data).not()) {
                    rez = false
                }
            }
            return rez
        }

        override suspend fun getOperationsSet(): Set<String> {
            return nodes.flatMap { treeTargeting ->
                treeTargeting.getOperationsSet()
            }.toSet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            for (node in nodes) {
                node.fetchTargetingInfo(data)
            }
        }

        override fun hasSegmentationNode(): Boolean {
            for (node in nodes) {
                if (node.hasSegmentationNode())
                    return true
            }
            return false
        }

        override fun hasGeoNode(): Boolean {
            for (node in nodes) {
                if (node.hasGeoNode())
                    return true
            }
            return false
        }

        override fun hasOperationNode(): Boolean {
            for (node in nodes) {
                if (node.hasOperationNode())
                    return true
            }
            return false
        }

    }

    internal data class UnionNode(
        override val type: String,
        val nodes: List<TreeTargeting>,
    ) : TreeTargeting(type) {
        override fun checkTargeting(data: TargetingData): Boolean {
            var rez = false
            for (node in nodes) {
                val check = node.checkTargeting(data)
                mindboxLogD("Check UnionNode ${node.type}: $check")
                if (check) {
                    rez = true
                }
            }
            return rez
        }

        override suspend fun getOperationsSet(): Set<String> {
            return nodes.flatMap { treeTargeting ->
                treeTargeting.getOperationsSet()
            }.toSet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            for (node in nodes) {
                node.fetchTargetingInfo(data)
            }
        }

        override fun hasSegmentationNode(): Boolean {
            for (node in nodes) {
                if (node.hasSegmentationNode())
                    return true
            }
            return false
        }

        override fun hasGeoNode(): Boolean {
            for (node in nodes) {
                if (node.hasGeoNode())
                    return true
            }
            return false
        }

        override fun hasOperationNode(): Boolean {
            for (node in nodes) {
                if (node.hasOperationNode())
                    return true
            }
            return false
        }
    }

    internal data class SegmentNode(
        override val type: String,
        val kind: Kind,
        val segmentationExternalId: String,
        val segmentExternalId: String,
    ) : TreeTargeting(type) {

        private val inAppSegmentationRepository by mindboxInject { inAppSegmentationRepository }

        override fun checkTargeting(data: TargetingData): Boolean {
            if (inAppSegmentationRepository.getCustomerSegmentationFetched() != CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS) return false
            val segmentationsWrapperList = inAppSegmentationRepository.getCustomerSegmentations()
            return when (kind) {
                Kind.POSITIVE -> segmentationsWrapperList.find { segmentationWrapper -> segmentationWrapper.segmentation == segmentationExternalId }?.segment == segmentExternalId
                Kind.NEGATIVE -> segmentationsWrapperList.find { it.segmentation == segmentationExternalId }
                    ?.segment
                    ?.let { it != segmentExternalId } == true
            }

        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            if (inAppSegmentationRepository.getCustomerSegmentationFetched() == CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED) {
                inAppSegmentationRepository.fetchCustomerSegmentations()
            }
        }

        override fun hasSegmentationNode(): Boolean {
            return true
        }

        override fun hasGeoNode(): Boolean {
            return false
        }

        override fun hasOperationNode(): Boolean {
            return false
        }
    }

    internal data class VisitNode(override val type: String, val kind: KindVisit, val value: Long) :
        TreeTargeting(type) {

        override fun checkTargeting(data: TargetingData): Boolean {
            val userVisitCount = MindboxPreferences.userVisitCount.toLong()
            return when (kind) {
                KindVisit.GTE -> {
                    value >= userVisitCount
                }

                KindVisit.LTE -> {
                    value <= userVisitCount
                }

                KindVisit.EQUALS -> {
                    value == userVisitCount
                }

                KindVisit.NOT_EQUALS -> {
                    value != userVisitCount
                }
            }
        }

        override suspend fun fetchTargetingInfo(data: TargetingData) {
            return
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return false
        }

        override fun hasOperationNode(): Boolean {
            return false
        }

        override suspend fun getOperationsSet(): Set<String> {
            return emptySet()
        }
    }
}