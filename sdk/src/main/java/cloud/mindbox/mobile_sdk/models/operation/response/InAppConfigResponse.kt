package cloud.mindbox.mobile_sdk.models.operation.response


import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

internal data class InAppConfigResponse(
    @SerializedName("inapps")
    val inApps: List<InAppDto>?,
    @SerializedName("logs")
    val monitoring: List<LogRequestDto>?,
)

internal data class LogRequestDto(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("deviceUUID")
    val deviceId: String,
    @SerializedName("from")
    val from: String,
    @SerializedName("to")
    val to: String,
)

internal data class InAppDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("sdkVersion")
    val sdkVersion: SdkVersion?,
    @SerializedName("targeting")
    val targeting: TreeTargetingDto?,
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
    val variants: List<PayloadDto?>?,
)

internal sealed class PayloadDto {
    data class SimpleImage(
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
    @SerializedName("logs")
    val monitoring: List<LogRequestDto>?,
) {

    internal data class InAppDtoBlank(
        @SerializedName("id")
        val id: String,
        @SerializedName("sdkVersion")
        val sdkVersion: SdkVersion?,
        @SerializedName("targeting")
        val targeting: JsonObject?,
        @SerializedName("form")
        val form: JsonObject?, // FormDto. Parsed after filtering inApp versions.
    )
}
