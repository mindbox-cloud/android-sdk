package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class ContentTypeResponse {

    @SerializedName("possibleDiscounts")
    POSSIBLE_DISCOUNTS,

    @SerializedName("text")
    TEXT
}
