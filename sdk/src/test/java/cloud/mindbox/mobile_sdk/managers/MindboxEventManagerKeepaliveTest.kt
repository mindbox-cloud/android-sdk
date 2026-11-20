package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.TokenData
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MindboxEventManagerKeepaliveTest {

    private val context = mockk<Context>()
    private val timeProvider = mockk<SystemTimeProvider> {
        every { currentTimeMillis() } returns 1_000L
    }
    private val updateData = UpdateData(
        isNotificationsEnabled = true,
        instanceId = "instanceId",
        version = 1,
        tokens = listOf(TokenData(token = "token", notificationProvider = "FCM")),
    )

    private lateinit var originalScope: CoroutineScope

    @Before
    fun setUp() {
        originalScope = Mindbox.mindboxScope
        setMindboxScope(CoroutineScope(Job().apply { cancel() }))
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk(relaxed = true) {
            every { timeProvider } returns this@MindboxEventManagerKeepaliveTest.timeProvider
        }
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.lastInfoUpdateTime = any() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        setMindboxScope(originalScope)
    }

    @Test
    fun `appInfoUpdate moves the Keepalive clock by default`() {
        MindboxEventManager.appInfoUpdate(context, updateData)

        verify(exactly = 1) { MindboxPreferences.lastInfoUpdateTime = 1_000L }
    }

    @Test
    fun `appInfoUpdate about the ids alone leaves the Keepalive clock alone`() {
        MindboxEventManager.appInfoUpdate(context, updateData, refreshesKeepalive = false)

        verify(exactly = 0) { MindboxPreferences.lastInfoUpdateTime = any() }
    }

    @Test
    fun `appTrackingIdsUpdate leaves the Keepalive clock alone`() {
        MindboxEventManager.appTrackingIdsUpdate(context, updateData)

        verify(exactly = 0) { MindboxPreferences.lastInfoUpdateTime = any() }
    }

    @Test
    fun `appKeepalive moves the Keepalive clock`() {
        MindboxEventManager.appKeepalive(context, updateData)

        verify(exactly = 1) { MindboxPreferences.lastInfoUpdateTime = 1_000L }
    }

    private fun queuedEventType(send: () -> Unit): EventType {
        setMindboxScope(CoroutineScope(Dispatchers.Unconfined))
        mockkObject(InitializeLock)
        coEvery { InitializeLock.await(any()) } just runs
        mockkObject(DbManager)
        val queued = LinkedBlockingQueue<Event>()
        every { DbManager.addEventToQueue(any(), any()) } answers { queued.put(secondArg()) }
        every { DbManager.getConfigurations() } returns null

        send()

        return checkNotNull(queued.poll(5, TimeUnit.SECONDS)) { "No event was queued" }.eventType
    }

    private val googleIds = listOf(TrackingId("google", "g"))

    @Test
    fun `appTrackingIdsUpdate queues a tracking ids event`() {
        val type = queuedEventType { MindboxEventManager.appTrackingIdsUpdate(context, updateData.copy(trackingIds = googleIds)) }

        assertEquals(EventType.AppTrackingIdsUpdated, type)
    }

    @Test
    fun `appInfoUpdate queues an ordinary update even with an erase`() {
        val type = queuedEventType { MindboxEventManager.appInfoUpdate(context, updateData.copy(trackingIds = emptyList())) }

        assertEquals(EventType.AppInfoUpdated, type)
    }

    @Test
    fun `appKeepalive with a set queues a tracking keepalive`() {
        val type = queuedEventType { MindboxEventManager.appKeepalive(context, updateData.copy(trackingIds = googleIds)) }

        assertEquals(EventType.AppTrackingKeepalive, type)
    }

    @Test
    fun `appKeepalive without a set queues an ordinary keepalive`() {
        val type = queuedEventType { MindboxEventManager.appKeepalive(context, updateData) }

        assertEquals(EventType.AppKeepalive, type)
    }

    @Test
    fun `a keepalive with a non-empty set is a tracking keepalive`() {
        assertEquals(
            EventType.AppTrackingKeepalive,
            MindboxEventManager.keepaliveEventType(listOf(TrackingId("google", "g"))),
        )
    }

    @Test
    fun `a keepalive with an empty or unread set stays an ordinary keepalive`() {
        assertEquals(EventType.AppKeepalive, MindboxEventManager.keepaliveEventType(emptyList()))
        assertEquals(EventType.AppKeepalive, MindboxEventManager.keepaliveEventType(null))
    }
}
