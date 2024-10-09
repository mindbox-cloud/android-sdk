package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import android.graphics.Color
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class CloseButtonModalElementDtoDataFiller :
    DataFiller<ElementDto.CloseButtonElementDto?> {

    internal companion object {
        const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
        private const val DEFAULT_COLOR = "#FFFFFF"
        private const val DEFAULT_LINE_WIDTH = 2
        private const val DEFAULT_MODAL_WIDTH_SIZE = 24.0
        private const val DEFAULT_MODAL_HEIGHT_SIZE = 24.0
        private const val DEFAULT_SIZE_KIND = "dp"
        private const val DEFAULT_BOTTOM_POSITION = 0.02
        private const val DEFAULT_TOP_POSITION = 0.02
        private const val DEFAULT_LEFT_POSITION = 0.02
        private const val DEFAULT_RIGHT_POSITION = 0.02
        private const val DEFAULT_POSITION_KIND = "proportion"
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
                DEFAULT_COLOR
            }
        val newLineWidth = if (item.lineWidth != null && item.lineWidth.toString()
                .toDoubleOrNull() != null
        ) {
            item.lineWidth
        } else {
            mindboxLogI("Line width is not valid. Applying default")
            DEFAULT_LINE_WIDTH
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
                        bottom = DEFAULT_BOTTOM_POSITION,
                        kind = DEFAULT_POSITION_KIND,
                        left = DEFAULT_LEFT_POSITION,
                        right = DEFAULT_RIGHT_POSITION,
                        top = DEFAULT_TOP_POSITION
                    )
                )
            }
        val newSize =
            if (item.size != null && sizeNames.contains(item.size.kind)) {
                item.size
            } else {
                mindboxLogI("Unknown size ${item.size?.kind}. Applying default")

                ElementDto.CloseButtonElementDto.SizeDto(
                    height = DEFAULT_MODAL_HEIGHT_SIZE,
                    kind = DEFAULT_SIZE_KIND,
                    width = DEFAULT_MODAL_WIDTH_SIZE
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
