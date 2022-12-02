package cloud.mindbox.mobile_sdk.models.operation.response


import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

internal data class InAppConfigResponse(
    @SerializedName("inapps")
    val inApps: List<InAppDto>?,
)

internal data class InAppDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("sdkVersion")
    val sdkVersion: SdkVersion?,
    @SerializedName("targeting")
    val targeting: TargetingDto?,
    @SerializedName("form")
    val form: FormDto?,
)

internal data class SdkVersion(
    @SerializedName("min")
    val minVersion: Int?,
    @SerializedName("max")
    val maxVersion: Int?,
)

internal data class FormDto(
    @SerializedName("variants")
    val variants: List<PayloadDto>?,
)

internal data class TargetingDto(
    @SerializedName("${"$"}type")
    val type: String?,
    @SerializedName("segmentation")
    val segmentation: String?,
    @SerializedName("segment")
    val segment: String?,
)

internal sealed class PayloadDto {
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

internal data class InAppConfigResponseBlank(
    @SerializedName("inapps")
    val inApps: List<InAppDtoBlank>?,
) {
    internal data class InAppDtoBlank(
        @SerializedName("id")
        val id: String,
        @SerializedName("sdkVersion")
        val sdkVersion: SdkVersion?,
        @SerializedName("targeting")
        val targeting: TargetingDto?,
        @SerializedName("form")
        val form: JsonObject?, // FormDto. Parsed after filtering inApp versions.
    )
}
