package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class ModalElementDtoDataFillerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var closeButtonModalElementDtoDataFiller: CloseButtonModalElementDtoDataFiller

    @OverrideMockKs
    private lateinit var modalElementDtoDataFiller: ModalElementDtoDataFiller

    @Test
    fun `fillData should return mapped elements`() {
        // Arrange
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto()
        val inputList = listOf(closeButtonElementDto)
        val expectedOutputList = listOf(closeButtonElementDto)

        every {
            closeButtonModalElementDtoDataFiller.fillData(closeButtonElementDto)
        } returns closeButtonElementDto

        // Act
        val result = modalElementDtoDataFiller.fillData(inputList)

        // Assert
        assertEquals(expectedOutputList, result)
    }

    @Test
    fun `fillData should return null for null input`() {
        // Arrange
        val inputList: List<ElementDto?>? = null

        // Act
        val result = modalElementDtoDataFiller.fillData(inputList)

        // Assert
        assertEquals(null, result)
    }

    @Test
    fun `fillData should handle null element in the list`() {
        // Arrange
        val inputList = listOf<ElementDto?>(null)

        // Act
        val result = modalElementDtoDataFiller.fillData(inputList)

        // Assert
        assertEquals(listOf(null), result)
    }
}