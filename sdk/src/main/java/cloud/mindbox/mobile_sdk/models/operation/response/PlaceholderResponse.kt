package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class PlaceholderResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("content") public val content: List<ContentResponse>? = null
) {

    override fun toString(): String = "PlaceholderResponse(ids=$ids, content=$content)"
}
