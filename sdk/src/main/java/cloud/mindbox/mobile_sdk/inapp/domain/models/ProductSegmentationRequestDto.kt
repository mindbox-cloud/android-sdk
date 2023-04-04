package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName


internal data class ProductSegmentationRequestDto(
    @SerializedName("products")
    val products: List<ProductRequestDto>,
    @SerializedName("segmentations")
    val segmentations: List<SegmentationRequestDto>,
)

internal data class SegmentationRequestDto(
    @SerializedName("ids")
    val ids: SegmentationRequestIds,
)

internal data class SegmentationRequestIds(
    @SerializedName("externalId")
    val externalId: String
)

internal data class ProductRequestDto(
    @SerializedName("ids")
    val ids: Ids,
)