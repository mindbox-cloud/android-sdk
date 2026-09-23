package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.TrackingIdResult
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GOOGLE = "google"
private const val HUAWEI = "huawei"
private const val GOOGLE_VALUE = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val HUAWEI_VALUE = "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"
private const val STORED_BOTH =
    """[{"type":"google","value":"38400000-8cf0-11bd-b23e-10b96e40000d"},{"type":"huawei","value":"1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"}]"""
private const val STORED_GOOGLE =
    """[{"type":"google","value":"38400000-8cf0-11bd-b23e-10b96e40000d"}]"""

class TrackingIdsResolverImplTest {

    private val context = mockk<Context>()

    private var shouldCollect = true
    private var lastSent = ""

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.shouldCollectTrackingIds } answers { shouldCollect }
        every { MindboxPreferences.lastSentTrackingIds } answers { lastSent }
        every { MindboxPreferences.lastSentTrackingIds = any() } answers { lastSent = firstArg() }
    }

    @After
    fun onTestEnd() {
        unmockkObject(MindboxPreferences)
    }

    private fun resolver(vararg results: TrackingIdResult) = TrackingIdsResolverImpl(
        pushServiceHandlers = { results.map(::handlerReturning) },
        scope = { CoroutineScope(UnconfinedTestDispatcher()) },
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    /** Reads on a real dispatcher so a blocking provider actually blocks. */
    private fun resolverWithHangingProvider(vararg fast: TrackingIdResult) = TrackingIdsResolverImpl(
        pushServiceHandlers = { fast.map(::handlerReturning) + handlerThatHangs() },
        scope = { CoroutineScope(Dispatchers.IO) },
        ioDispatcher = Dispatchers.IO,
        timeoutMillis = 100L,
    )

    private fun handlerThatHangs(): PushServiceHandler =
        mockk<PushServiceHandler>().also { handler ->
            every { handler.tryGetTrackingId(context) } answers {
                Thread.sleep(300L)
                TrackingIdResult.Denied(GOOGLE)
            }
        }

    private fun handlerReturning(result: TrackingIdResult): PushServiceHandler =
        mockk<PushServiceHandler>().also { handler ->
            every { handler.tryGetTrackingId(context) } returns result
        }

    private fun success(type: String, value: String) =
        TrackingIdResult.Success(TrackingId(type, value))

    private val googleId = TrackingId(GOOGLE, GOOGLE_VALUE)
    private val huaweiId = TrackingId(HUAWEI, HUAWEI_VALUE)

    @Test
    fun `available identifier is returned`() = runTest {
        val payload = resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals(listOf(googleId), payload)
    }

    @Test
    fun `resolve alone persists nothing`() = runTest {
        resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals("", lastSent)
    }

    @Test
    fun `markSent records what the operation carried`() = runTest {
        val resolver = resolver(success(GOOGLE, GOOGLE_VALUE))

        resolver.markSent(resolver.resolve(context))

        assertEquals(STORED_GOOGLE, lastSent)
    }

    @Test
    fun `an outage repeats the last reported set`() = runTest {
        lastSent = STORED_GOOGLE

        val payload = resolver(TrackingIdResult.Unavailable).resolve(context)

        assertEquals(listOf(googleId), payload)
    }

    @Test
    fun `an outage on a device that reported nothing stays empty`() = runTest {
        val payload = resolver(TrackingIdResult.Unavailable).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `both identifiers are returned on a device with GMS and HMS`() = runTest {
        val payload = resolver(
            success(HUAWEI, HUAWEI_VALUE),
            success(GOOGLE, GOOGLE_VALUE),
        ).resolve(context)

        // Order is independent of the order the handlers were passed in.
        assertEquals(listOf(googleId, huaweiId), payload)
    }

    @Test
    fun `one reachable provider outweighs another one being down`() = runTest {
        val payload = resolver(
            TrackingIdResult.Unavailable,
            success(HUAWEI, HUAWEI_VALUE),
        ).resolve(context)

        assertEquals(listOf(huaweiId), payload)
    }

    @Test
    fun `opt-out erases a value that was sent before`() = runTest {
        lastSent = STORED_GOOGLE

        val payload = resolver(TrackingIdResult.Denied(GOOGLE)).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `opt-out reports an empty set even when nothing was ever sent`() = runTest {
        val payload = resolver(TrackingIdResult.Denied(GOOGLE)).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `the empty set keeps being reported, so a lost erase heals itself`() = runTest {
        lastSent = STORED_GOOGLE
        val resolver = resolver(TrackingIdResult.Denied(GOOGLE))

        val erase = resolver.resolve(context)
        resolver.markSent(erase)

        assertEquals(emptyList<TrackingId>(), erase)
        assertEquals(emptyList<TrackingId>(), resolver.resolve(context))
    }

    @Test
    fun `granting the permission again restores the value`() = runTest {
        lastSent = "[]"

        val payload = resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals(listOf(googleId), payload)
    }

    @Test
    fun `a RuStore-only device reports an empty set`() = runTest {
        val payload = resolver(TrackingIdResult.NotSupported).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `nothing is collected when the integrator turned tracking ids off`() = runTest {
        shouldCollect = false

        val payload = resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `turning the option off erases what was already sent`() = runTest {
        lastSent = STORED_GOOGLE
        shouldCollect = false

        val payload = resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `an outage is never a change, so it triggers no operation of its own`() = runTest {
        lastSent = STORED_GOOGLE
        val outage = resolver(TrackingIdResult.Unavailable)

        assertFalse(outage.hasChanged(outage.resolve(context)))
    }

    @Test
    fun `a hanging provider does not erase the stored value`() = runTest {
        lastSent = STORED_GOOGLE

        val payload = resolverWithHangingProvider().resolve(context)

        // A read we could not finish says nothing about the user's choice.
        assertEquals(listOf(googleId), payload)
    }

    @Test
    fun `a hanging provider is not an opt-out for a device that reported nothing`() = runTest {
        val payload = resolverWithHangingProvider().resolve(context)

        assertEquals(emptyList<TrackingId>(), payload)
    }

    @Test
    fun `a denied provider is erased while an unreadable one keeps its value`() = runTest {
        lastSent = STORED_BOTH

        val payload = resolver(
            TrackingIdResult.Denied(GOOGLE),
            TrackingIdResult.Unavailable,
        ).resolve(context)

        assertEquals(listOf(huaweiId), payload)
    }

    @Test
    fun `no providers at all keeps the stored value`() = runTest {
        lastSent = STORED_GOOGLE

        val payload = resolver().resolve(context)

        assertEquals(listOf(googleId), payload)
    }

    @Test
    fun `a fresh value replaces the stored one of the same type`() = runTest {
        lastSent = STORED_BOTH

        val payload = resolver(success(GOOGLE, "11111111-1111-1111-1111-111111111111")).resolve(context)

        assertEquals(
            listOf(TrackingId(GOOGLE, "11111111-1111-1111-1111-111111111111"), huaweiId),
            payload,
        )
    }

    @Test
    fun `a lasting outage never erases the stored value`() = runTest {
        lastSent = STORED_GOOGLE
        val outage = resolver(TrackingIdResult.Unavailable)

        val payloads = List(10) { outage.resolve(context).also(outage::markSent) }

        assertEquals(List(10) { listOf(googleId) }, payloads)
        assertEquals(STORED_GOOGLE, lastSent)
    }

    @Test
    fun `an outage does not produce value-empty-value churn`() = runTest {
        val available = resolver(success(GOOGLE, GOOGLE_VALUE))
        val outage = resolver(TrackingIdResult.Unavailable)

        available.markSent(available.resolve(context))
        val duringOutage = listOf(outage.resolve(context), outage.resolve(context))
        val afterRecovery = available.resolve(context)

        assertEquals(listOf(listOf(googleId), listOf(googleId)), duringOutage)
        assertEquals(listOf(googleId), afterRecovery)
    }

    @Test
    fun `the same value is not a change`() {
        lastSent = STORED_GOOGLE

        assertFalse(resolver().hasChanged(listOf(googleId)))
    }

    @Test
    fun `a different value is a change`() {
        lastSent = STORED_GOOGLE

        assertTrue(resolver().hasChanged(listOf(huaweiId)))
    }

    @Test
    fun `a first value is a change`() {
        assertTrue(resolver().hasChanged(listOf(googleId)))
    }

    @Test
    fun `an erase is a change`() {
        lastSent = STORED_GOOGLE

        assertTrue(resolver().hasChanged(emptyList()))
    }

    @Test
    fun `an empty set is not a change when nothing was ever sent`() {
        assertFalse(resolver().hasChanged(emptyList()))
    }

    @Test
    fun `an empty set is not a change when it was already reported`() {
        lastSent = "[]"

        assertFalse(resolver().hasChanged(emptyList()))
    }
}
