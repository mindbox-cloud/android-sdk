package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class ModalElementDtoDataFiller(private val closeButtonModalElementDtoDataFiller: CloseButtonModalElementDtoDataFiller) :
    DataFiller<List<ElementDto?>?> {
    override fun fillData(item: List<ElementDto?>?): List<ElementDto?>? {
        return item?.map { elementDto ->
            when (elementDto) {
                is ElementDto.CloseButtonElementDto -> {
                    closeButtonModalElementDtoDataFiller.fillData(elementDto)
                }

                null -> null
            }
        }
    }
}