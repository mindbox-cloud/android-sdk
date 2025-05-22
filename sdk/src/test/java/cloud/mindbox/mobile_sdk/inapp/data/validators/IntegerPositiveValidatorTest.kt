package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.*
import org.junit.Test

class IntegerPositiveValidatorTest {

    private val validator = IntegerPositiveValidator()

    @Test
    fun `positive number should be valid`() {
        assertTrue("Positive number should be valid", validator.isValid(42))
        assertTrue("Positive number should be valid", validator.isValid(1))
        assertTrue("Positive number should be valid", validator.isValid(Int.MAX_VALUE))
    }

    @Test
    fun `zero should be valid`() {
        assertTrue("Zero should be invalid", validator.isValid(0))
    }

    @Test
    fun `negative number should be invalid`() {
        assertFalse("Negative number should be invalid", validator.isValid(-1))
        assertFalse("Negative number should be invalid", validator.isValid(-42))
        assertFalse("Negative number should be invalid", validator.isValid(Int.MIN_VALUE))
    }

    @Test
    fun `null should be invalid`() {
        assertFalse("Null should be invalid", validator.isValid(null))
    }
}
