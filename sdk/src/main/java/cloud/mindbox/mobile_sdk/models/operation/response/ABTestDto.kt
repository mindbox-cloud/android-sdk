package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

internal data class ABTestDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("sdkVersion")
    val sdkVersion: SdkVersion?,
    @SerializedName("salt")
    val salt: String,
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
