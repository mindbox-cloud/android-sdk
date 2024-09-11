package cloud.mindbox.mobile_sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitializeLockTest {

    @Before
    fun setUp() {
        InitializeLock.reset(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.reset(InitializeLock.State.APP_STARTED)
    }

    @Test
    fun `test complete and await SAVE_MINDBOX_CONFIG`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }

        advanceTimeBy(100)
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test complete and await APP_STARTED`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.APP_STARTED)
        }
        advanceTimeBy(100)
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.APP_STARTED)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test reset and await APP_STARTED`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.APP_STARTED)
        }
        advanceTimeBy(100)
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.reset(InitializeLock.State.APP_STARTED)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test coroutine doesnt complete without change state`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        advanceTimeBy(1000)
        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun `test coroutine doesnt complete without change state for APP_STARTED`() = runTest {
        val job = launch {
            InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            InitializeLock.await(InitializeLock.State.APP_STARTED)
        }
        advanceTimeBy(1000)

        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun `test coroutine complete when reset status SAVE_MINDBOX_CONFIG`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        advanceTimeBy(100)
        assertTrue(job.isActive)
        InitializeLock.reset(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test multiple completes for the same state`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        advanceTimeBy(100)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)

        advanceUntilIdle()
        assertTrue(job.isCompleted)
    }

    @Test
    fun `test coroutine is cancelled before state completion`() = runTest {
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        advanceTimeBy(100)
        job.cancel()
        assertTrue(job.isCancelled)

        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `test coroutine when states already completed`() = runTest {
        launch {
            InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        advanceTimeBy(100)
        val job = launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }

        job.join()
        assertTrue(job.isCompleted)
    }
}