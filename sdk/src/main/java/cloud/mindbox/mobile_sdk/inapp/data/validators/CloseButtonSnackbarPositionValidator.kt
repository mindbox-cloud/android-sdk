package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.isInRange
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class CloseButtonSnackbarPositionValidator: Validator<ElementDto.CloseButtonElementDto.PositionDto?> {
    override fun isValid(item: ElementDto.CloseButtonElementDto.PositionDto?): Boolean {
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

}