package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto.SnackbarDto.ContentDto.PositionDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto.SnackbarDto.ContentDto.PositionDto.GravityDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto.SnackbarDto.ContentDto.PositionDto.MarginDto

internal class SnackBarDtoDataFiller(private val elementDtoDataFiller: ElementDtoDataFiller) :
    DefaultDataFiller<PayloadDto.SnackbarDto?> {

    private val horizontalPositionNames = setOf("center", "left", "right")
    private val verticalPositionNames = setOf("center", "top", "bottom")
    private val marginNames = setOf("dp")
    private val defaultMarginKind = "dp"
    private val defaultHorizontalGravity = "center"
    private val defaultVerticalGravity = "bottom"
    private val defaultBottomMargin = 20.0
    private val defaultTopMargin = 20.0
    private val defaultLeftMargin = 20.0
    private val defaultRightMargin = 20.0
    override fun fillData(item: PayloadDto.SnackbarDto?): PayloadDto.SnackbarDto? {
        val newGravityDto = if (item?.content?.position?.gravity?.horizontal != null
            && item.content.position.gravity.vertical != null
            && horizontalPositionNames.contains(item.content.position.gravity.horizontal) && verticalPositionNames.contains(
                item.content.position.gravity.vertical
            )
        ) item.content.position.gravity else GravityDto(
            horizontal = if (horizontalPositionNames.contains(item?.content?.position?.gravity?.horizontal)) item?.content?.position?.gravity?.horizontal else defaultHorizontalGravity,
            vertical = if (verticalPositionNames.contains(item?.content?.position?.gravity?.vertical)) item?.content?.position?.gravity?.vertical else defaultVerticalGravity
        )
        val newMarginDto =
            if (item?.content?.position?.margin != null && marginNames.contains(item.content.position.margin.kind)) item.content.position.margin else MarginDto(
                bottom = defaultBottomMargin,
                kind = defaultMarginKind,
                left = defaultLeftMargin,
                right = defaultRightMargin,
                top = defaultTopMargin
            )
        return item?.copy(
            content = item.content?.copy(
                elements = elementDtoDataFiller.fillData(item.content.elements),
                position = PositionDto(
                    gravity = newGravityDto,
                    margin = newMarginDto
                ),
            ), type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME
        )
    }

}