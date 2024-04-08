package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtlParametersValidatorTest {
    private val validator = TtlParametersValidator()

    @Test
    fun `isValid returns true for valid unit HOURS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "HOURS", value = 1L)

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit DAYS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "DAYS", value = 1L)

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit MINUTES and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "MINUTES", value = 1L)

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit SECONDS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "SECONDS", value = 1L)

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns false for invalid unit`() {
        val invalidUnitTtl =
            SettingsDtoBlank.TtlParametersDtoBlank(unit = "INVALID_UNIT", value = 1L)

        assertFalse(validator.isValid(invalidUnitTtl))
    }

    @Test
    fun `isValid returns false for negative value`() {
        val negativeValueTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "HOURS", value = -1L)

        assertFalse(validator.isValid(negativeValueTtl))
    }

    @Test
    fun `isValid returns true when value is 0`() {
        val negativeValueTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "HOURS", value = 0L)

        assertTrue(validator.isValid(negativeValueTtl))
    }

    @Test
    fun `isValid returns false for null unit and value`() {
        val nullTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = null, value = null)

        assertFalse(validator.isValid(nullTtl))
    }

    @Test
    fun `isValid returns false when unit null and value is valid`() {
        val nullTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = null, value = 1L)

        assertFalse(validator.isValid(nullTtl))
    }

    @Test
    fun `isValid returns false when unit is valid and value is null`() {
        val nullTtl = SettingsDtoBlank.TtlParametersDtoBlank(unit = "DAYS", value = null)

        assertFalse(validator.isValid(nullTtl))
    }
}