package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal interface ElementDataFiller<T> {

    fun fillData(item: T, type: ElementDto.InAppType): T
}