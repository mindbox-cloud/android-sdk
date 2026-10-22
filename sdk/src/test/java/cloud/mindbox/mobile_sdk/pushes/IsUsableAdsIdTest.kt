package cloud.mindbox.mobile_sdk.pushes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * `isUsableAdsId` decides whether an advertising id may become the `deviceUUID`, so a value it
 * wrongly accepts is shared by every device answering the same junk.
 */
class IsUsableAdsIdTest {

    private fun assertUsable(id: String) = assertTrue("Expected $id to be usable", id.isUsableAdsId())

    private fun assertUnusable(id: String) = assertFalse("Expected $id to be rejected", id.isUsableAdsId())

    @Test
    fun `a real advertising id is usable`() {
        assertUsable("38400000-8cf0-11bd-b23e-10b96e40000d")
    }

    @Test
    fun `a randomly generated uuid is usable`() {
        assertUsable(UUID.randomUUID().toString())
    }

    @Test
    fun `an uppercase uuid is usable`() {
        assertUsable("38400000-8CF0-11BD-B23E-10B96E40000D")
    }

    @Test
    fun `a uuid with a single non zero digit is usable`() {
        assertUsable("00000000-0000-0000-0000-000000000001")
    }

    @Test
    fun `the full zero id is rejected`() {
        assertUnusable("00000000-0000-0000-0000-000000000000")
    }

    /** MOBILE-321: the short zero form some devices answer with. */
    @Test
    fun `the short zero id is rejected`() {
        assertUnusable("0000-0000")
    }

    /**
     * `UUID.fromString` is lenient about component length, so this parses — only the character
     * check keeps it out.
     */
    @Test
    fun `a lenient all zero uuid is rejected`() {
        assertUnusable("0-0-0-0-0")
    }

    @Test
    fun `zeroes without dashes are rejected`() {
        assertUnusable("00000000000000000000000000000000")
    }

    @Test
    fun `a dash is rejected`() {
        assertUnusable("-")
    }

    @Test
    fun `an empty string is rejected`() {
        assertUnusable("")
    }

    @Test
    fun `a blank string is rejected`() {
        assertUnusable("   ")
    }

    @Test
    fun `an arbitrary non uuid string is rejected`() {
        assertUnusable("not-a-guid")
    }

    @Test
    fun `a truncated uuid is rejected`() {
        assertUnusable("38400000-8cf0-11bd-b23e")
    }

    @Test
    fun `an overlong uuid is rejected`() {
        assertUnusable("38400000-8cf0-11bd-b23e-10b96e40000d-10b96e40000d")
    }
}
