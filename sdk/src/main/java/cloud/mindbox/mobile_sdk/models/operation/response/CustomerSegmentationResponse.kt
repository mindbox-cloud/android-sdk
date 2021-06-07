package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class CustomerSegmentationResponse(
    @SerializedName("segmentation") val segmentation: SegmentationResponse? = null,
    @SerializedName("segment") val segment: SegmentResponse? = null
) {

    override fun toString() = "CustomerSegmentationResponse(segmentation=$segmentation, " +
            "segment=$segment)"

}
