package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import com.android.volley.Request
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GatewayManagerPushDeliveredTest {

    private val serviceGenerator: MindboxServiceGenerator = mockk(relaxed = true)
    private val gatewayManager = GatewayManager(serviceGenerator, timeProvider = { SystemTimeProvider() })

    private val configuration = Configuration(
        configurationId = 1L,
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "test-endpoint-id",
        domain = "api.mindbox.ru",
        packageName = "com.test.app",
        versionName = "1.0.0",
        versionCode = "1",
        subscribeCustomerIfCreated = true,
        shouldCreateCustomer = true,
    )

    @Before
    fun setUp() {
        mockLogger()
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.operationsDomainFromConfig } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `push delivered event is not sent through the gateway and stays queued`() {
        val results = mutableListOf<AsyncEventSendResult>()
        val event = Event(
            eventType = EventType.PushDelivered,
            additionalFields = hashMapOf(EventType.PushDelivered.FIELD_URL to "https://mbx.ru/d"),
        )

        gatewayManager.sendAsyncEvent(configuration, "device-uuid", event, shouldCountOffset = false) { results.add(it) }

        verify(exactly = 0) { serviceGenerator.addToRequestQueue(any<MindboxRequest>()) }
        verify(exactly = 0) { serviceGenerator.addToRequestQueue(any<Request<*>>()) }
        assertEquals(listOf(AsyncEventSendResult.RETRY_LATER), results)
    }
}
