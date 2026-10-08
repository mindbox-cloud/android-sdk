package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationError
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.Constants
import com.android.volley.TimeoutError
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InAppSegmentationRepositoryImpl(
    private val inAppMapper: InAppMapper,
    private val sessionStorageManager: SessionStorageManager,
    private val gatewayManager: GatewayManager,
) : InAppSegmentationRepository {

    private val customerSegmentationsMutex = Mutex()

    override suspend fun fetchCustomerSegmentations() = customerSegmentationsMutex.withLock {
        val state = sessionStorageManager.state
        if (state.customerSegmentationFetchStatus != CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED) {
            return@withLock
        }
        if (state.currentSessionInApps.isEmpty()) {
            MindboxLoggerImpl.d(
                this,
                "No unshown inapps. Do not request segmentations"
            )
            state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            return@withLock
        }
        MindboxLoggerImpl.d(
            this,
            "Request segmentations"
        )
        val configuration = DbManager.listenConfigurations().first()
        val response = try {
            withTimeoutOrNull(Constants.targetingFetchWaitLimit.interval) {
                gatewayManager.checkCustomerSegmentations(
                    configuration = configuration,
                    segmentationCheckRequest = inAppMapper.mapToCustomerSegmentationCheckRequest(state.currentSessionInApps)
                )
            } ?: throw CustomerSegmentationError(TimeoutError())
        } catch (error: CustomerSegmentationError) {
            state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            throw error
        }
        state.inAppCustomerSegmentations = inAppMapper.mapToSegmentationCheck(response)
        state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        return@withLock
    }

    override suspend fun fetchProductSegmentation(
        product: Pair<String, String>,
    ) {
        val configuration = DbManager.listenConfigurations().first()
        val segmentationCheckRequest =
            inAppMapper.mapToProductSegmentationCheckRequest(
                product,
                sessionStorageManager.state.currentSessionInApps
            )
        val result = gatewayManager.checkProductSegmentation(
            configuration,
            segmentationCheckRequest
        )
        sessionStorageManager.state.inAppProductSegmentations[product] =
            sessionStorageManager.state.inAppProductSegmentations.getOrElse(product) {
                mutableSetOf<ProductSegmentationResponseWrapper>().apply {
                    add(
                        inAppMapper.mapToProductSegmentationResponse(
                            result
                        )
                    )
                }
            }
        sessionStorageManager.state.processedProductSegmentations[product] =
            ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
    }

    override fun getProductSegmentations(
        productId: Pair<String, String>,
    ): Set<ProductSegmentationResponseWrapper?> {
        return LoggingExceptionHandler.runCatching(emptySet()) {
            sessionStorageManager.state.inAppProductSegmentations[productId] ?: emptySet()
        }
    }

    override fun setCustomerSegmentationStatus(status: CustomerSegmentationFetchStatus) {
        sessionStorageManager.state.customerSegmentationFetchStatus = status
    }

    override fun getCustomerSegmentationFetched(): CustomerSegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionStorageManager.state.customerSegmentationFetchStatus
        }
    }

    override fun getProductSegmentationFetched(productId: Pair<String, String>): ProductSegmentationFetchStatus {
        return LoggingExceptionHandler.runCatching(ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR) {
            sessionStorageManager.state.processedProductSegmentations[productId] ?: ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        }
    }

    override fun getCustomerSegmentations(): List<CustomerSegmentationInApp> {
        return LoggingExceptionHandler.runCatching(emptyList()) {
            sessionStorageManager.state.inAppCustomerSegmentations?.customerSegmentations ?: emptyList()
        }
    }
}
