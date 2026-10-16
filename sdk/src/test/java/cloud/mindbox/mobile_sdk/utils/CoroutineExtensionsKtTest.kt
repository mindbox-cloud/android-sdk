package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.Mindbox

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CoroutineExtensionsKtTest {

    private suspend fun delayedOperation(timeMillis: Long): String {
        delay(timeMillis.milliseconds)
        return timeMillis.toString()
    }

    @Test
    fun `awaitAllWithTimeout two of three jobs`() = runTest {
        val jobs = listOf(500L, 1000L, 2000L)
            .map { async { delayedOperation(it) } }

        val results: List<String> = jobs.awaitAllWithTimeout(1500)
        assertEquals(listOf("500", "1000"), results)
    }

    @Test
    fun `awaitAllWithTimeout zero of three jobs`() = runTest {
        val jobs = listOf(500L, 1000L, 2000L).map { async { delayedOperation(it) } }

        val results: List<String> = jobs.awaitAllWithTimeout(100)
        assertEquals(listOf<String>(), results)
    }

    @Test
    fun `awaitAllWithTimeout three of three jobs`() = runTest {
        val jobs = listOf(500L, 1000L, 2000L).map { async { delayedOperation(it) } }

        val results: List<String> = jobs.awaitAllWithTimeout(5000)
        assertEquals(listOf("500", "1000", "2000"), results)
    }

    @Test
    fun `awaitAllWithTimeout empty list`() = runTest {
        val jobs = listOf<Deferred<String>>()

        val results: List<String> = jobs.awaitAllWithTimeout(1000)
        assertEquals(listOf<String>(), results)
    }

    @Test
    fun `awaitAllWithTimeout for thread sleep`() = runTest {
        val jobs = listOf(
            async(Dispatchers.Default) {
                Thread.sleep(100)
                "1000"
            },
        )

        val results: List<String> = jobs.awaitAllWithTimeout(10)
        assertEquals(listOf<String>(), results)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `awaitAllWithTimeout cancels unfinished jobs`() = runTest {
        val jobs = listOf(100L, 180_000L).map { async { delayedOperation(it) } }

        val results: List<String> = jobs.awaitAllWithTimeout(1_000)

        assertEquals(listOf("100"), results)
        assertTrue(jobs.last().isCancelled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `awaitAllWithTimeout does not delay parent completion when a job hangs`() = runTest {
        val started = testScheduler.currentTime

        coroutineScope {
            val jobs = listOf(100L, 180_000L).map { async { delayedOperation(it) } }
            jobs.awaitAllWithTimeout(1_000)
        }

        assertEquals(1_000L, testScheduler.currentTime - started)
    }

    @Test
    fun `launchWithLock should execute block`() = runTest {
        val mutex = Mutex()
        var blockExecuted = false

        val job = launchWithLock(mutex) {
            blockExecuted = true
        }
        job.join()

        assertTrue(blockExecuted)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `launchWithLock should provide mutual exclusion`() = runTest {
        val mutex = Mutex()
        val executionLog = mutableListOf<String>()
        val scope = this

        scope.launchWithLock(mutex) {
            executionLog.add("Job 1 Start")
            delay(100.milliseconds)
            executionLog.add("Job 1 End")
        }
        scope.launchWithLock(mutex) {
            executionLog.add("Job 2 Start")
            delay(100.milliseconds)
            executionLog.add("Job 2 End")
        }

        advanceUntilIdle()

        val expectedLog = listOf("Job 1 Start", "Job 1 End", "Job 2 Start", "Job 2 End")
        assertEquals(expectedLog, executionLog)
    }

    @Test
    fun `launchWithLock should unlock mutex on exception`() = runTest {
        val mutex = Mutex()
        val scope = CoroutineScope(SupervisorJob() + Mindbox.coroutineExceptionHandler)
        val errorMessage = "Test exception"

        val job1 = scope.launchWithLock(mutex) {
            throw RuntimeException(errorMessage)
        }
        var job2Finished = false
        val job2 = scope.launchWithLock(mutex) {
            job2Finished = true
        }
        job1.join()
        job2.join()

        assertTrue(job2Finished)
    }
}
