package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSpanPositiveValidatorTest {
    private val validator = TimeSpanPositiveValidator()

    @Test
    fun `isValid returns true for valid unit HOURS and non-negative value`() {
        val validInAppSessionTime = "01:00:00"

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit DAYS and non-negative value`() {
        val validInAppSessionTime = "1.00:00:00"

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit MINUTES and non-negative value`() {
        val validInAppSessionTime = "00:01:00"

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit SECONDS and non-negative value`() {
        val validInAppSessionTime = "00:00:01"

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns false for invalid unit`() {
        val invalidInAppSessionTime = "1:00:00:00"

        assertFalse(validator.isValid(invalidInAppSessionTime))
    }

    @Test
    fun `isValid returns false for negative value`() {
        val negativeInAppSessionTime = "-1:00:00"

        assertFalse(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns false when value is 0`() {
        val negativeInAppSessionTime = "-00:00:00"

        assertFalse(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns true when pushTokenKeepalive not null`() {
        val negativeInAppSessionTime = "00:00:00"

        assertFalse(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns false for invalid formats`() {
        val invalidInputs = listOf(
            "123",
            "",
            " ",
            null
        )

        invalidInputs.forEach { input ->
            assertFalse(validator.isValid(input))
        }
    }
}
