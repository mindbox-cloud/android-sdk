package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto

internal class DataManager(
    private val modalWindowDtoDataFiller: ModalWindowDtoDataFiller,
    private val snackBarDtoDataFiller: SnackBarDtoDataFiller,
    private val frequencyDataFiller: FrequencyDataFiller
){
    fun fillFormData(item: FormDto?): FormDto? {
        return item?.copy(variants = item.variants?.filterNotNull()?.map { payloadDto ->
            when (payloadDto) {
                is PayloadDto.ModalWindowDto -> {
                    modalWindowDtoDataFiller.fillData(payloadDto)
                }

                is PayloadDto.SnackbarDto -> {
                    snackBarDtoDataFiller.fillData(payloadDto)
                }
            }
        })
    }

    fun fillFrequencyData(item: FrequencyDto?): FrequencyDto {
        return frequencyDataFiller.fillData(item)
    }

}