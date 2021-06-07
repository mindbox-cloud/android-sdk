package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class PlaceholderResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("content") val content: List<ContentResponse>? = null
) {

    override fun toString() = "PlaceholderResponse(ids=$ids, content=$content)"

}
