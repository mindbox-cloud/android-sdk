package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoroutineExtensionsKtTest {

    private suspend fun delayedOperation(timeMillis: Long): String {
        delay(timeMillis)
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
}
