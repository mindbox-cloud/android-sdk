package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.generateRandomUuid
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val ADS_ID = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val SECOND_ADS_ID = "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"
private const val RANDOM_UUID = "ffffffff-ffff-ffff-ffff-ffffffffffff"
private const val ANONYMIZER_DOMAIN = "anonymizer.example.com"

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceUuidResolverImplTest {

    private val context = mockk<Context>()

    private var storedDeviceUuid = ""
    private var shouldCollect = true
    private var operationsDomainFromConfig: String? = null
    private var configuration: Configuration? = storedConfiguration(operationsDomain = null)

    private val firstHandler = mockk<PushServiceHandler> {
        every { getAdsIdentification(context) } returns ADS_ID
    }
    private val secondHandler = mockk<PushServiceHandler> {
        every { getAdsIdentification(context) } returns SECOND_ADS_ID
    }

    private fun resolver(handlers: List<PushServiceHandler> = listOf(firstHandler, secondHandler)) =
        DeviceUuidResolverImpl(
            pushServiceHandlers = { handlers },
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    @Before
    fun onTestStart() {
        mockkStatic("cloud.mindbox.mobile_sdk.ExtensionsKt")
        every { generateRandomUuid() } returns RANDOM_UUID
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.deviceUuid } answers { storedDeviceUuid }
        every { MindboxPreferences.deviceUuid = any() } answers { storedDeviceUuid = firstArg() }
        every { MindboxPreferences.shouldCollectTrackingIds } answers { shouldCollect }
        every { MindboxPreferences.operationsDomainFromConfig } answers { operationsDomainFromConfig }
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } answers { configuration }
    }

    @After
    fun onTestEnd() {
        unmockkObject(DbManager)
        unmockkObject(MindboxPreferences)
        unmockkStatic("cloud.mindbox.mobile_sdk.ExtensionsKt")
    }

    private fun storedConfiguration(operationsDomain: String?) = Configuration(
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "test-endpoint",
        domain = "api.mindbox.ru",
        packageName = "com.test.app",
        versionName = "1.0.0",
        versionCode = "1",
        subscribeCustomerIfCreated = true,
        shouldCreateCustomer = true,
        operationsDomain = operationsDomain,
    )

    // region already assigned

    @Test
    fun `returns the stored deviceUuid without asking the providers`() = runTest {
        storedDeviceUuid = "stored-uuid"

        assertEquals("stored-uuid", resolver().resolve(context))

        verify(exactly = 0) { firstHandler.getAdsIdentification(any()) }
        verify(exactly = 0) { MindboxPreferences.deviceUuid = any() }
        verify(exactly = 0) { generateRandomUuid() }
    }

    @Test
    fun `assigns once and then returns the same value`() = runTest {
        val resolver = resolver()

        val first = resolver.resolve(context)
        val second = resolver.resolve(context)

        assertEquals(ADS_ID, first)
        assertEquals(first, second)
        verify(exactly = 1) { firstHandler.getAdsIdentification(context) }
        verify(exactly = 1) { MindboxPreferences.deviceUuid = ADS_ID }
    }

    // endregion

    // region tracking on

    @Test
    fun `takes the advertising id of the first provider and stores it`() = runTest {
        assertEquals(ADS_ID, resolver().resolve(context))

        assertEquals(ADS_ID, storedDeviceUuid)
        verify(exactly = 0) { secondHandler.getAdsIdentification(any()) }
        verify(exactly = 0) { generateRandomUuid() }
    }

    @Test
    fun `generates a random deviceUuid when there are no providers`() = runTest {
        assertEquals(RANDOM_UUID, resolver(handlers = emptyList()).resolve(context))

        assertEquals(RANDOM_UUID, storedDeviceUuid)
        verify(exactly = 1) { generateRandomUuid() }
    }

    // endregion

    // region tracking off

    @Test
    fun `generates a random deviceUuid when disableTrackingIds is set`() = runTest {
        shouldCollect = false

        assertEquals(RANDOM_UUID, resolver().resolve(context))

        assertEquals(RANDOM_UUID, storedDeviceUuid)
        verify(exactly = 0) { firstHandler.getAdsIdentification(any()) }
        verify(exactly = 0) { secondHandler.getAdsIdentification(any()) }
    }

    @Test
    fun `keeps the deviceUuid assigned from the advertising id after the tracking is turned off`() = runTest {
        storedDeviceUuid = ADS_ID
        shouldCollect = false

        assertEquals(ADS_ID, resolver().resolve(context))

        verify(exactly = 0) { generateRandomUuid() }
    }

    // endregion

    // region anonymizer

    @Test
    fun `generates a random deviceUuid when operationsDomain is set in init`() = runTest {
        configuration = storedConfiguration(operationsDomain = ANONYMIZER_DOMAIN)

        assertEquals(RANDOM_UUID, resolver().resolve(context))

        assertEquals(RANDOM_UUID, storedDeviceUuid)
        verify(exactly = 0) { firstHandler.getAdsIdentification(any()) }
        verify(exactly = 0) { secondHandler.getAdsIdentification(any()) }
    }

    @Test
    fun `generates a random deviceUuid when operationsDomain comes from the mobile config`() = runTest {
        operationsDomainFromConfig = ANONYMIZER_DOMAIN

        assertEquals(RANDOM_UUID, resolver().resolve(context))

        verify(exactly = 0) { firstHandler.getAdsIdentification(any()) }
    }

    @Test
    fun `a blank operationsDomain does not stop the advertising id`() = runTest {
        configuration = storedConfiguration(operationsDomain = "  ")
        operationsDomainFromConfig = ""

        assertEquals(ADS_ID, resolver().resolve(context))
    }

    @Test
    fun `keeps the deviceUuid assigned from the advertising id after an anonymizer appears`() = runTest {
        storedDeviceUuid = ADS_ID
        operationsDomainFromConfig = ANONYMIZER_DOMAIN

        assertEquals(ADS_ID, resolver().resolve(context))

        verify(exactly = 0) { generateRandomUuid() }
    }

    // endregion
}
