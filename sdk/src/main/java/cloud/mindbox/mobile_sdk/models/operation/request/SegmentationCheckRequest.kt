package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

internal data class SegmentationCheckRequest(
    @SerializedName("segmentations")
    val segmentations: List<SegmentationDataRequest>?
)

internal data class IdsRequest(
    @SerializedName("externalId")
    val externalId: String?
)

internal data class SegmentationDataRequest(
    @SerializedName("ids")
    val ids: IdsRequest?
)
