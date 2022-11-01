package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName

internal data class SegmentationCheckInApp(
    @SerializedName("status")
    val status: String,
    @SerializedName("customerSegmentations")
    val customerSegmentations: List<CustomerSegmentationInApp>,
)

internal data class CustomerSegmentationInApp(
    @SerializedName("segmentation")
    val segmentation: SegmentationInApp?,
    @SerializedName("segment")
    val segment: SegmentInApp?,
)

internal data class IdsInApp(
    @SerializedName("externalId")
    val externalId: String?,
)

internal data class SegmentationInApp(
    @SerializedName("ids")
    val ids: IdsInApp?,
)

internal data class SegmentInApp(
    @SerializedName("ids")
    val ids: IdsInApp?,
)