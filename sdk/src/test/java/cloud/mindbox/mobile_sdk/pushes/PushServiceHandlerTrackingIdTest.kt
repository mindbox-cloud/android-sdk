package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.models.TrackingId
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

private const val GOOGLE = "google"
private const val GOOGLE_VALUE = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val ZERO_GUID = "00000000-0000-0000-0000-000000000000"

/** Guards that `tryGetTrackingId` never invents a value the way `getAdsIdentification` does. */
class PushServiceHandlerTrackingIdTest {

    private val context = mockk<Context>()

    private class TestPushServiceHandler(
        override val trackingIdType: String? = GOOGLE,
        private val isServiceAvailable: Boolean = true,
        private val adsId: () -> Pair<String?, Boolean>,
    ) : PushServiceHandler() {

        override val notificationProvider: String = "TEST"

        override suspend fun initService(context: Context) = Unit

        override fun getAdsId(context: Context): Pair<String?, Boolean> = adsId()

        override fun isAvailable(context: Context): Boolean = isServiceAvailable

        override suspend fun getToken(context: Context): String? = null

        override fun convertToRemoteMessage(message: Any): MindboxRemoteMessage? = null
    }

    @Test
    fun `valid id is reported`() {
        val handler = TestPushServiceHandler { GOOGLE_VALUE to false }

        assertEquals(
            TrackingIdResult.Success(TrackingId(GOOGLE, GOOGLE_VALUE)),
            handler.tryGetTrackingId(context),
        )
    }

    @Test
    fun `limit ad tracking counts as an opt-out`() {
        val handler = TestPushServiceHandler { GOOGLE_VALUE to true }

        assertEquals(TrackingIdResult.Denied, handler.tryGetTrackingId(context))
    }

    @Test
    fun `zero guid counts as an opt-out`() {
        val handler = TestPushServiceHandler { ZERO_GUID to false }

        assertEquals(TrackingIdResult.Denied, handler.tryGetTrackingId(context))
    }

    @Test
    fun `an identifier that is not a guid is reported as is`() {
        val handler = TestPushServiceHandler { "not-a-guid" to false }

        assertEquals(
            TrackingIdResult.Success(TrackingId(GOOGLE, "not-a-guid")),
            handler.tryGetTrackingId(context),
        )
    }

    @Test
    fun `empty id counts as an opt-out`() {
        val handler = TestPushServiceHandler { "" to false }

        assertEquals(TrackingIdResult.Denied, handler.tryGetTrackingId(context))
    }

    @Test
    fun `an unavailable provider is not an opt-out`() {
        val handler = TestPushServiceHandler(isServiceAvailable = false) { GOOGLE_VALUE to false }

        assertEquals(TrackingIdResult.Unavailable, handler.tryGetTrackingId(context))
    }

    @Test
    fun `a throwing provider never yields a random uuid`() {
        val handler = TestPushServiceHandler { error("Google Play services are updating") }

        assertEquals(TrackingIdResult.Unavailable, handler.tryGetTrackingId(context))
    }

    @Test
    fun `a provider without an tracking id type supplies nothing`() {
        val handler = TestPushServiceHandler(trackingIdType = null) { GOOGLE_VALUE to false }

        assertEquals(TrackingIdResult.NotSupported, handler.tryGetTrackingId(context))
    }

    @Test
    fun `deviceUUID seeding still falls back to a random uuid`() {
        val handler = TestPushServiceHandler { error("Google Play services are updating") }

        val deviceUuid = handler.getAdsIdentification(context)

        assertEquals(36, deviceUuid.length)
    }
}
