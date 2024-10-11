package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class PointOfContactResponse {

    @SerializedName("Email", alternate = ["EMAIL", "email"])
    EMAIL,

    @SerializedName("Sms", alternate = ["SMS", "sms"])
    SMS,

    @SerializedName("Viber", alternate = ["VIBER", "viber"])
    VIBER,

    @SerializedName("Webpush", alternate = ["WebPush"])
    WEBPUSH,

    @SerializedName("Mobilepush", alternate = ["MobilePush"])
    MOBILEPUSH
}
