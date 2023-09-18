package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ElementValidatorTest {
    private val elementValidator = ElementValidator()

    @Test
    fun `test isValid returns true for valid CloseButtonElementDto`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(
            color = "#000000",
            lineWidth = 1.0,
            position = InAppStub.getElementPositionDto().copy(
                margin = InAppStub.getElementMarginDto().copy(
                    bottom = 1.0, kind = "proportion", left = 1.0, right = 1.0, top = 1.0

                )

            ),
            size = InAppStub.getElementSizeDto().copy(
                height = 1.0, kind = "dp", width = 1.0

            ),
            type = ElementDto.CloseButtonElementDto.CLOSE_BUTTON_ELEMENT_JSON_NAME
        )

        assertTrue(elementValidator.isValid(closeButtonElementDto))
    }

    @Test
    fun `test isValid returns false for invalid type`() {
        val elementDto = InAppStub.getCloseButtonElementDto().copy(type = "invalid_type")
        assertFalse(elementValidator.isValid(elementDto))
    }

    @Test
    fun `test isValid returns false for missing color`() {
        val elementDto = InAppStub.getCloseButtonElementDto().copy(color = null)
        assertFalse(elementValidator.isValid(elementDto))
    }

    @Test
    fun `test isValid returns false for missing lineWidth`() {
        val elementDto = InAppStub.getCloseButtonElementDto().copy(lineWidth = null)
        assertFalse(elementValidator.isValid(elementDto))
    }

    @Test
    fun `test isValid returns false for invalid position kind`() {
        val elementDto = InAppStub.getCloseButtonElementDto().copy(
            position = InAppStub.getElementPositionDto().copy(
                margin = InAppStub.getElementMarginDto().copy(kind = "invalid_kind")
            )
        )
        assertFalse(elementValidator.isValid(elementDto))
    }

    @Test
    fun `test isValid returns false for invalid size kind`() {
        val elementDto = InAppStub.getCloseButtonElementDto().copy(
            size = InAppStub.getElementSizeDto().copy(kind = "invalid_kind")
        )
        assertFalse(elementValidator.isValid(elementDto))
    }
    @Test
    fun `test isValid returns false when closeButtonElementDto type is invalid`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(type = "invalid_type")
        assertFalse(elementValidator.isValid(closeButtonElementDto))
    }

    @Test
    fun `test isValid returns false when closeButtonElementDto has missing color`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(color = null)
        assertFalse(elementValidator.isValid(closeButtonElementDto))
    }

    @Test
    fun `test isValid returns false when closeButtonElementDto has missing lineWidth`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(lineWidth = null)
        assertFalse(elementValidator.isValid(closeButtonElementDto))
    }

    @Test
    fun `test isValid returns false when closeButtonElementDto has invalid position`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(
            position = InAppStub.getElementPositionDto().copy(
                margin = InAppStub.getElementMarginDto().copy(kind = "invalid_kind")
            )
        )
        assertFalse(elementValidator.isValid(closeButtonElementDto))
    }

    @Test
    fun `test isValid returns false when closeButtonElementDto has invalid size kind`() {
        val closeButtonElementDto = InAppStub.getCloseButtonElementDto().copy(
            size = InAppStub.getElementSizeDto().copy(kind = "invalid_kind")
        )
        assertFalse(elementValidator.isValid(closeButtonElementDto))
    }

}