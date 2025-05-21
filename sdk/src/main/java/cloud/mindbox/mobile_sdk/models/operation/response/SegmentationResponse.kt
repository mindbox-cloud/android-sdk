package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class SegmentationResponse(
    @SerializedName("ids") public val ids: Ids? = null
) {
    override fun toString(): String = "SegmentationResponse(ids=$ids)"
}
