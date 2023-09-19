package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto

internal class ModalWindowDtoDataFiller(private val elementDtoDataFiller: ElementDtoDataFiller) :
    DefaultDataFiller<PayloadDto.ModalWindowDto?> {

    override fun fillData(item: PayloadDto.ModalWindowDto?): PayloadDto.ModalWindowDto? {
        return item?.copy(
            content = item.content?.copy(elements = elementDtoDataFiller.fillData(item.content.elements, ElementDto.InAppType.MODAL_WINDOW)),
            type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME
        )
    }
}