package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import com.google.gson.annotations.SerializedName

/**
 * In-app types
 **/
internal sealed class PayloadDto {

    data class SnackbarDto(
        @SerializedName("content")
        val content: ContentDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : PayloadDto() {

        internal data class ContentDto(
            @SerializedName("background")
            val background: BackgroundDto?,
            @SerializedName("elements")
            var elements: List<ElementDto?>?,
            @SerializedName("position")
            val position: PositionDto
        ) {
            internal data class PositionDto(
                @SerializedName("gravity")
                val gravity: GravityDto?,
                @SerializedName("margin")
                val margin: MarginDto
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
                internal data class GravityDto(
                    @SerializedName("horizontal")
                    val horizontal: String?,
                    @SerializedName("vertical")
                    val vertical: String?
                )
            }

        }

        internal companion object {
            const val SNACKBAR_JSON_NAME = "snackbar"
        }
    }

    data class ModalWindowDto(
        @SerializedName("content")
        val content: ContentDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : PayloadDto() {

        internal companion object {
            const val MODAL_JSON_NAME = "modal"
        }

        internal data class ContentDto(
            @SerializedName("background")
            val background: BackgroundDto?,
            @SerializedName("elements")
            var elements: List<ElementDto?>?
        ) {
        }
    }
}