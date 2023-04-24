package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationValidatorTest {

    private val validator = OperationValidator()

    @Test
    fun `operation is valid`() {
        assertTrue(validator.isValid(SettingsDto.OperationDtoBlank(systemName = "test")))
        assertTrue(validator.isValid(SettingsDto.OperationDtoBlank(systemName = "tfkDFKJHFJDst")))
    }

    @Test
    fun `operation is not valid`() {
        assertFalse(validator.isValid(SettingsDto.OperationDtoBlank(systemName = null)))
        assertFalse(validator.isValid(SettingsDto.OperationDtoBlank(systemName = "")))
        assertFalse(validator.isValid(SettingsDto.OperationDtoBlank(systemName = "   ")))
        assertFalse(validator.isValid(SettingsDto.OperationDtoBlank(systemName = "\t")))
    }
}