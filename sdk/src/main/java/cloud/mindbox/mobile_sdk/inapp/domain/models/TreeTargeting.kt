package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType
import com.android.volley.VolleyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.inject

internal interface ITargeting {
    fun checkTargeting(): Boolean
}

internal interface TargetingInfo {
    suspend fun fetchTargetingInfo()

    fun hasSegmentationNode(): Boolean

    fun hasGeoNode(): Boolean

    fun hasOperationNode(): Boolean

    fun getOperationsSet(): Set<String>
}

internal enum class Kind {
    POSITIVE,
    NEGATIVE
}


internal sealed class TreeTargeting(open val type: String) : ITargeting, TargetingInfo,
    MindboxKoin.MindboxKoinComponent {

    internal data class TrueNode(override val type: String) : TreeTargeting(type) {

        override fun checkTargeting(): Boolean {
            return true
        }

        override suspend fun fetchTargetingInfo() {
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

        override fun getOperationsSet(): Set<String> {
            return emptySet()
        }
    }

    internal data class OperationNode(
        override val type: String,
        val systemName: String,
    ) : TreeTargeting(type) {

        private var lastEvent: InAppEventType? = null
        private val operationNodeScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default + Mindbox.coroutineExceptionHandler)

        init {
            operationNodeScope.launch {
                MindboxEventManager.eventFlow.collect { inAppEventType ->
                    lastEvent = inAppEventType
                }
            }
        }

        override fun checkTargeting(): Boolean {
            return lastEvent?.name == systemName
        }

        override suspend fun fetchTargetingInfo() {
            return
        }

        override fun hasSegmentationNode(): Boolean {
            return false
        }

        override fun hasGeoNode(): Boolean {
            return false
        }

        override fun hasOperationNode(): Boolean {
            return true
        }

        override fun getOperationsSet(): Set<String> {
            return setOf(systemName)
        }

    }

    internal data class CountryNode(
        override val type: String,
        val kind: Kind,
        val ids: List<String>,
    ) : TreeTargeting(type) {

        private val inAppGeoRepositoryImpl: InAppGeoRepository by inject()

        override fun checkTargeting(): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val countryId = inAppGeoRepositoryImpl.getGeo().countryId
            return if (kind == Kind.POSITIVE) ids.contains(countryId) else ids.contains(countryId)
                .not()
        }

        override fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo() {
            runCatching {
                if (inAppGeoRepositoryImpl.getGeoFetchedStatus() == GeoFetchStatus.GEO_NOT_FETCHED) {
                    inAppGeoRepositoryImpl.fetchGeo()
                }
            }.onFailure { throwable ->
                if (throwable is VolleyError) {
                    MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                } else {
                    throw throwable
                }
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

        private val inAppGeoRepositoryImpl: InAppGeoRepository by inject()


        override fun checkTargeting(): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val cityId = inAppGeoRepositoryImpl.getGeo().cityId
            return if (kind == Kind.POSITIVE) ids.contains(cityId) else ids.contains(cityId)
                .not()
        }

        override fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo() {
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

        private val inAppGeoRepositoryImpl: InAppGeoRepository by inject()

        override fun checkTargeting(): Boolean {
            if (inAppGeoRepositoryImpl.getGeoFetchedStatus() != GeoFetchStatus.GEO_FETCH_SUCCESS) return false
            val regionId = inAppGeoRepositoryImpl.getGeo().regionId
            return if (kind == Kind.POSITIVE) ids.contains(regionId) else ids.contains(regionId)
                .not()
        }

        override fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo() {
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
        override fun checkTargeting(): Boolean {
            var rez = true
            for (node in nodes) {
                if (node.checkTargeting().not()) {
                    rez = false
                }
            }
            return rez
        }

        override fun getOperationsSet(): Set<String> {
            return nodes.flatMap { treeTargeting ->
                treeTargeting.getOperationsSet()
            }.toSet()
        }

        override suspend fun fetchTargetingInfo() {
            for (node in nodes) {
                node.fetchTargetingInfo()
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
        override fun checkTargeting(): Boolean {
            var rez = false
            for (node in nodes) {
                if (node.checkTargeting()) {
                    rez = true
                }
            }
            return rez
        }

        override fun getOperationsSet(): Set<String> {
            return nodes.flatMap { treeTargeting ->
                treeTargeting.getOperationsSet()
            }.toSet()
        }

        override suspend fun fetchTargetingInfo() {
            for (node in nodes) {
                node.fetchTargetingInfo()
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

        private val inAppSegmentationRepository: InAppSegmentationRepository by inject()

        override fun checkTargeting(): Boolean {
            if (inAppSegmentationRepository.getSegmentationFetched() != SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS) return false
            val segmentationsWrapperList = inAppSegmentationRepository.getSegmentations()
            return when (kind) {
                Kind.POSITIVE -> segmentationsWrapperList.find { segmentationWrapper -> segmentationWrapper.segmentation == segmentationExternalId }?.segment == segmentExternalId
                Kind.NEGATIVE -> segmentationsWrapperList.find { it.segmentation == segmentationExternalId }
                    ?.segment
                    ?.let { it != segmentExternalId } == true
            }

        }

        override fun getOperationsSet(): Set<String> {
            return emptySet()
        }

        override suspend fun fetchTargetingInfo() {
            if (inAppSegmentationRepository.getSegmentationFetched() == SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED) {
                inAppSegmentationRepository.fetchSegmentations()
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
}