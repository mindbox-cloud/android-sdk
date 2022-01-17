package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class SegmentationResponse(
    @SerializedName("ids") val ids: Ids? = null
) {

    override fun toString() = "SegmentationResponse(ids=$ids)"

}
