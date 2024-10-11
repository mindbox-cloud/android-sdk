package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.isInRange
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class CloseButtonModalSizeValidator : Validator<ElementDto.CloseButtonElementDto.SizeDto?> {
    override fun isValid(item: ElementDto.CloseButtonElementDto.SizeDto?): Boolean {
        val rez = item?.kind != null &&
            item.height.isInRange(0.0, Double.MAX_VALUE) &&
            item.width.isInRange(0.0, Double.MAX_VALUE)
        if (!rez) {
            mindboxLogI(
                "Close button size is not valid. Expected kind != null and width/height in range [0, inf]. " +
                    "Actual params : kind =  ${item?.kind}, height = ${item?.height}, width = ${item?.width}"
            )
        }
        return rez
    }
}
