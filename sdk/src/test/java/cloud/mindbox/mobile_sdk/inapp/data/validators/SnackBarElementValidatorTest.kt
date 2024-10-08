package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

internal class SnackBarElementValidatorTest {
    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var closeButtonElementValidator: CloseButtonSnackbarElementValidator

    @OverrideMockKs
    private lateinit var modalElementValidator: SnackBarElementValidator

    @Test
    fun `validate close button called`() {
        val testItem = InAppStub.getCloseButtonElementDto()
        every {
            closeButtonElementValidator.isValid(any())
        } returns true
        modalElementValidator.isValid(testItem)
        verify(exactly = 1) {
            closeButtonElementValidator.isValid(testItem)
        }
    }

    @Test
    fun `validate null returns false`() {
        val testItem: ElementDto.CloseButtonElementDto? = null
        assertFalse(modalElementValidator.isValid(testItem))
    }
}
