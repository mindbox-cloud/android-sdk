package cloud.mindbox.mobile_sdk.inapp.domain.models

internal data class ProductSegmentationResponse(
    val segmentationExternalId: String,
    val segmentExternalId: String,
)

internal data class ProductResponse(
    val productList: List<ProductSegmentationResponse>,
)

internal data class ProductSegmentationResponseWrapper(
    val productSegmentations: List<ProductResponse>,
)