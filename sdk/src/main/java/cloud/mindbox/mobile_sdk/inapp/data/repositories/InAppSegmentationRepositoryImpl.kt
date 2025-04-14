package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.flow.first

internal class InAppSegmentationRepositoryImpl(
    private val inAppMapper: InAppMapper,
    private val sessionStorageManager: SessionStorageManager,
    private val gatewayManager: GatewayManager,
) : InAppSegmentationRepository {

    override suspend fun fetchCustomerSegmentations() {
        if (sessionStorageManager.currentSessionInApps.isEmpty()) {
            MindboxLoggerImpl.d(
                this,
                "No unshown inapps. Do not request segmentations"
            )
            sessionStorageManager.customerSegmentationFetchStatus =
                CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            return
        }
        MindboxLoggerImpl.d(
            this,
            "Request segmentations"
        )
        val configuration = DbManager.listenConfigurations().first()
        val response = gatewayManager.checkCustomerSegmentations(
            configuration = configuration,
            segmentationCheckRequest = inAppMapper.mapToCustomerSegmentationCheckRequest(
                sessionStorageManager.currentSessionInApps
            )
        )
        sessionStorageManager.inAppCustomerSegmentations =
            inAppMapper.mapToSegmentationCheck(response)
        sessionStorageManager.customerSegmentationFetchStatus =
            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
    }

    override suspend fun fetchProductSegmentation(
        product: Pair<String, String>,
    ) {
        val configuration = DbManager.listenConfigurations().first()
        val segmentationCheckRequest =
            inAppMapper.mapToProductSegmentationCheckRequest(
                product,
                sessionStorageManager.currentSessionInApps
            )
        val result = gatewayManager.checkProductSegmentation(
            configuration,
            segmentationCheckRequest
        )
        sessionStorageManager.inAppProductSegmentations[product] =
            sessionStorageManager.inAppProductSegmentations.getOrElse(product) {
                mutableSetOf<ProductSegmentationResponseWrapper>().apply {
                    add(
                        inAppMapper.mapToProductSegmentationResponse(
                            result
                        )
                    )
                }
            }
        sessionStorageManager.processedProductSegmentations[product] =
            ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
    }

    override fun getProductSegmentations(
        productId: Pair<String, String>,
    ): Set<ProductSegmentationResponseWrapper?> {
        return LoggingExceptionHandler.runCatching(emptySet()) {
            sessionStorageManager.inAppProductSegmentations[productId] ?: emptySet()
        }
    }

    override fun setCustomerSegmentationStatus(status: CustomerSegmentationFetchStatus) {
        sessionStorageManager.customerSegmentationFetchStatus = status
    }

    override fun getCustomerSegmentationFetched(): CustomerSegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionStorageManager.customerSegmentationFetchStatus
        }
    }

    override fun getProductSegmentationFetched(productId: Pair<String, String>): ProductSegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionStorageManager.processedProductSegmentations[productId] ?: ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        }
    }

    override fun getCustomerSegmentations(): List<CustomerSegmentationInApp> {
        return LoggingExceptionHandler.runCatching(emptyList()) {
            sessionStorageManager.inAppCustomerSegmentations?.customerSegmentations ?: emptyList()
        }
    }
}
