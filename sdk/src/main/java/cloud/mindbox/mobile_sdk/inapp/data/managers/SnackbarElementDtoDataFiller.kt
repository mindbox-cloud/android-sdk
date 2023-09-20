package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class SnackbarElementDtoDataFiller(private val closeButtonSnackbarElementDtoDataFiller: CloseButtonSnackbarElementDtoDataFiller): DataFiller<List<ElementDto?>?> {
    override fun fillData(item: List<ElementDto?>?): List<ElementDto?>? {
        return item?.map { elementDto ->
            when (elementDto) {
                is ElementDto.CloseButtonElementDto -> {
                    closeButtonSnackbarElementDtoDataFiller.fillData(elementDto)
                }

                null -> null
            }
        }
    }
}