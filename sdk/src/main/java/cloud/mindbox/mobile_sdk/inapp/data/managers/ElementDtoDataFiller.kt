package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal class ElementDtoDataFiller : ElementDataFiller<List<ElementDto?>?> {
    override fun fillData(
        item: List<ElementDto?>?,
        type: ElementDto.InAppType
    ): List<ElementDto?>? {
        mindboxLogD("Start checking elements")
        return item?.map {
            mindboxLogD("Checking element = $it")
            it?.updateWithDefaults(type)
        }
    }
}