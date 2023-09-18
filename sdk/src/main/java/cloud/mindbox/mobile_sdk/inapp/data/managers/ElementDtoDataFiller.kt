package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class ElementDtoDataFiller: DefaultDataFiller<List<ElementDto?>?> {
    override fun fillData(item: List<ElementDto?>?): List<ElementDto?>? {
        return item?.map {
            it?.updateWithDefaults()
        }
    }
}