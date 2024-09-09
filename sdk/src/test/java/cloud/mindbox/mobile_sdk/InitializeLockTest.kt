package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.utils.AwaitSync
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InitializeLockTest {
    private val awaitSync = AwaitSync()

    @Before
    fun setUp() {
        InitializeLock.reset(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.reset(InitializeLock.State.APP_STARTED)
    }

    @Test
    fun `test complete and await SAVE_MINDBOX_CONFIG`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }

        awaitSync.waitForAwait()
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test complete and await APP_STARTED`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.APP_STARTED)
        }
        awaitSync.waitForAwait()
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.APP_STARTED)
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test reset and await APP_STARTED`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.APP_STARTED)

        }
        awaitSync.waitForAwait()
        assertTrue(job.isActive)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.reset(InitializeLock.State.APP_STARTED)
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test coroutine doesnt complete without change state`() = runBlocking {
        val result = try {
            withTimeout(3_000) {
                InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }
        assertFalse(result)
    }

    @Test
    fun `test coroutine doesnt complete without change state for APP_STARTED`() = runBlocking {
        val result = try {
            InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            withTimeout(3_000) {
                InitializeLock.await(InitializeLock.State.APP_STARTED)
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }
        assertFalse(result)
    }

    @Test
    fun `test coroutine complete when reset status SAVE_MINDBOX_CONFIG`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        awaitSync.waitForAwait()
        assertTrue(job.isActive)
        InitializeLock.reset(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `test multiple completes for the same state`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        awaitSync.waitForAwait()
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)

        job.join()
        assertTrue(job.isCompleted)
    }

    @Test
    fun `test coroutine is cancelled before state completion`() = runBlocking {
        val job = launch {
            awaitSync.signalAwait()
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }
        awaitSync.waitForAwait()
        job.cancel()
        assertTrue(job.isCancelled)

        InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `test coroutine when states already completed`() = runBlocking {

        launch {
            InitializeLock.complete(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            awaitSync.signalAwait()
        }
        val job = launch {
            awaitSync.waitForAwait()
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
        }

        job.join()
        assertTrue(job.isCompleted)
    }
}