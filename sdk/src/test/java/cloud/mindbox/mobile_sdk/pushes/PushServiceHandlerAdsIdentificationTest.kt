package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.isUuid
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID_ID = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

/**
 * `getAdsIdentification` seeds `deviceUUID`, which goes into every request URL and into the A/B
 * hash. Anything that is not a real, non-zero UUID has to be replaced by a random one — otherwise
 * every device answering the same junk shares a single `deviceUUID`.
 */
class PushServiceHandlerAdsIdentificationTest {

    private val context = mockk<Context>()

    private class TestPushServiceHandler(
        private val adsId: () -> Pair<String?, Boolean>,
    ) : PushServiceHandler() {

        override val notificationProvider: String = "TEST"

        override suspend fun initService(context: Context) = Unit

        override fun getAdsId(context: Context): Pair<String?, Boolean> = adsId()

        override fun isAvailable(context: Context): Boolean = true

        override suspend fun getToken(context: Context): String? = null

        override fun convertToRemoteMessage(message: Any): MindboxRemoteMessage? = null
    }

    private fun deviceUuidFor(id: String?, isLimitAdTrackingEnabled: Boolean = false): String =
        TestPushServiceHandler { id to isLimitAdTrackingEnabled }.getAdsIdentification(context)

    private fun assertRandomlyGenerated(id: String?) {
        val result = deviceUuidFor(id)

        assertNotEquals(id, result)
        assertTrue("Expected a valid UUID, got $result", result.isUuid())
    }

    @Test
    fun `a valid advertising id is kept`() {
        assertEquals(VALID_ID, deviceUuidFor(VALID_ID))
    }

    /** MOBILE-321: the short zero form some devices answer with. */
    @Test
    fun `the short zero id is replaced by a random uuid`() {
        assertRandomlyGenerated("0000-0000")
    }

    /**
     * Guards the fix itself: `isUuid()` alone accepts the zero id, so a format check on its own
     * would reintroduce the very case the previous guard did handle.
     */
    @Test
    fun `the full zero id is replaced by a random uuid`() {
        assertRandomlyGenerated(ZERO_ID)
    }

    @Test
    fun `zeroes without dashes are replaced by a random uuid`() {
        assertRandomlyGenerated("00000000000000000000000000000000")
    }

    @Test
    fun `a non uuid value is replaced by a random uuid`() {
        assertRandomlyGenerated("not-a-guid")
    }

    @Test
    fun `a dash value is replaced by a random uuid`() {
        assertRandomlyGenerated("-")
    }

    @Test
    fun `an empty value is replaced by a random uuid`() {
        assertRandomlyGenerated("")
    }

    @Test
    fun `a null value is replaced by a random uuid`() {
        assertRandomlyGenerated(null)
    }

    @Test
    fun `limit ad tracking replaces even a valid id`() {
        val result = deviceUuidFor(VALID_ID, isLimitAdTrackingEnabled = true)

        assertNotEquals(VALID_ID, result)
        assertTrue(result.isUuid())
    }

    @Test
    fun `a throwing provider still yields a usable uuid`() {
        val result = TestPushServiceHandler { error("Play services are updating") }
            .getAdsIdentification(context)

        assertTrue(result.isUuid())
    }
}
