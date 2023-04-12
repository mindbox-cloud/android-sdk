package cloud.mindbox.mobile_sdk.inapp.domain.models

import androidx.work.workDataOf
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
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

    override suspend fun fetchTargetingInfo(data: TargetingData) {
        if (data !is TargetingData.OperationBody) return
        val body = gson.fromJson(data.operationBody, OperationBodyRequest::class.java)
        body?.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull()?.also { entry ->
            if (entry.value.isNullOrBlank()) return
            if (inAppSegmentationRepository.getProductSegmentationFetched() == ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED) {
                runCatching {
                    inAppSegmentationRepository.fetchProductSegmentation(
                        entry.key to entry.value!!
                    )
                }.onFailure { error ->
                    if (error is ProductSegmentationError) {
                        inAppSegmentationRepository.setProductSegmentationFetchStatus(ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR)
                        mindboxLogE("Error fetching product segmentations")
                    }
                }
            }
        } ?: return
    }

    override fun checkTargeting(data: TargetingData): Boolean {
        if (data !is TargetingData.OperationBody) return false

        val body = gson.fromJson(data.operationBody, OperationBodyRequest::class.java)
        val id = body?.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull()?.value
            ?: return false
        val segmentationsResult = inAppSegmentationRepository.getProductSegmentations(id).flatMap {
            it?.productSegmentations?.firstOrNull()?.productList ?: emptyList()
        }
        if (segmentationsResult.isEmpty()) return false
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
