package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackgroundWorkManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val requests = mutableListOf<OneTimeWorkRequest>()
    private val policies = mutableListOf<ExistingWorkPolicy>()
    private val names = mutableListOf<String>()
    private val continuations = mutableListOf<WorkContinuation>()

    @Before
    fun setUp() {
        mockLogger()
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.hostAppName } returns "test-app"
        val workManager: WorkManager = mockk {
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
        unmockkAll()
    }

    @Test
    fun `event worker starts with a 120 second delay`() {
        BackgroundWorkManager.startOneTimeService(context)

        assertEquals(120_000L, requests.single().workSpec.initialDelay)
    }

    @Test
    fun `event worker retries exponentially from a 180 second base`() {
        BackgroundWorkManager.startOneTimeService(context)

        val workSpec = requests.single().workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(180_000L, workSpec.backoffDelayDuration)
    }

    @Test
    fun `next run is enqueued with append policy under the unique name and tag of the event worker`() {
        BackgroundWorkManager.startOneTimeService(context)
        BackgroundWorkManager.startNextOneTimeService(context)

        assertEquals(listOf(ExistingWorkPolicy.KEEP, ExistingWorkPolicy.APPEND_OR_REPLACE), policies)
        assertEquals(names[0], names[1])
        requests.forEach { assertTrue("tags ${it.tags}", names[0] in it.tags) }
        continuations.forEach { verify(exactly = 1) { it.enqueue() } }
        val nextRun = requests.last().workSpec
        assertEquals(120_000L, nextRun.initialDelay)
        assertEquals(180_000L, nextRun.backoffDelayDuration)
    }
}
