package cloud.mindbox.mobile_sdk.embedded.webview

import android.view.View
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWebViewProviderTest {

    private class FakePage : EmbeddedBlockPage {
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override var onMessage: ((TempEmbeddedBlockPageMessage) -> Unit)? = null
        override var onMechanicMessage: ((org.json.JSONObject) -> Unit)? = null
        override var onPageError: ((String) -> Unit)? = null
        var loadCount = 0
        var pauseCount = 0
        var resumeCount = 0
        var releaseCount = 0

        override fun load() {
            loadCount++
        }

        override fun pause() {
            pauseCount++
        }

        override fun resume() {
            resumeCount++
        }

        override fun release() {
            releaseCount++
        }

        fun send(message: TempEmbeddedBlockPageMessage) {
            onMessage?.invoke(message)
        }

        fun fail(description: String) {
            onPageError?.invoke(description)
        }
    }

    private lateinit var page: FakePage
    private lateinit var provider: EmbeddedBlockWebViewProvider
    private val states = mutableListOf<EmbeddedBlockState>()

    @Before
    fun setUp() {
        page = FakePage()
        provider = EmbeddedBlockWebViewProvider(page)
        states.clear()
        provider.onStateChange = { states.add(it) }
    }

    @Test
    fun `start reports loading and loads the page`() {
        provider.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
        assertEquals(1, page.loadCount)
    }

    @Test
    fun `ready message with a plausible height becomes the Ready state`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))

        // The height is only validated (zero → Failed, implausible → Failed), never carried:
        // the host owns the block size.
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertNotNull(provider.contentView)
    }

    @Test
    fun `heightChanged is not a second way to say ready`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.HeightChanged(heightCssPx = 150.0))

        // The host owns the height, so this message carries nothing the native side can act on.
        // Showing a block on it would let a page skip the readiness handshake entirely.
        assertEquals(EmbeddedBlockState.Loading, states.last())
        assertNull(provider.contentView)
    }

    @Test
    fun `a relayout to zero does not collapse a block the user is looking at`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))
        page.send(TempEmbeddedBlockPageMessage.HeightChanged(heightCssPx = 0.0))

        // A page measuring itself mid-animation reports zero and recovers a frame later; pulling
        // the block out of the host layout for that would be a visible jump for nothing.
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertNotNull(provider.contentView)
    }

    @Test
    fun `a page with nothing to show says so and empties the block`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Empty)

        // The page worked, its targeting just matched nothing — the empty state, not a failure.
        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertNull(provider.contentView)
        // The buried page is silenced: invisible content must not keep running JS.
        assertEquals(1, page.pauseCount)
    }

    @Test
    fun `a page that empties itself after being ready collapses the block`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))
        page.send(TempEmbeddedBlockPageMessage.Empty)

        // Content can disappear live (every story watched, targeting re-evaluated).
        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertNull(provider.contentView)
    }

    @Test
    fun `a ready at zero height is a broken page, not an empty one`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 0.0))

        // The page announced it rendered and rendered nothing. Emptiness has its own message, so
        // this is a contradiction — and a block must not pass a contradiction off as a normal
        // outcome.
        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertNull(provider.contentView)
        assertEquals(1, page.pauseCount)
    }

    @Test
    fun `a negative height is broken the same way`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = -10.0))

        assertEquals(EmbeddedBlockState.Failed, states.last())
    }

    @Test
    fun `a failed page is silenced`() {
        provider.start()

        page.fail("Page load error")

        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertEquals(1, page.pauseCount)
    }

    @Test
    fun `implausible height means a broken page and fails the block`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 1.0e9))

        // Honoring it would hand one JS message the power to blow up the host's measure pass.
        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertNull(provider.contentView)
        assertEquals(1, page.pauseCount)
    }

    @Test
    fun `contentView is hidden until the page is ready`() {
        provider.start()

        assertNull(provider.contentView)
    }

    @Test
    fun `pause quiets the page and silences it until the next start`() {
        provider.start()
        provider.pause()
        states.clear()

        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))

        assertTrue(states.isEmpty())
        assertEquals(1, page.pauseCount)
    }

    @Test
    fun `start after pause resumes the page and replays the current state`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))
        provider.pause()
        states.clear()

        provider.start()

        // A pause is not a teardown: the page is resumed, never reloaded, and the container
        // immediately learns where the content stands.
        assertEquals(1, page.loadCount)
        assertEquals(1, page.resumeCount)
        assertTrue(states.single() is EmbeddedBlockState.Ready)
        assertNotNull(provider.contentView)
    }

    @Test
    fun `messages are accepted again after a resume`() {
        provider.start()
        provider.pause()
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))

        assertTrue(states.last() is EmbeddedBlockState.Ready)
    }

    @Test
    fun `a page error fails the block right away`() {
        provider.start()

        page.fail("Page load error -2: net::ERR_NAME_NOT_RESOLVED")

        // No waiting out the container's timeout: a broken main frame is terminal.
        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertNull(provider.contentView)
    }

    @Test
    fun `a page error while paused is latched and the next start replays Failed`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))
        provider.pause()
        states.clear()

        // The system is free to kill the renderer of a backgrounded WebView; the paused
        // container is not notified right away…
        page.fail("renderer gone while paused")
        assertTrue(states.isEmpty())

        provider.start()

        // …but the next start must replay Failed, not the stale Ready over a dead page.
        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertNull(provider.contentView)
    }

    @Test
    fun `release tears the page down for good`() {
        provider.start()
        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))

        provider.release()

        assertEquals(1, page.releaseCount)
        assertNull(provider.contentView)
    }

    @Test
    fun `a released provider ignores the page it no longer owns`() {
        provider.start()
        provider.release()
        states.clear()

        page.send(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 104.0))

        assertTrue(states.isEmpty())
    }
}
