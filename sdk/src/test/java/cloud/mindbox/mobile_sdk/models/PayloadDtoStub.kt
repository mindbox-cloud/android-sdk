package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto

internal class PayloadDtoStub {

    companion object {
        fun getSnackbarMarginDto() = PayloadDto.SnackbarDto.ContentDto.PositionDto.MarginDto(
            bottom = null,
            kind = null,
            left = null,
            right = null,
            top = null
        )

        fun getSnackbarGravityDto() = PayloadDto.SnackbarDto.ContentDto.PositionDto.GravityDto(
            horizontal = null,
            vertical = null
        )

        fun getSnackbarPositionDto() = PayloadDto.SnackbarDto.ContentDto.PositionDto(
            gravity = getSnackbarGravityDto(),
            margin = PayloadDto.SnackbarDto.ContentDto.PositionDto.MarginDto(
                bottom = null,
                kind = null,
                left = null,
                right = null,
                top = null
            )
        )
    }
}
