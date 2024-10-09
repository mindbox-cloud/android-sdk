package cloud.mindbox.mobile_sdk.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendLazyKtTest {

    @Test
    fun `check suspend lazy property not changed`() = runTest {
        var value = "42"
        val lazy: SuspendLazy<String> = suspendLazy {
            delay(100)
            value
        }

        assertEquals("42", lazy.invoke())
        value = "not 42"
        assertEquals("42", lazy.invoke())
    }

    @Test
    fun `check suspend lazy property called ones`() = runTest {
        val mockk: TestInterface = mockk {
            every { call() } returns "test"
        }
        val lazy: SuspendLazy<String> = suspendLazy { mockk.call() }

        repeat(5) {
            lazy.invoke()
        }

        assertEquals("test", lazy.invoke())
        verify(exactly = 1) { mockk.call() }
    }

    private interface TestInterface {
        fun call(): String
    }
}
