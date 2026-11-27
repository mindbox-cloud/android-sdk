package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.PushDeliveryWorkManager
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PushDeliveryWorkerDelegateTest {

    private companion object {
        const val ID = 7L
        const val RECEIVED_AT = 1_000_000_000L
        const val HALF_YEAR = 15_552_000_000L
    }

    private val context: Context = mockk(relaxed = true)
    private val scheduler = TestCoroutineScheduler()
    private val pushDeliveredManager: PushDeliveredManager = mockk()
    private val pauseManager = PushDeliveryPauseManager(
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
    )
    private val queueManager = PushDeliveryQueueManager()
    private val rows = linkedMapOf<Long, PushDelivery>()
    private val results = ArrayDeque<AsyncEventSendResult>()
    private val attemptOffsets = mutableListOf<Long>()
    private val sentDeliveries = mutableListOf<PushDelivery>()
    private val queueRuns = mutableListOf<Pair<Long, Int>>()
    private val nextQueueRuns = mutableListOf<Triple<Long, Int, Int>>()
    private var elapsedAtStart = 0L
    private var attemptDuration = 0L
    private var isStorageBroken = false
    private var areComponentsInitialized = false
    private val originalSdk = Build.VERSION.SDK_INT
    private var configuration: Configuration? = Configuration(
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

    private val elapsed: Long
        get() = elapsedAtStart + scheduler.currentTime

    private val now: Long
        get() = RECEIVED_AT + elapsed

    @Before
    fun setUp() {
        setSdk(Build.VERSION_CODES.S)
        mockLogger()
        mockkObject(Mindbox)
        every { Mindbox.initComponents(any()) } answers { areComponentsInitialized = true }
        mockkObject(InitializeLock)
        coEvery { InitializeLock.await(any()) } just runs
        val timeProvider: SystemTimeProvider = mockk {
            every { currentTimestamp() } answers { Timestamp(now) }
        }
        val appModule: AppModule = mockk {
            every { this@mockk.timeProvider } returns timeProvider
            every { pushDeliveredManager } returns this@PushDeliveryWorkerDelegateTest.pushDeliveredManager
            every { pushDeliveryPauseManager } returns pauseManager
            every { pushDeliveryQueueManager } returns queueManager
        }
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } answers {
            if (!areComponentsInitialized) throw UninitializedPropertyAccessException("appModule")
            appModule
        }
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } answers { configuration }
        every { DbManager.getPushDelivery(any()) } answers { rows[firstArg()] }
        every { DbManager.getQueuedPushDeliveries() } answers {
            rows.values.filter { it.isInQueue }.sortedWith(compareBy({ it.attempts }, { it.id }))
        }
        every { DbManager.removePushDelivery(any()) } answers {
            if (isStorageBroken) false else true.also { rows.remove(firstArg<Long>()) }
        }
        every { DbManager.updatePushDelivery(any()) } answers {
            val updated = firstArg<PushDelivery>()
            when {
                isStorageBroken -> null
                rows.containsKey(updated.id) -> 1.also { rows[updated.id] = updated }
                else -> 0
            }
        }
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.hostAppName } returns "test-app"
        mockkObject(PushDeliveryWorkManager)
        every { PushDeliveryWorkManager.enqueueQueue(any(), any(), any()) } answers { queueRuns += secondArg<Long>() to thirdArg<Int>() }
        every { PushDeliveryWorkManager.enqueueNextQueue(any(), any(), any(), any()) } answers {
            nextQueueRuns += Triple(secondArg<Long>(), thirdArg<Int>(), arg<Int>(3))
        }
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            attemptOffsets.add(elapsed)
            sentDeliveries.add(secondArg())
            delay(attemptDuration)
            results.removeFirst()
        }
    }

    @After
    fun tearDown() {
        setSdk(originalSdk)
        unmockkAll()
    }

    private fun setSdk(sdkInt: Int) = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdkInt)

    private fun row(
        id: Long = ID,
        attempts: Int = 0,
        provider: String? = null,
        isInQueue: Boolean = false,
        receivedAfter: Long = 0L,
    ) = PushDelivery(
        id = id,
        url = "https://mbx.ru/d/$id",
        receivedAt = RECEIVED_AT + receivedAfter,
        attempts = attempts,
        provider = provider,
        isInQueue = isInQueue,
    ).also { rows[id] = it }

    private fun answers(vararg result: AsyncEventSendResult) {
        results.addAll(result)
    }

    private fun failures(count: Int) = answers(*Array(count) { AsyncEventSendResult.RETRY_LATER })

    private fun newScope() = TestScope(StandardTestDispatcher(scheduler))

    private fun run(startedAfter: Long = 0L, id: Long = ID) = newScope().runTest {
        elapsedAtStart = startedAfter - scheduler.currentTime
        PushDeliveryWorkerDelegate().send(context, id)
    }

    private fun runQueue(throttles: Int = 0, failures: Int = 0) = newScope().runTest {
        PushDeliveryWorkerDelegate().sendQueue(context, throttles, failures)
    }

    @Test
    fun `three failed attempts start at 0, 3 and 6 seconds and the row is handed to the queue that starts in 10 seconds`() {
        row()
        failures(3)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
        assertEquals(3, rows.getValue(ID).attempts)
        assertTrue(rows.getValue(ID).isInQueue)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `attempt that takes its whole 2500 ms does not move the next one`() {
        row()
        failures(3)
        attemptDuration = 2_500L

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
    }

    @Test
    fun `HMS attempt that takes its whole 2500 ms moves the next one to its end`() {
        row(provider = "HCM")
        failures(3)
        attemptDuration = 2_500L

        run()

        assertEquals(listOf(0L, 2_500L, 5_000L), attemptOffsets)
    }

    @Test
    fun `task that started late keeps 3 seconds between attempt starts`() {
        row()
        failures(3)

        run(startedAfter = 1_000L)

        assertEquals(listOf(1_000L, 4_000L, 7_000L), attemptOffsets)
    }

    @Test
    fun `throttled and failed attempts do not stop the attempts of the task`() {
        row()
        answers(AsyncEventSendResult.THROTTLED, AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.RETRY_LATER)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
        assertEquals(3, rows.getValue(ID).attempts)
    }

    @Test
    fun `task whose last attempt got 429 hands the row to the queue that waits the first 429 pause`() {
        row()
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.THROTTLED, AsyncEventSendResult.THROTTLED)

        run()

        assertTrue(rows.getValue(ID).isInQueue)
        assertEquals(listOf(150_000L to 1), queueRuns)
    }

    @Test
    fun `429 followed by another failure in the task hands the row to the queue without the 429 pause`() {
        row()
        answers(AsyncEventSendResult.THROTTLED, AsyncEventSendResult.THROTTLED, AsyncEventSendResult.RETRY_LATER)

        run()

        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `accepted or rejected confirmation is deleted with no more attempts and no queue`() {
        listOf(AsyncEventSendResult.DELIVERED, AsyncEventSendResult.DISCARDED).forEach { result ->
            attemptOffsets.clear()
            row()
            answers(result)

            run()

            assertEquals("$result", listOf(0L), attemptOffsets)
            assertNull("$result", rows[ID])
        }
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `confirmation accepted on the third attempt is deleted and is not handed to the queue`() {
        row()
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.DELIVERED)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
        assertNull(rows[ID])
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `FCM push on Android 12 gets the fourth attempt at 15 seconds in the same task and is handed to the queue`() {
        row(provider = "FCM")
        failures(4)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L, 15_000L), attemptOffsets)
        assertEquals(4, rows.getValue(ID).attempts)
        assertTrue(rows.getValue(ID).isInQueue)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `FCM push accepted on the fourth attempt is deleted`() {
        row(provider = "FCM")
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.DELIVERED)

        run()

        assertEquals(4, attemptOffsets.size)
        assertNull(rows[ID])
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `FCM task that started a little late still makes the fourth attempt at 15 seconds from the receipt`() {
        row(provider = "FCM")
        failures(4)

        run(startedAfter = 1_000L)

        assertEquals(listOf(1_000L, 4_000L, 7_000L, 15_000L), attemptOffsets)
    }

    @Test
    fun `FCM task that started late keeps 3 seconds between all four attempts`() {
        row(provider = "FCM")
        failures(4)

        run(startedAfter = 10_000L)

        assertEquals(listOf(10_000L, 13_000L, 16_000L, 19_000L), attemptOffsets)
    }

    @Test
    fun `FCM push below Android 12 gets three attempts and no attempt at 15 seconds`() {
        setSdk(Build.VERSION_CODES.R)
        row(provider = "FCM")
        failures(3)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
    }

    @Test
    fun `push of an unknown provider gets the three regular attempts`() {
        row()
        failures(3)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
    }

    @Test
    fun `HMS push gets its attempts at 0, 2 and 4 seconds and is handed to the queue`() {
        row(provider = "HCM")
        failures(3)

        run()

        assertEquals(listOf(0L, 2_000L, 4_000L), attemptOffsets)
        assertTrue(rows.getValue(ID).isInQueue)
    }

    @Test
    fun `HMS task that started late keeps 2 seconds between attempt starts`() {
        row(provider = "HCM")
        failures(3)

        run(startedAfter = 1_000L)

        assertEquals(listOf(1_000L, 3_000L, 5_000L), attemptOffsets)
    }

    @Test
    fun `HMS task restarted 6 seconds after the receipt is already out of its window and makes one attempt`() {
        row(attempts = 1, provider = "HCM")
        failures(1)

        run(startedAfter = 6_001L)

        assertEquals(listOf(6_001L), attemptOffsets)
    }

    @Test
    fun `every attempt is sent with the receipt time and the current counters of the row`() {
        row()
        failures(3)

        run()

        assertEquals(listOf(0, 1, 2), sentDeliveries.map { it.attempts })
        assertTrue(sentDeliveries.all { it.receivedAt == RECEIVED_AT && it.id == ID })
    }

    @Test
    fun `task restarted inside the window continues from the counter in the row`() {
        row(attempts = 1)
        failures(2)

        run(startedAfter = 4_000L)

        assertEquals(listOf(4_000L, 7_000L), attemptOffsets)
        assertEquals(3, rows.getValue(ID).attempts)
    }

    @Test
    fun `FCM task restarted after the third attempt waits for the time of the fourth`() {
        row(attempts = 3, provider = "FCM")
        failures(1)

        run(startedAfter = 9_000L)

        assertEquals(listOf(15_000L), attemptOffsets)
    }

    @Test
    fun `FCM task restarted after the fourth attempt makes one attempt at once and hands the row to the queue`() {
        row(attempts = 4, provider = "FCM")
        failures(1)

        run(startedAfter = 16_000L)

        assertEquals(listOf(16_000L), attemptOffsets)
        assertEquals(5, rows.getValue(ID).attempts)
        assertTrue(rows.getValue(ID).isInQueue)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `task restarted after the window makes one attempt at once and hands the row to the queue`() {
        row(attempts = 1)
        failures(1)

        run(startedAfter = 600_000L)

        assertEquals(listOf(600_000L), attemptOffsets)
        assertEquals(2, rows.getValue(ID).attempts)
        assertTrue(rows.getValue(ID).isInQueue)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `task restarted after the window delivers the confirmation with one attempt`() {
        row(attempts = 1)
        answers(AsyncEventSendResult.DELIVERED)

        run(startedAfter = 600_000L)

        assertNull(rows[ID])
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `task restarted after its row was handed to the queue sends nothing and makes sure the queue is scheduled`() {
        row(attempts = 3, isInQueue = true)

        run(startedAfter = 10_000L)

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertEquals(3, rows.getValue(ID).attempts)
        assertEquals(listOf(0L to 0), queueRuns)
    }

    @Test
    fun `worker stopped between attempts keeps the row out of the queue and schedules nothing`() = newScope().runTest {
        row()
        failures(3)

        val job = launch { PushDeliveryWorkerDelegate().send(context, ID) }
        advanceTimeBy(4_000L)
        runCurrent()
        job.cancel()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(listOf(0L, 3_000L), attemptOffsets)
        assertEquals(2, rows.getValue(ID).attempts)
        assertFalse(rows.getValue(ID).isInQueue)
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `worker stopped in the middle of an attempt keeps the row as it was`() = newScope().runTest {
        row()
        failures(3)
        attemptDuration = 2_000L

        val job = launch { PushDeliveryWorkerDelegate().send(context, ID) }
        advanceTimeBy(1_000L)
        runCurrent()
        job.cancel()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(listOf(0L), attemptOffsets)
        assertEquals(0, rows.getValue(ID).attempts)
        assertFalse(rows.getValue(ID).isInQueue)
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `row removed during an attempt is not written back and is not handed to the queue`() {
        row()
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            attemptOffsets.add(elapsed)
            rows.clear()
            AsyncEventSendResult.RETRY_LATER
        }

        run()

        assertEquals(listOf(0L), attemptOffsets)
        assertTrue(rows.isEmpty())
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `row removed during the last attempt of the task is not handed to the queue`() {
        row(attempts = 2)
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            rows.clear()
            AsyncEventSendResult.RETRY_LATER
        }

        run(startedAfter = 6_000L)

        assertTrue(rows.isEmpty())
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `row that is not in the table ends the task without a request`() {
        run()

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `row older than half a year is deleted without a request`() {
        row()

        run(startedAfter = HALF_YEAR)

        assertNull(rows[ID])
        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
    }

    @Test
    fun `row one second younger than half a year is still sent`() {
        row(attempts = 9)
        answers(AsyncEventSendResult.DELIVERED)

        run(startedAfter = HALF_YEAR - 1_000L)

        assertEquals(1, attemptOffsets.size)
        assertNull(rows[ID])
    }

    @Test
    fun `without a saved configuration the row waits for the SDK start`() {
        row()
        configuration = null

        run()

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertEquals(0, rows.getValue(ID).attempts)
        assertFalse(rows.getValue(ID).isInQueue)
        assertTrue(queueRuns.isEmpty())
    }

    @Test
    fun `task that starts with the clock behind the receipt time makes its attempts from its own start`() {
        row(provider = "FCM")
        failures(4)

        run(startedAfter = -3_600_000L)

        assertEquals(listOf(0L, 3_000L, 6_000L, 15_000L), attemptOffsets.map { it + 3_600_000L })
    }

    @Test
    fun `clock moved back in the middle of the task holds an attempt no longer than the wait limit`() {
        row()
        failures(3)
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            attemptOffsets.add(scheduler.currentTime)
            if (attemptOffsets.size == 1) elapsedAtStart -= 3_600_000L
            results.removeFirst()
        }

        run()

        assertEquals(listOf(0L, 30_000L, 60_000L), attemptOffsets)
    }

    @Test
    fun `row with a broken negative counter still gets the attempts of its schedule`() {
        row(attempts = -2)
        failures(3)

        run()

        assertEquals(listOf(0L, 3_000L, 6_000L), attemptOffsets)
    }

    @Test
    fun `queue sends only the rows handed to it, oldest first, deletes accepted and rejected ones and ends`() {
        row(id = 1L, isInQueue = true)
        row(id = 2L, isInQueue = true)
        row(id = 3L)
        answers(AsyncEventSendResult.DELIVERED, AsyncEventSendResult.DISCARDED)

        runQueue()

        assertEquals(listOf(1L, 2L), sentDeliveries.map { it.id })
        assertEquals(listOf(3L), rows.keys.toList())
        assertTrue(nextQueueRuns.isEmpty())
    }

    @Test
    fun `queue keeps a row that got no answer, still sends the rest and runs again after the first retry pause`() {
        row(id = 1L, isInQueue = true)
        row(id = 2L, isInQueue = true)
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.DELIVERED)

        runQueue()

        assertEquals(listOf(1L, 2L), sentDeliveries.map { it.id })
        assertEquals(listOf(1L), rows.keys.toList())
        assertEquals(listOf(Triple(7_500L, 0, 1)), nextQueueRuns)
    }

    @Test
    fun `failed run doubles the retry pause and starts the 429 pauses over`() {
        row(isInQueue = true)
        failures(1)

        runQueue(throttles = 2, failures = 2)

        assertEquals(listOf(Triple(30_000L, 0, 3)), nextQueueRuns)
    }

    @Test
    fun `429 after a failed run that followed 429 waits the first 429 pause again`() {
        row(isInQueue = true)
        answers(AsyncEventSendResult.THROTTLED, AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.THROTTLED)

        runQueue()
        runQueue(throttles = nextQueueRuns[0].second, failures = nextQueueRuns[0].third)
        runQueue(throttles = nextQueueRuns[1].second, failures = nextQueueRuns[1].third)

        assertEquals(
            listOf(Triple(150_000L, 1, 0), Triple(7_500L, 0, 1), Triple(150_000L, 1, 0)),
            nextQueueRuns,
        )
    }

    @Test
    fun `first 429 stops the run, the next run waits the first 429 pause and the retry pauses start over`() {
        row(id = 1L, isInQueue = true)
        row(id = 2L, isInQueue = true)
        answers(AsyncEventSendResult.THROTTLED)

        runQueue(failures = 3)

        assertEquals(listOf(1L), sentDeliveries.map { it.id })
        assertEquals(listOf(1L, 2L), rows.keys.toList())
        assertEquals(listOf(Triple(150_000L, 1, 0)), nextQueueRuns)
    }

    @Test
    fun `failed row followed by 429 in one run waits the first 429 pause and starts the retry pauses over`() {
        row(id = 1L, isInQueue = true)
        row(id = 2L, isInQueue = true)
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.THROTTLED)

        runQueue(throttles = 1, failures = 2)

        assertEquals(listOf(Triple(150_000L, 1, 0)), nextQueueRuns)
    }

    @Test
    fun `accepted row followed by 429 in one run makes the next 429 pause longer`() {
        row(id = 1L, isInQueue = true)
        row(id = 2L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED, AsyncEventSendResult.THROTTLED)

        runQueue(throttles = 1)

        assertEquals(listOf(Triple(300_000L, 2, 0)), nextQueueRuns)
    }

    @Test
    fun `queue run stopped after its last request schedules no next run`() = newScope().runTest {
        row(isInQueue = true)
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            currentCoroutineContext().cancel()
            AsyncEventSendResult.RETRY_LATER
        }

        val job = launch { PushDeliveryWorkerDelegate().sendQueue(context, throttles = 0, failures = 0) }
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(rows.getValue(ID).isInQueue)
        assertTrue(nextQueueRuns.isEmpty())
    }

    @Test
    fun `repeated 429 makes the next pause longer`() {
        row(isInQueue = true)
        answers(AsyncEventSendResult.THROTTLED)

        runQueue(throttles = 2)

        assertEquals(listOf(Triple(600_000L, 3, 0)), nextQueueRuns)
    }

    @Test
    fun `queue sends no more than 20 rows in one run and starts the next run at once with the counts reset`() {
        (1L..25L).forEach { row(id = it, isInQueue = true) }
        answers(*Array(25) { AsyncEventSendResult.DELIVERED })

        runQueue(throttles = 2, failures = 2)

        assertEquals((1L..20L).toList(), sentDeliveries.map { it.id })
        assertEquals((21L..25L).toList(), rows.keys.toList())
        assertEquals(listOf(Triple(0L, 0, 0)), nextQueueRuns)
    }

    @Test
    fun `queue with failures sends no more than 20 rows and leaves the rest to the next run`() {
        (1L..25L).forEach { row(id = it, isInQueue = true) }
        failures(25)

        runQueue()

        assertEquals((1L..20L).toList(), sentDeliveries.map { it.id })
        assertEquals((1L..25L).toList(), rows.keys.toList())
        assertEquals(listOf(Triple(7_500L, 0, 1)), nextQueueRuns)
    }

    @Test
    fun `rows that keep failing in the queue go after the other rows in the next run`() {
        (1L..25L).forEach { row(id = it, isInQueue = true) }
        failures(40)

        runQueue()
        runQueue(failures = 1)

        assertEquals((21L..25L) + (1L..15L), sentDeliveries.drop(20).map { it.id })
        assertEquals(List(15) { 2 } + List(10) { 1 }, rows.values.map { it.attempts })
    }

    @Test
    fun `row that got 429 in the queue keeps its attempt count`() {
        row(isInQueue = true, attempts = 3)
        answers(AsyncEventSendResult.THROTTLED)

        runQueue()

        assertEquals(3, rows.getValue(ID).attempts)
    }

    @Test
    fun `row handed to the queue after the run found nothing left gets the next run after the ending one`() {
        row(id = 1L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        runQueue()
        row()
        failures(3)

        run()

        assertTrue(queueRuns.isEmpty())
        assertEquals(listOf(Triple(10_000L, 0, 0)), nextQueueRuns)
    }

    @Test
    fun `row handed to the queue after 429 right after the run found nothing left follows that run after the 429 pause`() {
        row(id = 1L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        runQueue()
        row()
        answers(AsyncEventSendResult.RETRY_LATER, AsyncEventSendResult.THROTTLED, AsyncEventSendResult.THROTTLED)

        run()

        assertTrue(queueRuns.isEmpty())
        assertEquals(listOf(Triple(150_000L, 1, 0)), nextQueueRuns)
    }

    @Test
    fun `row handed over after a run that left rows for the next run waits for that next run`() {
        (1L..21L).forEach { row(id = it, isInQueue = true) }
        answers(*Array(20) { AsyncEventSendResult.DELIVERED })
        runQueue()
        row()
        failures(3)

        run()

        assertEquals(listOf(Triple(0L, 0, 0)), nextQueueRuns)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `row handed over after the queue run was stopped on its last answer waits for the restarted run`() {
        row(id = 1L, isInQueue = true)
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            currentCoroutineContext().cancel()
            AsyncEventSendResult.DELIVERED
        }
        newScope().runTest {
            val job = launch { PushDeliveryWorkerDelegate().sendQueue(context, throttles = 0, failures = 0) }
            runCurrent()
            assertTrue(job.isCancelled)
        }
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers { results.removeFirst() }
        row()
        failures(3)

        run()

        assertTrue(nextQueueRuns.isEmpty())
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `row is handed to the queue and its queue run is asked for under the lock that ends a run`() {
        val isLockHeld = mutableListOf<Boolean>()
        every { PushDeliveryWorkManager.enqueueQueue(any(), any(), any()) } answers { isLockHeld += Thread.holdsLock(queueManager) }
        every { PushDeliveryWorkManager.enqueueNextQueue(any(), any(), any(), any()) } answers { isLockHeld += Thread.holdsLock(queueManager) }
        row(id = 1L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        runQueue()
        row(id = 2L)
        row(id = 3L)
        failures(6)

        run(id = 2L)
        run(id = 3L)

        assertEquals(listOf(true, true), isLockHeld)
    }

    @Test
    fun `only the first row handed over after the run ended gets a run of its own, the next one joins it`() {
        row(id = 1L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        runQueue()
        row(id = 2L)
        row(id = 3L)
        failures(6)

        run(id = 2L)
        run(id = 3L)

        assertEquals(listOf(Triple(10_000L, 0, 0)), nextQueueRuns)
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `row handed over after a failed queue run waits for the retry that run scheduled`() {
        row(id = 1L, isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        runQueue()
        row(id = 2L, isInQueue = true)
        failures(1)
        runQueue()
        nextQueueRuns.clear()
        row()
        failures(3)

        run()

        assertTrue(nextQueueRuns.isEmpty())
        assertEquals(listOf(10_000L to 0), queueRuns)
    }

    @Test
    fun `row handed to the queue during the run gets the next run`() {
        row(id = 1L, isInQueue = true)
        coEvery { pushDeliveredManager.send(any(), any()) } coAnswers {
            sentDeliveries.add(secondArg())
            row(id = 2L, isInQueue = true)
            AsyncEventSendResult.DELIVERED
        }

        runQueue()

        assertEquals(listOf(1L), sentDeliveries.map { it.id })
        assertEquals(listOf(Triple(0L, 0, 0)), nextQueueRuns)
    }

    @Test
    fun `queue drops a row older than half a year without a request`() {
        row(isInQueue = true, receivedAfter = -HALF_YEAR)

        runQueue()

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `queue that cannot delete an answered row runs again after the first retry pause`() {
        row(isInQueue = true)
        answers(AsyncEventSendResult.DELIVERED)
        isStorageBroken = true

        runQueue()
        assertEquals(listOf(Triple(7_500L, 0, 1)), nextQueueRuns)
    }

    @Test
    fun `queue without a saved configuration sends nothing and waits for the SDK start`() {
        row(isInQueue = true)
        configuration = null

        runQueue()

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertTrue(nextQueueRuns.isEmpty())
    }

    @Test
    fun `queue with nothing handed to it ends without a next run`() {
        row()

        runQueue()

        coVerify(exactly = 0) { pushDeliveredManager.send(any(), any()) }
        assertTrue(nextQueueRuns.isEmpty())
    }
}
