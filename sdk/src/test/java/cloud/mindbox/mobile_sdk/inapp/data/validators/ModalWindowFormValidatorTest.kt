package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ModalWindowFormValidatorTest {


    private val modalWindowFormValidator = ModalWindowFormValidator()

    @Test
    fun `test isValid returns true when all conditions are met`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(InAppStub.getImageLayerDto())
                ),
                elements = listOf(InAppStub.getCloseButtonElementDto())
            )
        )

        assertTrue(modalWindowFormValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when type is not correct`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(type = "invalid_type")

        assertFalse(modalWindowFormValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when background layers are empty`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(layers = emptyList())
            )
        )

        assertFalse(modalWindowFormValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when background is null`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getContentDto().copy(
                background = null
            )
        )
        assertFalse(modalWindowFormValidator.isValid(modalWindowDto))
    }
}