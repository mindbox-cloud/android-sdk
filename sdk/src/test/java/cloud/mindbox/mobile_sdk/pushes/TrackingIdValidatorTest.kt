package cloud.mindbox.mobile_sdk.pushes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shape is not checked: a value that is not a GUID is still a value. */
class TrackingIdValidatorTest {

    @Test
    fun `canonical guid is accepted`() {
        assertTrue(TrackingIdValidator.isValid("38400000-8cf0-11bd-b23e-10b96e40000d"))
    }

    @Test
    fun `a guid that is almost all zeroes is still accepted`() {
        assertTrue(TrackingIdValidator.isValid("00000000-0000-0000-0000-000000000001"))
    }

    @Test
    fun `an identifier that is not a guid is accepted`() {
        assertTrue(TrackingIdValidator.isValid("not-a-guid"))
    }

    @Test
    fun `an opaque vendor identifier is accepted`() {
        assertTrue(TrackingIdValidator.isValid("A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"))
    }

    @Test
    fun `a guid without dashes is accepted`() {
        assertTrue(TrackingIdValidator.isValid("384000008cf011bdb23e10b96e40000d"))
    }

    @Test
    fun `null is rejected`() {
        assertFalse(TrackingIdValidator.isValid(null))
    }

    @Test
    fun `empty string is rejected`() {
        assertFalse(TrackingIdValidator.isValid(""))
    }

    @Test
    fun `blank string is rejected`() {
        assertFalse(TrackingIdValidator.isValid("   "))
    }

    /** What Google Play services return when the user limited ad tracking. */
    @Test
    fun `zero guid is rejected`() {
        assertFalse(TrackingIdValidator.isValid("00000000-0000-0000-0000-000000000000"))
    }

    /** MOBILE-321: some devices answer with a short zero form instead of the 36-char sentinel. */
    @Test
    fun `the non standard zero sentinel is rejected`() {
        assertFalse(TrackingIdValidator.isValid("0000-0000"))
    }

    @Test
    fun `zero sentinel without dashes is rejected`() {
        assertFalse(TrackingIdValidator.isValid("00000000000000000000000000000000"))
    }

    @Test
    fun `a single zero is rejected`() {
        assertFalse(TrackingIdValidator.isValid("0"))
    }
}
