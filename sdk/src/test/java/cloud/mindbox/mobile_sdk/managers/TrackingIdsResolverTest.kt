package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.TrackingIdResult
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GOOGLE = "google"
private const val HUAWEI = "huawei"
private const val GOOGLE_VALUE = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val HUAWEI_VALUE = "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"
private const val STORED_GOOGLE =
    """[{"type":"google","value":"38400000-8cf0-11bd-b23e-10b96e40000d"}]"""
private const val ANONYMIZER_DOMAIN = "anonymizer.example.com"

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingIdsResolverImplTest {

    private val context = mockk<Context>()

    private var shouldCollect = true
    private var isTrackingDisabled = false
    private var lastSent = ""
    private var operationsDomainFromConfig: String? = null
    private var trackingDomain: String? = null
    private var configuration: Configuration? = storedConfiguration(operationsDomain = null)

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.shouldCollectTrackingIds } answers { shouldCollect }
        every { MindboxPreferences.isTrackingDisabled } answers { isTrackingDisabled }
        every { MindboxPreferences.lastSentTrackingIds } answers { lastSent }
        every { MindboxPreferences.lastSentTrackingIds = any() } answers { lastSent = firstArg() }
        every { MindboxPreferences.operationsDomainFromConfig } answers { operationsDomainFromConfig }
        every { MindboxPreferences.trackingDomain } answers { trackingDomain }
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } answers { configuration }
    }

    @After
    fun onTestEnd() {
        unmockkObject(MindboxPreferences)
        unmockkObject(DbManager)
    }

    private fun storedConfiguration(
        operationsDomain: String?,
        domain: String = "api.mindbox.ru",
    ) = Configuration(
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "test-endpoint",
        domain = domain,
        packageName = "com.test.app",
        versionName = "1.0.0",
        versionCode = "1",
        subscribeCustomerIfCreated = true,
        shouldCreateCustomer = true,
        operationsDomain = operationsDomain,
    )

    private fun resolver(vararg results: TrackingIdResult) = resolver(results.map(::handlerReturning))

    private fun resolver(handlers: List<PushServiceHandler>) = TrackingIdsResolverImpl(
        pushServiceHandlers = { handlers },
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
            every { handler.notificationProvider } returns "HCM"
            every { handler.trackingIdType } returns HUAWEI
            every { handler.tryGetTrackingId(context) } answers {
                Thread.sleep(300L)
                TrackingIdResult.Denied(HUAWEI)
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

    // region identifiers read with certainty

    @Test
    fun `available identifier is returned`() = runTest {
        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
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
    fun `resolve alone persists nothing`() = runTest {
        resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context)

        assertEquals("", lastSent)
    }

    @Test
    fun `markSent records what the operation carried`() = runTest {
        val resolver = resolver(success(GOOGLE, GOOGLE_VALUE))

        resolver.markSent(resolver.resolve(context)!!)

        assertEquals(STORED_GOOGLE, lastSent)
    }

    @Test
    fun `a fresh value is reported even when the same type was sent before`() = runTest {
        lastSent = STORED_GOOGLE
        val renewed = "11111111-1111-1111-1111-111111111111"

        assertEquals(listOf(TrackingId(GOOGLE, renewed)), resolver(success(GOOGLE, renewed)).resolve(context))
    }

    // endregion

    // region no identifier: an empty set erases what the backend has

    @Test
    fun `opt-out reports an empty set`() = runTest {
        lastSent = STORED_GOOGLE

        assertEquals(emptyList<TrackingId>(), resolver(TrackingIdResult.Denied(GOOGLE)).resolve(context))
    }

    @Test
    fun `missing services report an empty set`() = runTest {
        lastSent = STORED_GOOGLE

        assertEquals(emptyList<TrackingId>(), resolver(TrackingIdResult.Unavailable).resolve(context))
    }

    @Test
    fun `a RuStore-only device reports an empty set`() = runTest {
        assertEquals(emptyList<TrackingId>(), resolver(TrackingIdResult.NotSupported).resolve(context))
    }

    @Test
    fun `no providers at all report an empty set`() = runTest {
        lastSent = STORED_GOOGLE

        assertEquals(emptyList<TrackingId>(), resolver().resolve(context))
    }

    @Test
    fun `a provider that answered outweighs one without services`() = runTest {
        val payload = resolver(
            TrackingIdResult.Unavailable,
            success(HUAWEI, HUAWEI_VALUE),
        ).resolve(context)

        assertEquals(listOf(huaweiId), payload)
    }

    @Test
    fun `a provider dropped from the integration is simply absent from the set`() = runTest {
        lastSent = STORED_GOOGLE

        // Firebase was dropped, the new build passes HMS only: the backend replaces the whole set.
        assertEquals(listOf(huaweiId), resolver(success(HUAWEI, HUAWEI_VALUE)).resolve(context))
    }

    @Test
    fun `the empty set keeps being reported, so a lost erase heals itself`() = runTest {
        lastSent = STORED_GOOGLE
        val resolver = resolver(TrackingIdResult.Denied(GOOGLE))

        val erase = resolver.resolve(context)!!
        resolver.markSent(erase)

        assertEquals(emptyList<TrackingId>(), erase)
        assertEquals(emptyList<TrackingId>(), resolver.resolve(context))
    }

    @Test
    fun `granting the permission again restores the value`() = runTest {
        lastSent = "[]"

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    // endregion

    // region not sure: null leaves the stored set alone

    @Test
    fun `a failed read is null`() = runTest {
        lastSent = STORED_GOOGLE

        assertNull(resolver(TrackingIdResult.Failed).resolve(context))
    }

    @Test
    fun `a failed read is null even when nothing was ever sent`() = runTest {
        assertNull(resolver(TrackingIdResult.Failed).resolve(context))
    }

    @Test
    fun `a hanging provider is null`() = runBlocking {
        assertNull(resolverWithHangingProvider().resolve(context))
    }

    @Test
    fun `a fresh value that came in time does not outweigh a hanging provider`() = runBlocking {
        assertNull(resolverWithHangingProvider(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `a fresh value next to a failed read is null`() = runTest {
        assertNull(resolver(success(GOOGLE, GOOGLE_VALUE), TrackingIdResult.Failed).resolve(context))
    }

    @Test
    fun `a failed read persists nothing`() = runTest {
        lastSent = STORED_GOOGLE

        resolver(TrackingIdResult.Failed).resolve(context)

        assertEquals(STORED_GOOGLE, lastSent)
    }

    @Test
    fun `a failed read does not produce value-empty-value churn`() = runTest {
        val available = resolver(success(GOOGLE, GOOGLE_VALUE))
        val failed = resolver(TrackingIdResult.Failed)

        available.markSent(available.resolve(context)!!)
        val duringOutage = listOf(failed.resolve(context), failed.resolve(context))
        val afterRecovery = available.resolve(context)!!

        assertEquals(listOf(null, null), duringOutage)
        assertEquals(listOf(googleId), afterRecovery)
        assertFalse(available.hasChanged(afterRecovery))
    }

    // endregion

    // region switches

    @Test
    fun `nothing is collected when the integrator turned tracking ids off`() = runTest {
        lastSent = STORED_GOOGLE
        shouldCollect = false

        assertEquals(emptyList<TrackingId>(), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `nothing is collected when trackingDomain is null`() = runTest {
        lastSent = STORED_GOOGLE
        isTrackingDisabled = true
        trackingDomain = null
        val handler = handlerReturning(success(GOOGLE, GOOGLE_VALUE))

        assertEquals(emptyList<TrackingId>(), resolver(listOf(handler)).resolve(context))
        verify(exactly = 0) { handler.tryGetTrackingId(any()) }
    }

    @Test
    fun `nothing is collected when operationsDomain is set in init`() = runTest {
        configuration = storedConfiguration(operationsDomain = ANONYMIZER_DOMAIN)

        assertEquals(emptyList<TrackingId>(), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `nothing is collected when operationsDomain comes from the mobile config`() = runTest {
        operationsDomainFromConfig = ANONYMIZER_DOMAIN

        assertEquals(emptyList<TrackingId>(), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `providers are not even read when operationsDomain is set`() = runTest {
        operationsDomainFromConfig = ANONYMIZER_DOMAIN
        val handler = handlerReturning(success(GOOGLE, GOOGLE_VALUE))

        resolver(listOf(handler)).resolve(context)

        verify(exactly = 0) { handler.tryGetTrackingId(any()) }
    }

    @Test
    fun `a switch is an erase, not a doubt, even when the read would have failed`() = runTest {
        shouldCollect = false

        assertEquals(emptyList<TrackingId>(), resolver(TrackingIdResult.Failed).resolve(context))
    }

    @Test
    fun `operationsDomain added to the mobile config stops the collection`() = runTest {
        val resolver = resolver(success(GOOGLE, GOOGLE_VALUE))
        resolver.markSent(resolver.resolve(context)!!)

        operationsDomainFromConfig = ANONYMIZER_DOMAIN
        val payload = resolver.resolve(context)!!

        assertEquals(emptyList<TrackingId>(), payload)
        assertTrue(resolver.hasChanged(payload))
    }

    @Test
    fun `operationsDomain removed from the mobile config resumes the collection`() = runTest {
        val resolver = resolver(success(GOOGLE, GOOGLE_VALUE))
        operationsDomainFromConfig = ANONYMIZER_DOMAIN
        resolver.markSent(resolver.resolve(context)!!)

        operationsDomainFromConfig = null

        assertEquals(listOf(googleId), resolver.resolve(context))
    }

    @Test
    fun `a blank operationsDomain in the stored configuration does not stop the collection`() = runTest {
        configuration = storedConfiguration(operationsDomain = "  ")

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `a missing stored configuration does not stop the collection`() = runTest {
        configuration = null

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    // endregion

    // region tracking host

    @Test
    fun `no tracking host is an erase and the providers are not read`() = runTest {
        lastSent = STORED_GOOGLE
        configuration = storedConfiguration(operationsDomain = null, domain = "gost-api.mindbox.ru")
        val handler = handlerReturning(success(GOOGLE, GOOGLE_VALUE))

        assertEquals(emptyList<TrackingId>(), resolver(listOf(handler)).resolve(context))
        verify(exactly = 0) { handler.tryGetTrackingId(any()) }
    }

    @Test
    fun `an explicit trackingDomain supplies the host for a domain without a twin`() = runTest {
        configuration = storedConfiguration(operationsDomain = null, domain = "gost-api.mindbox.ru")
        trackingDomain = "api-tracking.mindbox.ru"

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `a domain with a twin collects without any trackingDomain`() = runTest {
        configuration = storedConfiguration(operationsDomain = null, domain = "https://api-staging.mindbox.ru/")

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    @Test
    fun `a missing stored configuration does not deny the tracking host`() = runTest {
        configuration = null

        assertEquals(listOf(googleId), resolver(success(GOOGLE, GOOGLE_VALUE)).resolve(context))
    }

    // endregion

    // region hasChanged

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

    // endregion
}
