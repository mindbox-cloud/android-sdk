package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.pushes.DeliveredUrl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MindboxEventManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val gatewayManager: GatewayManager = mockk()
    private val pushDeliveredManager: PushDeliveredManager = mockk()
    private val deliveryPauseManager = DeliveryPauseManager { Milliseconds(0L) }
    private val originalMindboxScope = Mindbox.mindboxScope
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val sentEvents = CopyOnWriteArrayList<Event>()
    private val queuedEvents = CopyOnWriteArrayList<Event>()
    private val firstInitializeWrites = CountDownLatch(2)

    @Volatile
    private var isFirstInitialize = true

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

    private val initData = InitData(
        installationId = "",
        externalDeviceUUID = "",
        isNotificationsEnabled = true,
        subscribe = true,
        instanceId = "instance-id",
        ianaTimeZone = null,
        tokens = emptyList(),
    )

    @Before
    fun setUp() {
        mockLogger()
        setMindboxScope(scope)
        mockkObject(Mindbox)
        mockkObject(InitializeLock)
        coEvery { InitializeLock.await(any()) } just runs
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { gatewayManager } returns this@MindboxEventManagerTest.gatewayManager
            every { pushDeliveredManager } returns this@MindboxEventManagerTest.pushDeliveredManager
            every { deliveryPauseManager } returns this@MindboxEventManagerTest.deliveryPauseManager
            every { timeProvider } returns SystemTimeProvider()
        }
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.isFirstInitialize } answers { isFirstInitialize }
        every { MindboxPreferences.isFirstInitialize = any() } answers {
            isFirstInitialize = firstArg()
            firstInitializeWrites.countDown()
        }
        every { MindboxPreferences.deviceUuid } returns "device-uuid"
        every { MindboxPreferences.lastInfoUpdateTime = any() } just runs
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } returns configuration
        every { DbManager.addEventToQueue(any(), any()) } answers {
            queuedEvents.add(secondArg())
            Unit
        }
        every { DbManager.removeEventFromQueue(any()) } answers {
            queuedEvents.remove(firstArg<Event>())
            Unit
        }
        mockkObject(BackgroundWorkManager)
        every { BackgroundWorkManager.startOneTimeService(any()) } just runs
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            sentEvents.add(thirdArg())
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(AsyncEventSendResult.THROTTLED)
        }
    }

    @After
    fun tearDown() {
        MindboxEventManager.resetEventFlowCache()
        scope.cancel()
        setMindboxScope(originalMindboxScope)
        unmockkAll()
    }

    @Test
    fun `second install event held by the pause stays in the queue and first initialization is still completed`() {
        MindboxEventManager.appInstalled(context, initData, shouldCreateCustomer = true)
        MindboxEventManager.appInstalled(context, initData, shouldCreateCustomer = true)

        assertTrue(firstInitializeWrites.await(10, TimeUnit.SECONDS))
        assertEquals(listOf(EventType.AppInstalled), sentEvents.map { it.eventType })
        assertEquals(2, queuedEvents.size)
        assertFalse(isFirstInitialize)
        verify(exactly = 2) { BackgroundWorkManager.startOneTimeService(context) }
    }

    @Test
    fun `pushDelivered queues the confirmation with its url and sends it right away even before the first initialization`() {
        val sent = recordPushDeliveredSends()

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d/key/hash?sig=1"))

        assertTrue(sent.await(10, TimeUnit.SECONDS))
        val queued = queuedEvents.single()
        assertEquals(EventType.PushDelivered, queued.eventType)
        assertEquals("https://mbx.ru/d/key/hash?sig=1", queued.additionalFields?.get(EventType.PushDelivered.FIELD_URL))
        assertEquals(listOf(queued), sentEvents)
        verify(exactly = 0) { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pushDelivered is not passed to in-apps`() {
        isFirstInitialize = false
        val sent = recordPushDeliveredSends()

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d/key/hash?sig=1"))

        assertTrue(sent.await(10, TimeUnit.SECONDS))
        assertTrue(
            MindboxEventManager.eventFlow.replayCache.none { event ->
                event is InAppEventType.OrdinalEvent && event.eventType == EventType.PushDelivered
            }
        )
    }

    @Test
    fun `pushDelivered is sent when the process has no Mindbox init`() {
        coEvery { InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG) } coAnswers { awaitCancellation() }
        coEvery { InitializeLock.await(InitializeLock.State.APP_STARTED) } coAnswers { awaitCancellation() }
        val sent = recordPushDeliveredSends()

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d/key/hash?sig=1"))

        assertTrue(sent.await(10, TimeUnit.SECONDS))
        assertEquals(EventType.PushDelivered, queuedEvents.single().eventType)
    }

    @Test
    fun `pushDelivered waiting for an answer does not hold back operations`() {
        isFirstInitialize = false
        val confirmationSent = CountDownLatch(1)
        var answerConfirmation: ((AsyncEventSendResult) -> Unit)? = null
        every { pushDeliveredManager.send(any(), any(), any(), any()) } answers {
            answerConfirmation = lastArg()
            confirmationSent.countDown()
        }
        val operationSent = CountDownLatch(1)
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            sentEvents.add(thirdArg())
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(AsyncEventSendResult.DELIVERED)
            operationSent.countDown()
        }
        try {
            MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d/key/hash?sig=1"))
            assertTrue(confirmationSent.await(10, TimeUnit.SECONDS))

            MindboxEventManager.asyncOperation(context, "Test.Operation", "{}")

            assertTrue(operationSent.await(10, TimeUnit.SECONDS))
            assertEquals(EventType.AsyncOperation("Test.Operation").operation, sentEvents.single().eventType.operation)
        } finally {
            answerConfirmation?.invoke(AsyncEventSendResult.RETRY_LATER)
        }
    }

    private fun recordPushDeliveredSends(): CountDownLatch {
        val sent = CountDownLatch(1)
        every { pushDeliveredManager.send(any(), any(), any(), any()) } answers {
            sentEvents.add(secondArg())
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(AsyncEventSendResult.THROTTLED)
            sent.countDown()
        }
        return sent
    }

    private fun url(raw: String) = checkNotNull(DeliveredUrl.of(raw))
}
