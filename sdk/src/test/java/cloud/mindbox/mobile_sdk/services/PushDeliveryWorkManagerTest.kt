package cloud.mindbox.mobile_sdk.services

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PushDeliveryWorkManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val requests = mutableListOf<OneTimeWorkRequest>()
    private val policies = mutableListOf<ExistingWorkPolicy>()
    private val names = mutableListOf<String>()
    private val continuations = mutableListOf<WorkContinuation>()
    private lateinit var workManager: WorkManager
    private val originalSdk = Build.VERSION.SDK_INT
    private var queued = emptyList<PushDelivery>()

    @Before
    fun setUp() {
        mockLogger()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.hostAppName } returns "test-app"
        mockkObject(DbManager)
        every { DbManager.getQueuedPushDeliveries() } answers { queued }
        workManager = mockk(relaxed = true) {
            every { beginUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
                names.add(firstArg())
                policies.add(secondArg())
                requests.add(thirdArg())
                mockk<WorkContinuation>(relaxed = true).also(continuations::add)
            }
        }
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdk)
        unmockkAll()
    }

    @Test
    fun `task for a received push is expedited on Android 12 and falls back to a regular one without quota`() {
        PushDeliveryWorkManager.enqueue(context, 7L)

        val workSpec = requests.single().workSpec
        assertTrue(workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, workSpec.outOfQuotaPolicy)
        assertEquals(0L, workSpec.initialDelay)
    }

    @Test
    fun `task for a received push is a regular one below Android 12`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)

        PushDeliveryWorkManager.enqueue(context, 7L)

        val workSpec = requests.single().workSpec
        assertFalse(workSpec.expedited)
        assertEquals(0L, workSpec.initialDelay)
    }

    @Test
    fun `task for a received push needs a connection, carries the row id and restarts after 10 seconds`() {
        PushDeliveryWorkManager.enqueue(context, 7L)

        val request = requests.single()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(MindboxPushDeliveryWorker.inputData(7L), request.workSpec.input)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(10_000L, request.workSpec.backoffDelayDuration)
        assertEquals(MindboxPushDeliveryWorker::class.java.name, request.workSpec.workerClassName)
        verify(exactly = 1) { continuations.single().enqueue() }
    }

    @Test
    fun `each row has its own task name and tasks of received pushes are enqueued with the keep policy`() {
        PushDeliveryWorkManager.enqueue(context, 7L)
        PushDeliveryWorkManager.enqueue(context, 8L)
        PushDeliveryWorkManager.enqueue(context, 7L)

        assertEquals(names[0], names[2])
        assertTrue(names[0] != names[1])
        assertEquals(List(3) { ExistingWorkPolicy.KEEP }, policies)
    }

    @Test
    fun `queue is one regular task without a row id that waits its delay, carries the 429 and failure counts and backs off from 10 seconds`() {
        PushDeliveryWorkManager.enqueueNextQueue(context, Milliseconds(150_000L), throttles = 1, failures = 2)

        val workSpec = requests.single().workSpec
        assertFalse(workSpec.expedited)
        assertEquals(150_000L, workSpec.initialDelay)
        assertEquals(MindboxPushDeliveryWorker.queueInputData(throttles = 1, failures = 2), workSpec.input)
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(10_000L, workSpec.backoffDelayDuration)
        verify(exactly = 1) { continuations.single().enqueue() }
    }

    @Test
    fun `row handed to the queue keeps the queue task and the queue appends its next run under the same name`() {
        PushDeliveryWorkManager.enqueueQueue(context, Milliseconds(150_000L), throttles = 1)
        PushDeliveryWorkManager.enqueueNextQueue(context, Milliseconds(0L), throttles = 0, failures = 0)
        PushDeliveryWorkManager.enqueue(context, 7L)

        assertEquals(listOf(ExistingWorkPolicy.KEEP, ExistingWorkPolicy.APPEND_OR_REPLACE), policies.take(2))
        assertEquals(names[0], names[1])
        assertTrue(names[0] != names[2])
        assertEquals(150_000L, requests[0].workSpec.initialDelay)
        assertEquals(MindboxPushDeliveryWorker.queueInputData(throttles = 1, failures = 0), requests[0].workSpec.input)
        assertEquals(0L, requests[1].workSpec.initialDelay)
        assertEquals(MindboxPushDeliveryWorker.queueInputData(throttles = 0, failures = 0), requests[1].workSpec.input)
    }

    @Test
    fun `SDK start gives rows not yet handed to the queue their own task and starts the queue when rows wait in it`() {
        every { DbManager.getPushDeliveryIdsOutOfQueue() } returns listOf(7L)
        queued = listOf(PushDelivery(id = 8L, url = "https://mbx.ru/d/8", receivedAt = 1L, isInQueue = true))
        PushDeliveryWorkManager.enqueue(context, 7L)

        PushDeliveryWorkManager.enqueueAll(context)

        assertEquals(3, requests.size)
        assertEquals(names[0], names[1])
        assertEquals(requests[0].workSpec.input, requests[1].workSpec.input)
        assertTrue(requests[1].workSpec.expedited)
        assertEquals(MindboxPushDeliveryWorker.queueInputData(throttles = 0, failures = 0), requests[2].workSpec.input)
        assertEquals(0L, requests[2].workSpec.initialDelay)
        assertEquals(List(3) { ExistingWorkPolicy.KEEP }, policies)
    }

    @Test
    fun `SDK start with no stored rows enqueues nothing`() {
        every { DbManager.getPushDeliveryIdsOutOfQueue() } returns emptyList()

        PushDeliveryWorkManager.enqueueAll(context)

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `row tasks and the queue share one tag and are cancelled by it`() {
        PushDeliveryWorkManager.enqueue(context, 7L)
        PushDeliveryWorkManager.enqueueQueue(context)
        val tag = requests.map { it.tags }.reduce { common, tags -> common intersect tags }
            .single { it != MindboxPushDeliveryWorker::class.java.name }

        PushDeliveryWorkManager.cancelAllWork(context)

        verify(exactly = 1) { workManager.cancelAllWorkByTag(tag) }
        assertTrue(names.all { it.startsWith(tag) })
    }
}
