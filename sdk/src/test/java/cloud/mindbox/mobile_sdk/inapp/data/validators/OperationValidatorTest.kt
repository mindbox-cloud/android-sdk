package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationValidatorTest {

    private val validator = OperationValidator()

    @Test
    fun `operation is valid`() {
        assertTrue(validator.isValid(SettingsDtoBlank.OperationDtoBlank(systemName = "test")))
        assertTrue(validator.isValid(SettingsDtoBlank.OperationDtoBlank(systemName = "tfkDFKJHFJDst")))
    }

    @Test
    fun `operation is not valid`() {
        assertFalse(validator.isValid(SettingsDtoBlank.OperationDtoBlank(systemName = "")))
        assertFalse(validator.isValid(SettingsDtoBlank.OperationDtoBlank(systemName = "   ")))
        assertFalse(validator.isValid(SettingsDtoBlank.OperationDtoBlank(systemName = "\t")))
    }
}
