package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class LimitTypeResponse {

    @SerializedName("groupLimit")
    GROUP_LIMIT,

    @SerializedName("personalLimit")
    PERSONAL_LIMIT
}
