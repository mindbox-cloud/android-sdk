package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class ModalWindowValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var imageLayerValidator: ImageLayerValidator

    @MockK
    private lateinit var elementValidator: ModalElementValidator

    @InjectMockKs
    private lateinit var modalWindowValidator: ModalWindowValidator

    @Test
    fun `test isValid returns true when all conditions are met`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getModalWindowContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(InAppStub.getImageLayerDto())
                ),
                elements = listOf(InAppStub.getCloseButtonElementDto())
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true

        every {
            elementValidator.isValid(any())
        } returns true

        assertTrue(modalWindowValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when type is not correct`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(type = "invalid_type")

        assertFalse(modalWindowValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when background layers are empty`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getModalWindowContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(layers = emptyList())
            )
        )

        assertFalse(modalWindowValidator.isValid(modalWindowDto))
    }

    @Test
    fun `test isValid returns false when background is null`() {
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getModalWindowContentDto().copy(
                background = null
            )
        )
        assertFalse(modalWindowValidator.isValid(modalWindowDto))
    }
}
