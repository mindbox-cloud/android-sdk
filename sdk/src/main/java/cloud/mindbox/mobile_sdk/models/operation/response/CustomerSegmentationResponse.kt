package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class CustomerSegmentationResponse(
    @SerializedName("segmentation") public val segmentation: SegmentationResponse? = null,
    @SerializedName("segment") public val segment: SegmentResponse? = null
) {

    override fun toString(): String = "CustomerSegmentationResponse(segmentation=$segmentation, " +
        "segment=$segment)"
}
