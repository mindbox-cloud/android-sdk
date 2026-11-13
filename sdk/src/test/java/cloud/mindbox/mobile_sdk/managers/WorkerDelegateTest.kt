package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.mockLogger
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkerDelegateTest {

    private val context: Context = mockk(relaxed = true)
    private val gatewayManager: GatewayManager = mockk()
    private val originalMindboxScope = Mindbox.mindboxScope
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val queue = mutableListOf<Event>()
    private val sentTransactionIds = mutableListOf<String>()
    private val resultsByEventType = mutableMapOf<EventType, AsyncEventSendResult>()
    private val resultsByTransactionId = mutableMapOf<String, AsyncEventSendResult>()
    private var monotonicNow = Milliseconds(0L)
    private val deliveryPauseManager = DeliveryPauseManager { monotonicNow }

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
        setMindboxScope(scope)
        mockkObject(Mindbox)
        every { Mindbox.initComponents(any()) } just runs
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { gatewayManager } returns this@WorkerDelegateTest.gatewayManager
            every { deliveryPauseManager } returns this@WorkerDelegateTest.deliveryPauseManager
        }
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.isFirstInitialize } returns false
        every { MindboxPreferences.deviceUuid } returns "device-uuid"
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } returns configuration
        every { DbManager.getFilteredEventsForBackgroundSend() } answers { queue.toList() }
        every { DbManager.getFilteredEvents() } answers { queue.toList() }
        every { DbManager.removeEventFromQueue(any()) } answers {
            queue.remove(firstArg<Event>())
            Unit
        }
        mockkObject(BackgroundWorkManager)
        every { BackgroundWorkManager.startOneTimeService(any()) } just runs
        every { BackgroundWorkManager.startNextOneTimeService(any()) } just runs
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            val event = thirdArg<Event>()
            sentTransactionIds.add(event.transactionId)
            val result = resultsByTransactionId[event.transactionId] ?: resultsByEventType.getValue(event.eventType)
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(result)
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        setMindboxScope(originalMindboxScope)
        unmockkAll()
    }

    @Test
    fun `throttled operation is not sent again in the same run while other operations are`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        resultsByEventType[operation("Rejected")] = AsyncEventSendResult.DISCARDED
        val refused1 = event("Refused")
        val accepted1 = event("Accepted")
        val refused2 = event("Refused")
        val rejected = event("Rejected")
        val accepted2 = event("Accepted")
        val refused3 = event("Refused")
        queue.addAll(listOf(refused1, accepted1, refused2, rejected, accepted2, refused3))

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(
            listOf(refused1, accepted1, rejected, accepted2).map { it.transactionId },
            sentTransactionIds,
        )
        assertEquals(listOf(refused1, refused2, refused3), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused throttled, skip for 180 s") }
        verify(exactly = 0) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused paused, postpone, 180 s left") }
    }

    @Test
    fun `track visit throttling does not skip a custom operation with the same name`() {
        resultsByEventType[EventType.TrackVisit] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("TrackVisit")] = AsyncEventSendResult.DELIVERED
        val visit1 = Event(eventType = EventType.TrackVisit)
        val custom = event("TrackVisit")
        val visit2 = Event(eventType = EventType.TrackVisit)
        queue.addAll(listOf(visit1, custom, visit2))

        WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(visit1, custom).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(visit1, visit2), queue)
    }

    @Test
    fun `server errors never skip the rest of the operation`() {
        resultsByEventType[operation("Down")] = AsyncEventSendResult.DELIVERED
        val broken1 = event("Down")
        val broken2 = event("Down")
        val healthy = event("Down")
        resultsByTransactionId[broken1.transactionId] = AsyncEventSendResult.RETRY_LATER
        resultsByTransactionId[broken2.transactionId] = AsyncEventSendResult.RETRY_LATER
        queue.addAll(listOf(broken1, broken2, healthy))

        WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(broken1, broken2, healthy).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(broken1, broken2), queue)
    }

    @Test
    fun `operation kept for a later retry is still sent for every event in the run`() {
        resultsByEventType[operation("Silent")] = AsyncEventSendResult.RETRY_LATER
        val events = List(3) { event("Silent") }
        queue.addAll(events)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(events.map { it.transactionId }, sentTransactionIds)
        assertEquals(events, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `run that delivers every event of the operation ends with success`() {
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val events = List(3) { event("Accepted") }
        queue.addAll(events)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(events.map { it.transactionId }, sentTransactionIds)
        assertTrue(queue.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
    }

    @Test
    fun `immediate send schedules the worker only for an event that stays in the queue`() {
        val expectations = mapOf(
            AsyncEventSendResult.THROTTLED to true,
            AsyncEventSendResult.RETRY_LATER to true,
            AsyncEventSendResult.DELIVERED to false,
            AsyncEventSendResult.DISCARDED to false,
        )
        expectations.forEach { (sendResult, staysInQueue) ->
            resultsByEventType[operation("Immediate $sendResult")] = sendResult
            val event = event("Immediate $sendResult")
            queue.add(event)

            sendImmediately(event)

            assertEquals(listOf(event.transactionId), sentTransactionIds)
            assertEquals("$sendResult keeps the event", staysInQueue, event in queue)
            verify(exactly = 1) { gatewayManager.sendAsyncEvent(any(), any(), event, false, any()) }
            verify(exactly = if (staysInQueue) 1 else 0) { BackgroundWorkManager.startOneTimeService(context) }
            queue.clear()
            sentTransactionIds.clear()
            clearMocks(BackgroundWorkManager, answers = false, recordedCalls = true)
        }
    }

    @Test
    fun `operation throttled on an immediate send holds its next events and lets another operation through`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val refused = List(3) { event("Refused") }
        val accepted = event("Accepted")
        queue.addAll(refused + accepted)

        sendImmediately(refused[0])
        sendImmediately(refused[1])
        sendImmediately(accepted)
        sendImmediately(refused[2])

        assertEquals(listOf(refused[0], accepted).map { it.transactionId }, sentTransactionIds)
        assertEquals(refused, queue)
        verify(exactly = 3) { BackgroundWorkManager.startOneTimeService(context) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused throttled, skip for 180 s") }
        verify(exactly = 2) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused paused, postpone, 180 s left") }
    }

    @Test
    fun `held events do not prolong the pause and the operation is sent again once it expires`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val events = List(4) { event("Refused") }
        queue.addAll(events)

        sendImmediately(events[0])
        monotonicNow = Milliseconds(179_999L)
        sendImmediately(events[1])
        sendImmediately(events[2])
        monotonicNow = Milliseconds(180_000L)
        sendImmediately(events[3])

        assertEquals(listOf(events[0], events[3]).map { it.transactionId }, sentTransactionIds)
        assertEquals(events, queue)
        verify(exactly = 2) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused paused, postpone, 0 s left") }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused throttled, skip for 360 s") }
    }

    @Test
    fun `delivery after the pause lets the next events of the operation through at once`() {
        resultsByEventType[operation("Recovering")] = AsyncEventSendResult.DELIVERED
        val refused = event("Recovering")
        val probe = event("Recovering")
        val next = event("Recovering")
        resultsByTransactionId[refused.transactionId] = AsyncEventSendResult.THROTTLED
        queue.addAll(listOf(refused, probe, next))

        sendImmediately(refused)
        monotonicNow = Milliseconds(180_000L)
        sendImmediately(probe)
        sendImmediately(next)

        assertEquals(listOf(refused, probe, next).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(refused), queue)
    }

    @Test
    fun `worker skips an operation paused by an immediate send and runs again without slowing down`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val refused = List(3) { event("Refused") }
        val accepted = List(2) { event("Accepted") }
        queue.addAll(refused + accepted)
        sendImmediately(refused[0])
        sentTransactionIds.clear()

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(accepted.map { it.transactionId }, sentTransactionIds)
        assertEquals(refused, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused paused, postpone, 180 s left") }
        accepted.forEach { event -> verify(exactly = 1) { gatewayManager.sendAsyncEvent(any(), any(), event, true, any()) } }
    }

    @Test
    fun `run that skipped a paused operation and delivered 20 events continues in the next run`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val refused = event("Refused")
        val accepted = List(25) { event("Accepted") }
        queue.addAll(listOf(refused) + accepted)
        sendImmediately(refused)
        sentTransactionIds.clear()

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(accepted.take(20).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(refused) + accepted.drop(20), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `worker sends a paused operation again once its pause has expired`() {
        resultsByEventType[operation("Recovering")] = AsyncEventSendResult.DELIVERED
        val refused = event("Recovering")
        val waiting = event("Recovering")
        resultsByTransactionId[refused.transactionId] = AsyncEventSendResult.THROTTLED
        queue.addAll(listOf(refused, waiting))
        sendImmediately(refused)
        resultsByTransactionId.clear()
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(180_000L)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refused, waiting).map { it.transactionId }, sentTransactionIds)
        assertTrue(queue.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
    }

    @Test
    fun `operation whose pause expires during a worker run is sent again in the same run`() {
        resultsByEventType[operation("Recovering")] = AsyncEventSendResult.DELIVERED
        resultsByEventType[operation("Slow")] = AsyncEventSendResult.RETRY_LATER
        val refused = event("Recovering")
        val slow = event("Slow")
        val waiting = event("Recovering")
        resultsByTransactionId[refused.transactionId] = AsyncEventSendResult.THROTTLED
        queue.addAll(listOf(refused, slow, waiting))
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            val event = thirdArg<Event>()
            sentTransactionIds.add(event.transactionId)
            if (event == slow) monotonicNow = Milliseconds(180_000L)
            val result = resultsByTransactionId[event.transactionId] ?: resultsByEventType.getValue(event.eventType)
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(result)
        }

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refused, slow, waiting).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(refused, slow), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `operation throttled in a worker run is held on the next immediate send`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val queued = event("Refused")
        val created = event("Refused")
        queue.add(queued)
        WorkerDelegate().sendEventsWithResult(context, this)
        queue.add(created)

        sendImmediately(created)

        assertEquals(listOf(queued.transactionId), sentTransactionIds)
        assertEquals(listOf(queued, created), queue)
        verify(exactly = 1) { BackgroundWorkManager.startOneTimeService(context) }
    }

    @Test
    fun `run stops after 20 delivered events and continues in the next run without slowing down`() {
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val events = List(25) { event("Accepted") }
        queue.addAll(events)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(events.take(20).map { it.transactionId }, sentTransactionIds)
        assertEquals(events.drop(20), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: 20 events delivered, continue in the next run") }
    }

    @Test
    fun `run with an operation throttled for the first time slows down even when 20 events are delivered`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val refused = event("Refused")
        val accepted = List(25) { event("Accepted") }
        queue.addAll(listOf(refused) + accepted)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals((listOf(refused) + accepted.take(20)).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(refused) + accepted.drop(20), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
        verify(exactly = 0) { MindboxLoggerImpl.i(any(), "[GD] decision: 20 events delivered, continue in the next run") }
    }

    @Test
    fun `event kept after a server error does not slow down a run that delivered 20 events`() {
        resultsByEventType[operation("Down")] = AsyncEventSendResult.RETRY_LATER
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val broken = event("Down")
        val accepted = List(25) { event("Accepted") }
        queue.addAll(listOf(broken) + accepted)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals((listOf(broken) + accepted.take(20)).map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(broken) + accepted.drop(20), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `throttled operation waiting beyond the 20 delivered events does not slow the run down`() {
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val accepted = List(20) { event("Accepted") }
        val refused = event("Refused")
        queue.addAll(accepted + refused)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(accepted.map { it.transactionId }, sentTransactionIds)
        assertEquals(listOf(refused), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `events too young for this run get the next run after 20 delivered`() {
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val eligible = List(20) { event("Accepted") }
        val young = List(3) { event("Accepted") }
        queue.addAll(eligible + young)
        every { DbManager.getFilteredEventsForBackgroundSend() } answers { queue.filter { it in eligible } }

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(eligible.map { it.transactionId }, sentTransactionIds)
        assertEquals(young, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `worker stopped on the 20th delivery does not schedule the next run`() {
        queue.addAll(List(25) { event("Accepted") })
        val delegate = WorkerDelegate()
        every { gatewayManager.sendAsyncEvent(any(), any(), any(), any(), any()) } answers {
            sentTransactionIds.add(thirdArg<Event>().transactionId)
            if (sentTransactionIds.size == 20) delegate.onEndWork(this@WorkerDelegateTest)
            lastArg<(AsyncEventSendResult) -> Unit>().invoke(AsyncEventSendResult.DELIVERED)
        }

        val result = delegate.sendEventsWithResult(context, this)

        assertEquals(20, sentTransactionIds.size)
        assertTrue("result was $result", result is ListenableWorker.Result.Failure)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `discarded events do not count towards the 20 delivered`() {
        resultsByEventType[operation("Rejected")] = AsyncEventSendResult.DISCARDED
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val rejected = List(5) { event("Rejected") }
        val accepted = List(25) { event("Accepted") }
        queue.addAll(rejected + accepted)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals((rejected + accepted.take(20)).map { it.transactionId }, sentTransactionIds)
        assertEquals(accepted.drop(20), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `run that delivers exactly 20 events and empties the queue does not schedule the next run`() {
        resultsByEventType[operation("Accepted")] = AsyncEventSendResult.DELIVERED
        val events = List(20) { event("Accepted") }
        queue.addAll(events)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(events.map { it.transactionId }, sentTransactionIds)
        assertTrue(queue.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `run with only paused operations sends nothing and runs again without slowing down`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val refused = List(3) { event("Refused") }
        queue.addAll(refused)
        sendImmediately(refused[0])
        sentTransactionIds.clear()

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue(sentTransactionIds.isEmpty())
        assertEquals(refused, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `operation throttled again after its pause doubles the pause without slowing the worker down`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val refused = List(3) { event("Refused") }
        queue.addAll(refused)
        sendImmediately(refused[0])
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(180_000L)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refused[0].transactionId), sentTransactionIds)
        assertEquals(refused, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused throttled, skip for 360 s") }
        verify(exactly = 0) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused paused, postpone, 360 s left") }
    }

    @Test
    fun `operation throttled for the first time in a worker run slows the worker down`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val refused = List(3) { event("Refused") }
        queue.addAll(refused)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refused[0].transactionId), sentTransactionIds)
        assertEquals(refused, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `worker that slowed down on the first throttling runs without slowing down while the pause lasts`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val refused = List(2) { event("Refused") }
        queue.addAll(refused)
        WorkerDelegate().sendEventsWithResult(context, this)
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(179_999L)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue(sentTransactionIds.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `operation throttled again in the same run as a server error slows the worker down`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        resultsByEventType[operation("Down")] = AsyncEventSendResult.RETRY_LATER
        val refused = event("Refused")
        val broken = event("Down")
        queue.addAll(listOf(refused, broken))
        sendImmediately(refused)
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(180_000L)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refused, broken).map { it.transactionId }, sentTransactionIds)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `operation delivered after its pause is treated as throttled for the first time on the next 429`() {
        resultsByEventType[operation("Recovering")] = AsyncEventSendResult.DELIVERED
        val refused = event("Recovering")
        val delivered = event("Recovering")
        val refusedAgain = event("Recovering")
        resultsByTransactionId[refused.transactionId] = AsyncEventSendResult.THROTTLED
        queue.addAll(listOf(refused, delivered))
        sendImmediately(refused)
        resultsByTransactionId.clear()
        monotonicNow = Milliseconds(180_000L)
        sendImmediately(delivered)
        sendImmediately(refused)
        resultsByTransactionId[refusedAgain.transactionId] = AsyncEventSendResult.THROTTLED
        queue.add(refusedAgain)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(refusedAgain), queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 2) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Recovering throttled, skip for 180 s") }
    }

    @Test
    fun `run that finds only events too young to send runs again without slowing down`() {
        val young = List(2) { event("Accepted") }
        queue.addAll(young)
        every { DbManager.getFilteredEventsForBackgroundSend() } returns emptyList()

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue(sentTransactionIds.isEmpty())
        assertEquals(young, queue)
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
    }

    @Test
    fun `run that finds an empty queue ends without scheduling the next run`() {
        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue("result was $result", result is ListenableWorker.Result.Success)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `operation first throttled in a worker run only doubles its pause when the next run is throttled again`() {
        resultsByEventType[operation("Refused")] = AsyncEventSendResult.THROTTLED
        val refused = List(2) { event("Refused") }
        queue.addAll(refused)
        val firstRun = WorkerDelegate().sendEventsWithResult(context, this)
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(180_000L)

        val secondRun = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue("first run was $firstRun", firstRun is ListenableWorker.Result.Retry)
        assertEquals(listOf(refused[0].transactionId), sentTransactionIds)
        assertTrue("second run was $secondRun", secondRun is ListenableWorker.Result.Success)
        verify(exactly = 1) { BackgroundWorkManager.startNextOneTimeService(context) }
        verify(exactly = 1) { MindboxLoggerImpl.i(any(), "[GD] decision: operation Refused throttled, skip for 360 s") }
    }

    @Test
    fun `run that fails before sending anything slows the worker down`() {
        queue.addAll(List(2) { event("Accepted") })
        every { MindboxPreferences.deviceUuid } throws IllegalStateException("no device uuid")

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertTrue(sentTransactionIds.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `stopped worker that finds only events too young to send does not schedule the next run`() {
        queue.addAll(List(2) { event("Accepted") })
        every { DbManager.getFilteredEventsForBackgroundSend() } returns emptyList()
        val delegate = WorkerDelegate()
        delegate.onEndWork(this)

        val result = delegate.sendEventsWithResult(context, this)

        assertTrue("result was $result", result is ListenableWorker.Result.Failure)
        verify(exactly = 0) { BackgroundWorkManager.startNextOneTimeService(any()) }
    }

    @Test
    fun `throttled install event stays in the queue and is delivered by the worker after its pause`() {
        val install = Event(eventType = EventType.AppInstalled)
        resultsByTransactionId[install.transactionId] = AsyncEventSendResult.THROTTLED
        queue.add(install)
        sendImmediately(install)
        assertEquals(listOf(install), queue)
        resultsByTransactionId[install.transactionId] = AsyncEventSendResult.DELIVERED
        sentTransactionIds.clear()
        monotonicNow = Milliseconds(180_000L)

        val result = WorkerDelegate().sendEventsWithResult(context, this)

        assertEquals(listOf(install.transactionId), sentTransactionIds)
        assertTrue(queue.isEmpty())
        assertTrue("result was $result", result is ListenableWorker.Result.Success)
    }

    private val operationTypes = mutableMapOf<String, EventType.AsyncOperation>()

    private fun operation(name: String): EventType = operationTypes.getOrPut(name) { EventType.AsyncOperation(name) }

    private fun event(name: String) = Event(eventType = operation(name))

    private fun sendImmediately(event: Event) = WorkerDelegate().sendEventOrPostpone(
        context = context,
        configuration = configuration,
        deviceUuid = "device-uuid",
        event = event,
        parent = this,
    )
}
