package cloud.mindbox.mobile_sdk.inapp.data.validators

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class CloseButtonSnackbarElementValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var positionValidator: CloseButtonSnackbarPositionValidator

    @MockK
    private lateinit var sizeValidator: CloseButtonSnackbarSizeValidator

    @OverrideMockKs
    private lateinit var closeButtonSnackBarElementValidator: CloseButtonSnackbarElementValidator

    @Test
    fun `validate success`() {
        every {
            positionValidator.isValid(any())
        } returns true

        every {
            sizeValidator.isValid(any())
        } returns true
        assertTrue(closeButtonSnackBarElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong position`() {
        every {
            positionValidator.isValid(any())
        } returns false

        every {
            sizeValidator.isValid(any())
        } returns true
        assertFalse(closeButtonSnackBarElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong size`() {
        every {
            positionValidator.isValid(any())
        } returns true

        every {
            sizeValidator.isValid(any())
        } returns false
        assertFalse(closeButtonSnackBarElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong position and size`() {
        every {
            positionValidator.isValid(any())
        } returns false

        every {
            sizeValidator.isValid(any())
        } returns false
        assertFalse(closeButtonSnackBarElementValidator.isValid(mockk(relaxed = true)))
    }
}
