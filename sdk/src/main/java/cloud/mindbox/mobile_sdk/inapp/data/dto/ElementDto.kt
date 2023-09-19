package cloud.mindbox.mobile_sdk.inapp.data.dto

import android.graphics.Color
import cloud.mindbox.mobile_sdk.isInRange
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import com.google.gson.annotations.SerializedName

internal sealed class ElementDto {
    internal abstract fun updateWithDefaults(type: InAppType): ElementDto
    internal abstract fun validateValues(): Boolean

    internal enum class InAppType {
        MODAL_WINDOW,
        SNACKBAR
    }

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

        override fun updateWithDefaults(type: InAppType): ElementDto {
            val newColor =
                if (color != null && runCatching { Color.parseColor(color) }.getOrNull() != null) {
                    color
                } else {
                    mindboxLogI("Color is not valid. Applying default")
                    defaultColor
                }
            val newLineWidth = if (lineWidth != null && lineWidth.toString()
                    .toDoubleOrNull() != null
            ) {
                lineWidth
            } else {
                mindboxLogI("Line width is not valid. Applying default")
                defaultLineWidth
            }

            val newPosition =
                if (position?.margin != null && marginNames.contains(position.margin.kind)) {
                    position
                } else {
                    mindboxLogI("Unknown position ${position?.margin?.kind}. Applying default")
                    PositionDto(
                        PositionDto.MarginDto(
                            bottom = defaultBottomPosition,
                            kind = defaultPositionKind,
                            left = defaultLeftPosition,
                            right = defaultRightPosition,
                            top = defaultTopPosition
                        )
                    )
                }
            val newSize = if (size != null && sizeNames.contains(size.kind)) {
                size
            } else {
                mindboxLogI("Unknown size ${size?.kind}. Applying default")
                when (type) {
                    InAppType.MODAL_WINDOW -> {
                        SizeDto(
                            height = defaultModalHeightSize,
                            kind = defaultSizeKind,
                            width = defaultModalWidthSize
                        )
                    }

                    InAppType.SNACKBAR -> {
                        SizeDto(
                            height = defaultSnackbarHeightSize,
                            kind = defaultSizeKind,
                            width = defaultSnackbarWidthSize
                        )
                    }
                }
            }
            return copy(
                color = newColor,
                lineWidth = newLineWidth,
                position = newPosition,
                size = newSize,
                type = CLOSE_BUTTON_ELEMENT_JSON_NAME
            )
        }

        override fun validateValues(): Boolean {
            return isValidSize(size) && isValidPosition(position)
        }

        private fun isValidSize(item: SizeDto?): Boolean {
            val rez = item?.kind != null
                    && item.height.isInRange(0.0, Double.MAX_VALUE)
                    && item.width.isInRange(0.0, Double.MAX_VALUE)
            if (!rez) {
                mindboxLogI(
                    "Close button size is not valid. Expected kind != null and width/height in range [0, inf]. " +
                            "Actual params : kind =  ${item?.kind}, height = ${item?.height}, width = ${item?.width}"
                )
            }
            return rez
        }

        private fun isValidPosition(item: PositionDto?): Boolean {
            val rez = item?.margin?.kind != null
                    && item.margin.bottom.isInRange(0.0, 1.0)
                    && item.margin.top.isInRange(0.0, 1.0)
                    && item.margin.left.isInRange(0.0, 1.0)
                    && item.margin.right.isInRange(0.0, 1.0)
            if (!rez) {
                mindboxLogI(
                    "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                            "Actual params : kind =  ${item?.margin?.kind}, top = ${item?.margin?.top}, bottom = ${item?.margin?.bottom}, left = ${item?.margin?.left}, right = ${item?.margin?.right}"
                )
            }
            return rez
        }

        internal companion object {
            const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
            private const val defaultColor = "#FFFFFF"
            private const val defaultLineWidth = 2
            private const val defaultModalWidthSize = 24.0
            private const val defaultModalHeightSize = 24.0
            private const val defaultSnackbarWidthSize = 16.0
            private const val defaultSnackbarHeightSize = 16.0
            private const val defaultSizeKind = "dp"
            private const val defaultBottomPosition = 0.02
            private const val defaultTopPosition = 0.02
            private const val defaultLeftPosition = 0.02
            private const val defaultRightPosition = 0.02
            private const val defaultPositionKind = "proportion"
            private val sizeNames = setOf("dp")
            private val marginNames = setOf("proportion")
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