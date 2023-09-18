package cloud.mindbox.mobile_sdk.inapp.data.dto

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName

internal data class FormBlankDto(
    @SerializedName("variants")
    val variants: List<PayloadBlankDto?>?,
)

internal sealed class PayloadBlankDto {
    data class SnackBarBlankDto(
        @SerializedName("content")
        val content: ContentBlankDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : PayloadBlankDto() {

        internal data class ContentBlankDto(
            @SerializedName("background")
            val background: BackGroundBlankDto?,
            @SerializedName("elements")
            var elements: List<ElementDto?>?,
            @SerializedName("position")
            val position: PositionBlankDto
        ) {
            internal data class PositionBlankDto(
                @SerializedName("gravity")
                val gravity: GravityBlankDto?,
                @SerializedName("margin")
                val margin: MarginBlankDto
            ) {
                internal data class MarginBlankDto(
                    @SerializedName("bottom")
                    val bottom: Double?,
                    @SerializedName("kind")
                    val kind: String?,
                    @SerializedName("left")
                    val left: Double?,
                    @SerializedName("right")
                    var right: Double?,
                    @SerializedName("top")
                    val top: Double?
                )
                internal data class GravityBlankDto(
                    @SerializedName("horizontal")
                    val horizontal: String?,
                    @SerializedName("vertical")
                    val vertical: String?
                )
            }

        }
    }
    data class ModalWindowBlankDto(
        @SerializedName("content")
        val content: ContentBlankDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : PayloadBlankDto() {
        internal data class ContentBlankDto(
            @SerializedName("background")
            val background: BackGroundBlankDto?,
            @SerializedName("elements")
            val elements: List<ElementDto?>?
        )
    }
}

internal data class BackGroundBlankDto(@SerializedName("layers") val layers: JsonArray?)