package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class PointOfContactResponse {

    @SerializedName("Email") EMAIL,
    @SerializedName("Sms") SMS,
    @SerializedName("Viber") VIBER,
    @SerializedName("Webpush") WEBPUSH,
    @SerializedName("Mobilepush") MOBILEPUSH

}
