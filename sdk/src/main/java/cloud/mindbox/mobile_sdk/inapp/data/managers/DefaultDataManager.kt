package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto

internal class DefaultDataManager(
    private val modalWindowDtoDataFiller: ModalWindowDtoDataFiller,
    private val snackBarDtoDataFiller: SnackBarDtoDataFiller
) : DefaultDataFiller<FormDto?> {
    override fun fillData(item: FormDto?): FormDto? {
        return item?.copy(variants = item.variants?.filterNotNull()?.map { payloadDto ->
            when (payloadDto) {
                is PayloadDto.ModalWindowDto -> {
                    mindboxLogD("Start optionality check for $payloadDto")
                    val rez = modalWindowDtoDataFiller.fillData(payloadDto)
                    mindboxLogD("Finish optionality check for $payloadDto")
                    rez
                }
                is PayloadDto.SnackbarDto -> {
                    mindboxLogD("Start optionality check for $payloadDto")
                    val rez = snackBarDtoDataFiller.fillData(payloadDto)
                    mindboxLogD("Finish optionality check for $payloadDto")
                    rez
                }
            }
        })
    }


}