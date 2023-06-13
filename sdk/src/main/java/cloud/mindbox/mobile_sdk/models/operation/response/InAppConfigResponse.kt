package cloud.mindbox.mobile_sdk.models.operation.response


import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

internal data class InAppConfigResponse(
    @SerializedName("inapps")
    val inApps: List<InAppDto>?,
    @SerializedName("monitoring")
    val monitoring: List<LogRequestDto>?,
    @SerializedName("settings")
    val settings: Map<String, OperationDto>?,
    @SerializedName("abtests")
    val abtests: List<ABTestDto>?,
)

internal data class SettingsDto(
    @SerializedName("operations")
    val operations: Map<String?, OperationDtoBlank?>?
) {
    internal data class OperationDtoBlank(
        @SerializedName("systemName")
        val systemName: String?
    )
}

internal data class ABTestDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("sdkVersion")
    val sdkVersion: SdkVersion?,
    @SerializedName("salt")
    val salt: String?,
    @SerializedName("variants")
    val variants: List<VariantDto>?,
) {
    internal data class VariantDto(
        @SerializedName("id")
        val id: String,
        @SerializedName("modulus")
        val modulus: ModulusDto?,
        @SerializedName("objects")
        val objects: List<ObjectsDto>?,
    ) {
        internal data class ModulusDto(
            @SerializedName("lower")
            val lower: Int?,
            @SerializedName("upper")
            val upper: Int?,
        )
        internal data class ObjectsDto(
            @SerializedName("${"$"}type")
            val type: String?,
            @SerializedName("kind")
            val kind: String?,
            @SerializedName("inapps")
            val inapps: List<String>?,
        )
    }
}

internal data class OperationDto(
    @SerializedName("systemName")
    val systemName: String
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

/**
 * In-app types
 **/
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
    ) : PayloadDto() {
        companion object {
            const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"
        }
    }
}

internal data class MonitoringDto(
    @SerializedName("logs")
    val logs: List<LogRequestDtoBlank>?,
)

internal data class LogRequestDtoBlank(
    @SerializedName("requestId")
    val requestId: String?,
    @SerializedName("deviceUUID")
    val deviceId: String?,
    @SerializedName("from")
    val from: String?,
    @SerializedName("to")
    val to: String?,
)

internal data class InAppConfigResponseBlank(
    @SerializedName("inapps")
    val inApps: List<InAppDtoBlank>?,
    @SerializedName("monitoring")
    val monitoring: MonitoringDto?,
    @SerializedName("settings")
    val settings: SettingsDto?,
    @SerializedName("abtests")
    val abtests: List<ABTestDto>?,
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