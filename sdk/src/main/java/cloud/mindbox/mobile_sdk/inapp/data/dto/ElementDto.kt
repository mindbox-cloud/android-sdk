package cloud.mindbox.mobile_sdk.inapp.data.dto

import cloud.mindbox.mobile_sdk.isInRange
import com.google.gson.annotations.SerializedName

internal sealed class ElementDto {

    internal abstract fun default(): ElementDto

    internal abstract fun validateValues(): Boolean

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


        override fun default(): ElementDto {
            return CloseButtonElementDto(
                color = "#000000",
                lineWidth = 1.0,
                position = PositionDto.default(),
                size = SizeDto.default(),
                type = "closeButton"

            )
        }

        override fun validateValues(): Boolean {
            return isValidSize(size) && isValidPosition(position)
        }

        private fun isValidSize(item: SizeDto?): Boolean {
            return item?.kind != null && item.height != null && item.width != null && item.height.isInRange(
                0.0,
                Double.MAX_VALUE
            )
                .not() && item.width.isInRange(0.0, Double.MAX_VALUE)
        }

        private fun isValidPosition(item: PositionDto?): Boolean {
            return item?.margin?.kind != null
                    && item.margin.bottom != null && item.margin.bottom.isInRange(
                0.0,
                1.0
            )
                    && item.margin.top != null && item.margin.top.isInRange(0.0, 1.0)
                    && item.margin.left != null && item.margin.left.isInRange(0.0, 1.0)
                    && item.margin.right != null && item.margin.right.isInRange(
                0.0,
                1.0
            )
        }

        internal companion object {
            const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
        }

        internal data class PositionDto(
            @SerializedName("margin")
            val margin: MarginDto?
        ) {
            internal companion object {
                internal fun default(): PositionDto {
                    return PositionDto(margin = MarginDto.default())
                }
            }


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
            ) {
                internal companion object {
                    internal fun default(): MarginDto {
                        return MarginDto(
                            bottom = 0.02,
                            kind = "proportion",
                            left = 0.02,
                            right = 0.02,
                            top = 0.02
                        )
                    }
                }

            }
        }

        internal data class SizeDto(
            @SerializedName("height")
            val height: Double?,
            @SerializedName("kind")
            val kind: String?,
            @SerializedName("width")
            val width: Double?
        ) {
            internal companion object {
                internal fun default(): SizeDto {
                    return SizeDto(height = 24.0, kind = "dp", width = 24.0)
                }
            }

        }
    }
}