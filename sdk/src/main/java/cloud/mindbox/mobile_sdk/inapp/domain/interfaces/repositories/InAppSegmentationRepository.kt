package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper

internal interface InAppSegmentationRepository {

    suspend fun fetchCustomerSegmentations()

    suspend fun fetchProductSegmentation(product: Pair<String, String>)

    fun getProductSegmentations(productId: Pair<String, String>): Set<ProductSegmentationResponseWrapper?>

    fun setCustomerSegmentationStatus(status: CustomerSegmentationFetchStatus)

    fun getCustomerSegmentationFetched(): CustomerSegmentationFetchStatus

    fun getProductSegmentationFetched(productId: Pair<String, String>): ProductSegmentationFetchStatus

    fun getCustomerSegmentations(): List<CustomerSegmentationInApp>

    fun setLastCustomerSegmentationError(errorDetails: String)

    fun getLastCustomerSegmentationError(): String?

    fun setLastProductSegmentationError(product: Pair<String, String>, errorDetails: String)

    fun getLastProductSegmentationError(product: Pair<String, String>): String?
}
