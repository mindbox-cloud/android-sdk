package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.CloseButtonModalElementDtoDataFiller
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CloseButtonModalElementDtoDataFillerTest {

    private val dataFiller = CloseButtonModalElementDtoDataFiller()

    @Test
    fun `fillData with valid item`() {
        val item = InAppStub.getCloseButtonElementDto().copy(
            color = "#FF0000",
            lineWidth = 3,
            position = ElementDto.CloseButtonElementDto.PositionDto(
                margin = ElementDto.CloseButtonElementDto.PositionDto.MarginDto(
                    bottom = 0.1,
                    kind = "proportion",
                    left = 0.1,
                    right = 0.1,
                    top = 0.1
                )
            ),
            size = InAppStub.getElementSizeDto().copy(
                height = 20.0,
                kind = "dp",
                width = 20.0
            )
        )

        val result = dataFiller.fillData(item)

        assertEquals("#FF0000", result?.color)
        assertEquals(3, result?.lineWidth)
        assertEquals(item.position, result?.position)
        assertEquals(item.size, result?.size)
        assertEquals("closeButton", result?.type)
    }

    @Test
    fun `fillData with null item`() {
        val result = dataFiller.fillData(null)

        assertEquals(null, result)
    }

    @Test
    fun `fillData with invalid color`() {
        val item = InAppStub.getCloseButtonElementDto().copy(
            color = "invalid_color",
            lineWidth = 3
        )

        val result = dataFiller.fillData(item)

        assertEquals("#FFFFFF", result?.color)
        assertEquals(3, result?.lineWidth)
    }

    @Test
    fun `fillData with invalid line width`() {
        val item = InAppStub.getCloseButtonElementDto().copy(
            color = "#FF0000",
            lineWidth = "invalid_width"
        )

        val result = dataFiller.fillData(item)

        assertEquals("#FF0000", result?.color)
        assertEquals(2, result?.lineWidth) // Default line width applied
    }

    @Test
    fun `fillData with unknown position kind`() {
        val item = InAppStub.getCloseButtonElementDto().copy(
            color = "#FF0000",
            lineWidth = 3,
            position = InAppStub.getElementPositionDto().copy(
                margin = InAppStub.getElementMarginDto().copy(
                    bottom = 0.1,
                    kind = "unknown_position_kind",
                    left = 0.1,
                    right = 0.1,
                    top = 0.1
                )
            )
        )
        val expectedResult = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = 0.02,
                kind = "proportion",
                left = 0.02,
                right = 0.02,
                top = 0.02
            )
        )
        val result = dataFiller.fillData(item)

        assertEquals("#FF0000", result?.color)
        assertEquals(3, result?.lineWidth)
        // Default position applied because of unknown position kind
        assertEquals(
            expectedResult,
            result?.position
        )
    }

    @Test
    fun `fillData with unknown size kind`() {
        val item = InAppStub.getCloseButtonElementDto().copy(
            color = "#FF0000",
            lineWidth = 3,
            size = InAppStub.getElementSizeDto().copy(
                height = 24.0,
                kind = "unknown_size_kind",
                width = 20.0
            )
        )

        val result = dataFiller.fillData(item)

        val expectedResult = InAppStub.getElementSizeDto().copy(
            height = 24.0, kind = "dp", width = 24.0
        )

        assertEquals("#FF0000", result?.color)
        assertEquals(3, result?.lineWidth)
        // Default size applied because of unknown size kind
        assertEquals(
            expectedResult,
            result?.size
        )
    }
}
