package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto

internal class ModalWindowDtoDataFiller(private val elementDtoDataFiller: ModalElementDtoDataFiller) : DataFiller<PayloadDto.ModalWindowDto?> {

    override fun fillData(item: PayloadDto.ModalWindowDto?): PayloadDto.ModalWindowDto? = item?.copy(
        content = item.content?.copy(elements = elementDtoDataFiller.fillData(item.content.elements)),
        type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME
    )
}
