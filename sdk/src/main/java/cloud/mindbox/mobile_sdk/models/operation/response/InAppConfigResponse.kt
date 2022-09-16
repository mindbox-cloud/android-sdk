package cloud.mindbox.mobile_sdk.models.operation.response


import com.google.gson.annotations.SerializedName

data class InAppConfigResponse(
    @SerializedName("inapps")
    val inApps: List<InAppDto>?,
)

data class InAppDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("targeting")
    val targeting: TargetingDto?,
    @SerializedName("form")
    val form: FormDto?,
)

data class FormDto(
    @SerializedName("variants")
    val variants: List<PayloadDto>?,
)

data class TargetingDto(
    @SerializedName("${"$"}type")
    val type: String?,
    @SerializedName("segmentation")
    val segmentation: String?,
    @SerializedName("segment")
    val segment: String?
)

sealed class PayloadDto {
    class SimpleImage(
        @SerializedName("${"$"}type")
        val type: String?,
        @SerializedName("imageUrl")
        val imageUrl: String?,
        @SerializedName("redirectUrl")
        val redirectUrl: String?,
        @SerializedName("intentPayload")
        val intentPayload: String?,
    ) : PayloadDto()
}
