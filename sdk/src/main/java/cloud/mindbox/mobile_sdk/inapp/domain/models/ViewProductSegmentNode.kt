package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest

internal data class ViewProductSegmentNode(
    override val type: String,
    val kind: Kind,
    val segmentationExternalId: String,
    val segmentExternalId: String,
) : OperationNodeBase(type) {

    private val mobileConfigRepository by mindboxInject { mobileConfigRepository }
    private val inAppSegmentationRepository by mindboxInject { inAppSegmentationRepository }
    private val gson by mindboxInject { gson }
    private val sessionStorageManager by mindboxInject { sessionStorageManager }

    override suspend fun fetchTargetingInfo(data: TargetingData) {
        if (data !is TargetingData.OperationBody) return
        val body = gson.fromJson(data.operationBody, OperationBodyRequest::class.java)
        body?.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull()?.also { entry ->
            if (entry.value.isNullOrBlank()) return
            val productId = "${entry.key}:${entry.value!!}"
            if (inAppSegmentationRepository.getProductSegmentationFetched(productId) != ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS) {
                runCatching {
                    inAppSegmentationRepository.fetchProductSegmentation(
                        entry.key to entry.value!!
                    )
                }.onFailure { error ->
                    if (error is ProductSegmentationError) {
                        sessionStorageManager.processedProductSegmentations[productId] =
                            ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                        mindboxLogE("Error fetching product segmentations for product $productId")
                    }
                }
            }
        } ?: return
    }

    override fun checkTargeting(data: TargetingData): Boolean {
        if (data !is TargetingData.OperationBody) return false

        val body = gson.fromJson(data.operationBody, OperationBodyRequest::class.java)
        val entry = body?.viewProductRequest?.product?.ids?.ids?.entries?.firstOrNull() ?: return false
        val productId = "${entry.key}:${entry.value}"
        val segmentationsResult = inAppSegmentationRepository.getProductSegmentations(productId).flatMap {
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
