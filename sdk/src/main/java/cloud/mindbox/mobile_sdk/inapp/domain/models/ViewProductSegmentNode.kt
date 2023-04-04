package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import com.google.gson.Gson
import org.koin.core.component.inject

internal data class ViewProductSegmentNode(
    override val type: String,
    val kind: Kind,
    val segmentationExternalId: String,
    val segmentExternalId: String,
) : OperationNodeBase(type) {

    private val mobileConfigRepository: MobileConfigRepository by inject()
    private val inAppSegmentationRepository: InAppSegmentationRepository by inject()
    private val gson: Gson by inject()

    override suspend fun fetchTargetingInfo() {
        val event = lastEvent as? InAppEventType.OrdinalEvent ?: return
        val body = gson.fromJson(event.body, OperationBodyRequest::class.java)
        body.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull()?.also { entry ->
            if (entry.value.isNullOrBlank()) return
            runCatching {
                inAppSegmentationRepository.fetchProductSegmentation(
                    entry.key to entry.value!!,
                    segmentationExternalId
                )
            }
        } ?: return
    }

    override suspend fun filterEvent(event: InAppEventType): Boolean {
        return inAppEventManager.isValidViewProductEvent(event)
    }

    override fun checkTargeting(): Boolean {
        val event = lastEvent as? InAppEventType.OrdinalEvent ?: return false
        val body = gson.fromJson(event.body, OperationBodyRequest::class.java)
        val id =
            body.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull()?.value
                ?: return false
        val segmentationsResult =
            inAppSegmentationRepository.getProductSegmentation(id)?.productSegmentations?.first()?.productList
                ?: return false
        return when (kind) {
            Kind.POSITIVE -> segmentationsResult.any { segmentationWrapper -> segmentationWrapper.segmentationExternalId == segmentationExternalId && segmentationWrapper.segmentExternalId == segmentExternalId }
            Kind.NEGATIVE -> segmentationsResult.find { it.segmentationExternalId == segmentationExternalId }
                ?.segmentExternalId
                ?.let { it != segmentExternalId } == true
        }
    }

    override suspend fun getOperationsSet(): Set<String> {
        return mobileConfigRepository.getOperations()[OperationName.VIEW_PRODUCT]?.systemName?.let {
            setOf(it)
        } ?: setOf()
    }
}
