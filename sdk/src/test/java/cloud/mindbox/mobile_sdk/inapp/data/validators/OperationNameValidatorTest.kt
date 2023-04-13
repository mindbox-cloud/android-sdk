package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.*
import org.junit.Test

class OperationNameValidatorTest {
    private val validator = OperationNameValidator()

    @Test
    fun `operationName is valid`() {
        assertTrue(validator.isValid("viewCategory"))
        assertTrue(validator.isValid("viewProduct"))
        assertTrue(validator.isValid("setCart"))
    }

    @Test
    fun `operationName is not valid`() {
        assertFalse(validator.isValid(""))
        assertFalse(validator.isValid("     "))
        assertFalse(validator.isValid(null))
        assertFalse(validator.isValid("\n\t"))
        assertFalse(validator.isValid("viewCategory "))
        assertFalse(validator.isValid("viewcategory"))
    }
}