package cloud.mindbox.mobile_sdk.inapp.data.validators

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class CloseButtonModalElementValidatorTest {

    @get: Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var positionValidator: CloseButtonModalPositionValidator

    @MockK
    private lateinit var sizeValidator: CloseButtonModalSizeValidator

    @OverrideMockKs
    private lateinit var closeButtonElementValidator: CloseButtonModalElementValidator

    @Test
    fun `validate success`() {
        every {
            sizeValidator.isValid(any())
        } returns true
        every {
            positionValidator.isValid(any())
        } returns true

        assertTrue(closeButtonElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong size`() {
        every {
            sizeValidator.isValid(any())
        } returns false
        every {
            positionValidator.isValid(any())
        } returns true

        assertFalse(closeButtonElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong position`() {
        every {
            sizeValidator.isValid(any())
        } returns true
        every {
            positionValidator.isValid(any())
        } returns false

        assertFalse(closeButtonElementValidator.isValid(mockk(relaxed = true)))
    }

    @Test
    fun `validate wrong size and position`() {
        every {
            sizeValidator.isValid(any())
        } returns false
        every {
            positionValidator.isValid(any())
        } returns false

        assertFalse(closeButtonElementValidator.isValid(mockk(relaxed = true)))
    }
}