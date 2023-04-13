package cloud.mindbox.mobile_sdk.inapp.domain.models


import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

internal data class ProductSegmentationResponseDto(
    @SerializedName("products")
    val products: List<ProductResponseDto?>?,
    @SerializedName("status")
    val status: String?,
)

internal data class ProductResponseDto(
    @SerializedName("ids")
    val ids: Ids?,
    @SerializedName("segmentations")
    val segmentations: List<SegmentationResponseDto?>?,
)

internal data class SegmentResponseDto(
    @SerializedName("ids")
    val ids: Ids?,
)

internal data class SegmentationResponseDto(
    @SerializedName("ids")
    val ids: Ids?,
    @SerializedName("segment")
    val segment: SegmentResponseDto?,
)