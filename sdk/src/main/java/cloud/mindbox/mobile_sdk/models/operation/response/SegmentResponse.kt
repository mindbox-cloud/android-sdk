package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class SegmentResponse(
    @SerializedName("ids") val ids: Ids? = null
) {

    override fun toString() = "SegmentResponse(ids=$ids)"
}
