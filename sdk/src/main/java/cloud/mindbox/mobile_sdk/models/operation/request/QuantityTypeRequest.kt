package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public enum class QuantityTypeRequest {

    @SerializedName("int")
    INT,

    @SerializedName("double")
    DOUBLE
}
