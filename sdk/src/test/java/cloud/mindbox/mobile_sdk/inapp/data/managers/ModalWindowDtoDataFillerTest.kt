package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.ModalElementDtoDataFiller
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.ModalWindowDtoDataFiller
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class ModalWindowDtoDataFillerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var elementDtoDataFiller: ModalElementDtoDataFiller

    @OverrideMockKs
    private lateinit var modalWindowDtoDataFiller: ModalWindowDtoDataFiller

    @Test
    fun `fillData should correctly fill the data`() {
        // Given
        val item = PayloadDto.ModalWindowDto(
            content = InAppStub.getModalWindowContentDto().copy(elements = emptyList()),
            type = ""
        )

        every { elementDtoDataFiller.fillData(any()) } returns emptyList()

        // When
        val result = modalWindowDtoDataFiller.fillData(item)

        // Then
        assertEquals(emptyList<ElementDto>(), result?.content?.elements)
        assertEquals(PayloadDto.ModalWindowDto.MODAL_JSON_NAME, result?.type)
    }
}
