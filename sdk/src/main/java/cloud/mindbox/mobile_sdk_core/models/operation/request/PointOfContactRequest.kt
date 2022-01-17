package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

enum class PointOfContactRequest {

    @SerializedName("Email") EMAIL,
    @SerializedName("Sms") SMS,
    @SerializedName("Viber") VIBER,
    @SerializedName("Webpush") WEBPUSH,
    @SerializedName("Mobilepush") MOBILEPUSH

}
