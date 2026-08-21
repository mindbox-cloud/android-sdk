package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.util.Log
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class MindboxWebPageRegistryTest {

    private val registry = MindboxWebPageRegistry()

    private class RecordingPage : MindboxWebPage {
        val received = mutableListOf<Pair<WebViewAction, String>>()

        override fun push(action: WebViewAction, payload: String) {
            received.add(action to payload)
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        mockLogger()
        every { Log.isLoggable(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `broadcast reaches every page except the author`() {
        val author = RecordingPage()
        val listener = RecordingPage()
        val other = RecordingPage()
        registry.register(author)
        registry.register(listener)
        registry.register(other)

        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = author)

        assertEquals(0, author.received.size)
        assertEquals(listOf(WebViewAction.LOCAL_STATE_CHANGED to "{}"), listener.received)
        assertEquals(listOf(WebViewAction.LOCAL_STATE_CHANGED to "{}"), other.received)
    }

    @Test
    fun `registering the same page twice delivers each broadcast once`() {
        val page = RecordingPage()
        registry.register(page)
        registry.register(page)

        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = null)

        assertEquals(1, page.received.size)
    }

    @Test
    fun `an unregistered page no longer receives broadcasts`() {
        val leaving = RecordingPage()
        val staying = RecordingPage()
        registry.register(leaving)
        registry.register(staying)

        registry.unregister(leaving)
        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = null)

        assertEquals(0, leaving.received.size)
        assertEquals(1, staying.received.size)
    }

    @Test
    fun `unregistering a page that never registered does not throw`() {
        val page = RecordingPage()
        registry.register(page)

        registry.unregister(RecordingPage())
        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = null)

        assertEquals(1, page.received.size)
    }

    @Test
    fun `broadcast with nobody to receive does not throw`() {
        val author = RecordingPage()
        registry.register(author)

        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = author)

        assertEquals(0, author.received.size)
    }

    @Test
    fun `a page the owner let go is swept and no longer receives`() {
        var collectible: RecordingPage? = RecordingPage()
        val survivor = RecordingPage()
        registry.register(collectible!!)
        registry.register(survivor)

        collectible = null
        awaitCollected()

        registry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, "{}", excludingAuthor = null)
        assertEquals(1, survivor.received.size)
    }

    /** Entries are weak; give the collector a bounded number of chances to prove it. */
    private fun awaitCollected() {
        val probe = java.lang.ref.WeakReference(Any())
        repeat(20) {
            System.gc()
            System.runFinalization()
            if (probe.get() == null) return
            Thread.sleep(50)
        }
    }
}
