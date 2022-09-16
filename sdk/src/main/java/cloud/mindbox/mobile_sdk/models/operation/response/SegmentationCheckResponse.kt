package cloud.mindbox.mobile_sdk.models.operation.response


import com.google.gson.annotations.SerializedName

internal data class SegmentationCheckResponse(
    @SerializedName("status")
    val status: String?,
    @SerializedName("customerSegmentations")
    val customerSegmentations: List<CustomerSegmentationInAppResponse>?,
)

internal data class CustomerSegmentationInAppResponse(
    @SerializedName("segmentation")
    val segmentation: SegmentationInAppResponse?,
    @SerializedName("segment")
    val segment: SegmentInAppResponse?,
)

internal data class IdsResponse(
    @SerializedName("externalId")
    val externalId: String?,
)

internal data class SegmentationInAppResponse(
    @SerializedName("ids")
    val ids: IdsResponse?,
)

internal data class SegmentInAppResponse(
    @SerializedName("ids")
    val ids: IdsResponse?,
)