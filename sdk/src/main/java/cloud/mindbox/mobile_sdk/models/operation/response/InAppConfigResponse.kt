package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers.SlidingExpirationDtoBlankDeserializer
import cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers.InappSettingsDtoBlankDeserializer
import com.google.gson.annotations.JsonAdapter

internal data class InAppConfigResponse(
    @SerializedName("inapps")
    val inApps: List<InAppDto>?,
    @SerializedName("monitoring")
    val monitoring: List<LogRequestDto>?,
    @SerializedName("settings")
    val settings: SettingsDto?,
    @SerializedName("abtests")
    val abtests: List<ABTestDto>?,
)

internal data class SettingsDtoBlank(
    @SerializedName("operations")
    val operations: Map<String?, OperationDtoBlank?>?,
    @SerializedName("ttl")
    val ttl: TtlDtoBlank?,
    @SerializedName("slidingExpiration")
    val slidingExpiration: SlidingExpirationDtoBlank?,
    @SerializedName("inapp")
    val inappSettings: InappSettingsDtoBlank?
) {
    internal data class OperationDtoBlank(
        @SerializedName("systemName")
        val systemName: String
    )

    internal data class TtlDtoBlank(
        @SerializedName("inapps")
        val inApps: String
    )

    @JsonAdapter(SlidingExpirationDtoBlankDeserializer::class)
    internal data class SlidingExpirationDtoBlank(
        @SerializedName(SlidingExpirationDtoBlankDeserializer.CONFIG)
        val config: TimeSpan?,
        @SerializedName(SlidingExpirationDtoBlankDeserializer.PUSH_TOKEN_KEEP_ALIVE)
        val pushTokenKeepalive: TimeSpan?,
    )

    @JsonAdapter(InappSettingsDtoBlankDeserializer::class)
    internal data class InappSettingsDtoBlank(
        @SerializedName(InappSettingsDtoBlankDeserializer.MAX_INAPPS_PER_SESSION)
        val maxInappsPerSession: Int?,
        @SerializedName(InappSettingsDtoBlankDeserializer.MAX_INAPPS_PER_DAY)
        val maxInappsPerDay: Int?,
        @SerializedName(InappSettingsDtoBlankDeserializer.MIN_INTERVAL_BETWEEN_SHOWS)
        val minIntervalBetweenShows: TimeSpan?,
    )
}

internal data class SettingsDto(
    @SerializedName("operations")
    val operations: Map<String, OperationDto>?,
    @SerializedName("ttl")
    val ttl: TtlDto?,
    @SerializedName("slidingExpiration")
    val slidingExpiration: SlidingExpirationDto?,
    @SerializedName("inapp")
    val inapp: InappSettingsDto?
)

internal data class OperationDto(
    @SerializedName("systemName")
    val systemName: String
)

internal data class TtlDto(
    @SerializedName("inapps")
    val inApps: String
)

internal data class SlidingExpirationDto(
    val config: Milliseconds?,
    val pushTokenKeepalive: Milliseconds?,
)

internal data class InappSettingsDto(
    val maxInappsPerSession: Int?,
    val maxInappsPerDay: Int?,
    val minIntervalBetweenShows: Milliseconds?,
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
    @SerializedName("frequency")
    val frequency: FrequencyDto,
    @SerializedName("sdkVersion")
    val sdkVersion: SdkVersion?,
    @SerializedName("targeting")
    val targeting: TreeTargetingDto?,
    @SerializedName("form")
    val form: FormDto?,
)

internal sealed class FrequencyDto {
    internal data class FrequencyOnceDto(
        @SerializedName("${"$"}type")
        val type: String,
        @SerializedName("kind")
        val kind: String
    ) : FrequencyDto() {
        internal companion object {
            const val FREQUENCY_ONCE_JSON_NAME = "once"

            const val FREQUENCY_KIND_LIFETIME = "lifetime"
            const val FREQUENCY_KIND_SESSION = "session"
        }
    }

    internal data class FrequencyPeriodicDto(
        @SerializedName("${"$"}type")
        val type: String,
        @SerializedName("unit")
        val unit: String,
        @SerializedName("value")
        val value: Long
    ) : FrequencyDto() {
        internal companion object {
            const val FREQUENCY_PERIODIC_JSON_NAME = "periodic"

            const val FREQUENCY_UNIT_HOURS = "HOURS"
            const val FREQUENCY_UNIT_MINUTES = "MINUTES"
            const val FREQUENCY_UNIT_DAYS = "DAYS"
            const val FREQUENCY_UNIT_SECONDS = "SECONDS"
        }
    }
}

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

internal data class MonitoringDto(
    @SerializedName("logs")
    val logs: List<LogRequestDtoBlank>?,
)

internal data class LogRequestDtoBlank(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("deviceUUID")
    val deviceId: String,
    @SerializedName("from")
    val from: String,
    @SerializedName("to")
    val to: String,
)

internal data class InAppConfigResponseBlank(
    @SerializedName("inapps")
    val inApps: List<InAppDtoBlank>?,
    @SerializedName("monitoring")
    val monitoring: MonitoringDto?,
    @SerializedName("settings")
    val settings: SettingsDtoBlank?,
    @SerializedName("abtests")
    val abtests: List<ABTestDto>?,
) {

    internal data class InAppDtoBlank(
        @SerializedName("id")
        val id: String,
        @SerializedName("frequency")
        val frequency: JsonObject?,
        @SerializedName("sdkVersion")
        val sdkVersion: SdkVersion?,
        @SerializedName("targeting")
        val targeting: JsonObject?,
        // FormDto. Parsed after filtering inApp versions.
        @SerializedName("form")
        val form: JsonObject?,
    )
}
