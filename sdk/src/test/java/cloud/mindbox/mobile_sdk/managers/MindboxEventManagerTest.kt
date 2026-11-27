package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.pushes.DeliveredUrl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.services.PushDeliveryWorkManager
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import cloud.mindbox.mobile_sdk.utils.setEventScope
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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

    private companion object {
        const val WAIT_MS = 10_000L
    }

    private val context: Context = mockk(relaxed = true)
    private val gatewayManager: GatewayManager = mockk()
    private val pushDeliveredManager: PushDeliveredManager = mockk()
    private val deliveryPauseManager = DeliveryPauseManager { Milliseconds(0L) }
    private val originalMindboxScope = Mindbox.mindboxScope
    private val originalEventScope = Mindbox.eventScope
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val eventScope = CoroutineScope(Dispatchers.Unconfined)
    private val sentEvents = CopyOnWriteArrayList<Event>()
    private val queuedEvents = CopyOnWriteArrayList<Event>()
    private val storedDeliveries = CopyOnWriteArrayList<PushDelivery>()
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
        setEventScope(eventScope)
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
        every { MindboxPreferences.hostAppName } returns "test-app"
        mockkObject(PushDeliveryWorkManager)
        every { PushDeliveryWorkManager.enqueue(any(), any()) } just runs
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            sentEvents.add(thirdArg())
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(AsyncEventSendResult.THROTTLED)
        }
    }

    @After
    fun tearDown() {
        MindboxEventManager.resetEventFlowCache()
        scope.cancel()
        eventScope.cancel()
        setMindboxScope(originalMindboxScope)
        setEventScope(originalEventScope)
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
    fun `pushDelivered stores the confirmation with its url, receipt time and provider and starts its task`() {
        every { DbManager.addPushDelivery(any()) } answers {
            storedDeliveries.add(firstArg())
            5L
        }

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d/key/hash?sig=1"), provider = "FCM", receivedAt = Timestamp(42L))

        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
        assertEquals(
            listOf(PushDelivery(url = "https://mbx.ru/d/key/hash?sig=1", receivedAt = 42L, provider = "FCM")),
            storedDeliveries,
        )
    }

    @Test
    fun `pushDelivered neither sends the confirmation itself nor puts it into the operations queue`() {
        every { DbManager.addPushDelivery(any()) } returns 5L

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d"), provider = null, receivedAt = Timestamp(42L))

        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        verify(exactly = 0) { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { DbManager.addEventToQueue(any(), any()) }
        verify(exactly = 0) { BackgroundWorkManager.startOneTimeService(any()) }
    }

    @Test
    fun `pushDelivered stores the confirmation only after the SDK migrations finish`() {
        val migrated = CompletableDeferred<Unit>()
        coEvery { InitializeLock.await(InitializeLock.State.MIGRATION) } coAnswers { migrated.await() }
        every { DbManager.addPushDelivery(any()) } returns 5L

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d"), provider = null, receivedAt = Timestamp(42L))

        coVerify(timeout = WAIT_MS) { InitializeLock.await(InitializeLock.State.MIGRATION) }
        verify(exactly = 0) { DbManager.addPushDelivery(any()) }
        migrated.complete(Unit)
        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
    }

    @Test
    fun `pushDelivered is stored when the process has no Mindbox init and no saved configuration`() {
        coEvery { InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG) } coAnswers { awaitCancellation() }
        coEvery { InitializeLock.await(InitializeLock.State.APP_STARTED) } coAnswers { awaitCancellation() }
        every { DbManager.getConfigurations() } returns null
        every { DbManager.addPushDelivery(any()) } returns 5L

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d"), provider = null, receivedAt = Timestamp(42L))

        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
    }

    @Test
    fun `pushDelivered waiting for the SDK migrations stores the confirmation and starts its task when a soft re-init stops the SDK scope meanwhile`() {
        val migrated = CompletableDeferred<Unit>()
        coEvery { InitializeLock.await(InitializeLock.State.MIGRATION) } coAnswers { migrated.await() }
        every { DbManager.addPushDelivery(any()) } returns 5L

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d"), provider = null, receivedAt = Timestamp(42L))
        scope.cancel()
        migrated.complete(Unit)

        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
    }

    @Test
    fun `two pushes in a row get two rows and two tasks`() {
        every { DbManager.addPushDelivery(any()) } answers {
            storedDeliveries.add(firstArg())
            if (firstArg<PushDelivery>().url.endsWith("/1")) 5L else 6L
        }

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/1"), provider = null, receivedAt = Timestamp(42L))
        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/2"), provider = null, receivedAt = Timestamp(43L))

        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 5L) }
        verify(timeout = WAIT_MS, exactly = 1) { PushDeliveryWorkManager.enqueue(context, 6L) }
        assertEquals(setOf("https://mbx.ru/1", "https://mbx.ru/2"), storedDeliveries.map { it.url }.toSet())
    }

    @Test
    fun `confirmation that was not stored gets no task`() {
        every { DbManager.addPushDelivery(any()) } returns null

        MindboxEventManager.pushDelivered(context, url("https://mbx.ru/d"), provider = null, receivedAt = Timestamp(42L))

        verify(timeout = WAIT_MS, exactly = 1) { DbManager.addPushDelivery(any()) }
        verify(exactly = 0) { PushDeliveryWorkManager.enqueue(any(), any()) }
    }

    private fun url(raw: String) = checkNotNull(DeliveredUrl.of(raw))
}
