package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class ElementDtoDataFiller : ElementDataFiller<List<ElementDto?>?> {
    override fun fillData(
        item: List<ElementDto?>?,
        type: ElementDto.InAppType
    ): List<ElementDto?>? {
        return item?.map {
            it?.updateWithDefaults(type)
        }
    }
}