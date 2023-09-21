package cloud.mindbox.mobile_sdk.inapp.data.dto

import com.google.gson.annotations.SerializedName

internal sealed class ElementDto {

    internal data class CloseButtonElementDto(
        @SerializedName("color")
        val color: String?,
        @SerializedName("lineWidth")
        val lineWidth: Any?,
        @SerializedName("position")
        val position: PositionDto?,
        @SerializedName("size")
        val size: SizeDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : ElementDto() {

        internal companion object {
            const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
        }

        internal data class PositionDto(
            @SerializedName("margin")
            val margin: MarginDto?
        ) {
            internal data class MarginDto(
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
        }

        internal data class SizeDto(
            @SerializedName("height")
            val height: Double?,
            @SerializedName("kind")
            val kind: String?,
            @SerializedName("width")
            val width: Double?
        )
    }
}