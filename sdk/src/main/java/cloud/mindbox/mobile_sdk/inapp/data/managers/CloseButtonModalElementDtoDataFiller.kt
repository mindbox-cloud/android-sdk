package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.graphics.Color
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class CloseButtonModalElementDtoDataFiller :
    DataFiller<ElementDto.CloseButtonElementDto?> {

    internal companion object {
        const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
        private const val defaultColor = "#FFFFFF"
        private const val defaultLineWidth = 2
        private const val defaultModalWidthSize = 24.0
        private const val defaultModalHeightSize = 24.0
        private const val defaultSizeKind = "dp"
        private const val defaultBottomPosition = 0.02
        private const val defaultTopPosition = 0.02
        private const val defaultLeftPosition = 0.02
        private const val defaultRightPosition = 0.02
        private const val defaultPositionKind = "proportion"
        private val sizeNames = setOf("dp")
        private val marginNames = setOf("proportion")
    }

    override fun fillData(item: ElementDto.CloseButtonElementDto?): ElementDto.CloseButtonElementDto? {
        if (item == null) return null
        val newColor =
            if (item.color != null && runCatching { Color.parseColor(item.color) }.getOrNull() != null) {
                item.color
            } else {
                mindboxLogI("Color is not valid. Applying default")
                defaultColor
            }
        val newLineWidth = if (item.lineWidth != null && item.lineWidth.toString()
                .toDoubleOrNull() != null
        ) {
            item.lineWidth
        } else {
            mindboxLogI("Line width is not valid. Applying default")
            defaultLineWidth
        }
        val newPosition =
            if (item.position?.margin != null && marginNames.contains(
                    item.position.margin.kind
                )
            ) {
                item.position
            } else {
                mindboxLogI("Unknown position ${item.position?.margin?.kind}. Applying default")
                ElementDto.CloseButtonElementDto.PositionDto(
                    ElementDto.CloseButtonElementDto.PositionDto.MarginDto(
                        bottom = defaultBottomPosition,
                        kind = defaultPositionKind,
                        left = defaultLeftPosition,
                        right = defaultRightPosition,
                        top = defaultTopPosition
                    )
                )
            }
        val newSize =
            if (item.size != null && sizeNames.contains(item.size.kind)) {
                item.size
            } else {
                mindboxLogI("Unknown size ${item.size?.kind}. Applying default")

                ElementDto.CloseButtonElementDto.SizeDto(
                    height = defaultModalHeightSize,
                    kind = defaultSizeKind,
                    width = defaultModalWidthSize
                )
            }
        return item.copy(
            color = newColor,
            lineWidth = newLineWidth,
            position = newPosition,
            size = newSize,
            type = CLOSE_BUTTON_ELEMENT_JSON_NAME
        )
    }
}