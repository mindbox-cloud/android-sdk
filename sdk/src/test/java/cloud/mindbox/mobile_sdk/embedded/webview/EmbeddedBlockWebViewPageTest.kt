package cloud.mindbox.mobile_sdk.embedded.webview

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWebViewPageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun page(
        source: EmbeddedBlockWebViewPage.Source = EmbeddedBlockWebViewPage.Source.Html("<html></html>"),
        domReadyFlag: String? = null,
    ) = EmbeddedBlockWebViewPage(source, context, BRIDGE_NAME, domReadyFlag)

    private fun pageWithFlag(url: String = "https://example.com/stories.html") =
        page(EmbeddedBlockWebViewPage.Source.Url(url), domReadyFlag = "storiesReady")

    private fun bridgeOf(page: EmbeddedBlockWebViewPage): Any? =
        shadowOf(page.view as WebView).getJavascriptInterface(BRIDGE_NAME)

    private fun post(bridge: Any, json: String) {
        bridge.javaClass.getDeclaredMethod("postMessage", String::class.java).invoke(bridge, json)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun mainFrameRequest(url: String): WebResourceRequest = mockk {
        every { isForMainFrame } returns true
        every { this@mockk.url } returns Uri.parse(url)
    }

    private fun subFrameRequest(url: String): WebResourceRequest = mockk {
        every { isForMainFrame } returns false
        every { this@mockk.url } returns Uri.parse(url)
    }

    @Test
    fun `load with html source loads the html into the WebView`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Html("<html>feed</html>"))
        page.load()

        assertEquals("<html>feed</html>", shadowOf(page.view as WebView).lastLoadDataWithBaseURL?.data)
    }

    @Test
    fun `load with url source loads the url`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        page.load()

        assertEquals("https://example.com/feed", shadowOf(page.view as WebView).lastLoadedUrl)
    }

    @Test
    fun `a non-https page url is refused`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("http://example.com/feed"))
        page.load()

        // Block content is loaded into the host's process: plain http would let anyone on the
        // path inject the JS that talks to the bridge.
        assertNull(shadowOf(page.view as WebView).lastLoadedUrl)
    }

    @Test
    fun `a url whose real host hides behind userinfo is refused`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://mobile-static.mindbox.ru@evil.example/feed"))
        page.load()

        // Prefix-matching this url would pass; it actually loads evil.example.
        assertNull(shadowOf(page.view as WebView).lastLoadedUrl)
    }

    @Test
    fun `load registers the js bridge under the block's bridge name`() {
        val page = page()
        page.load()

        assertNotNull(bridgeOf(page))
    }

    @Test
    fun `a valid page message reaches onMessage on the main thread`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        post(bridgeOf(page)!!, """{"type":"ready","height":104}""")

        assertEquals(
            listOf<TempEmbeddedBlockPageMessage>(TempEmbeddedBlockPageMessage.Ready(104.0)),
            received,
        )
    }

    @Test
    fun `a page can say it has nothing to show`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        // Its own message on the wire, carrying no height: emptiness is a verdict, not a number.
        post(bridgeOf(page)!!, """{"type":"empty"}""")

        assertEquals(listOf<TempEmbeddedBlockPageMessage>(TempEmbeddedBlockPageMessage.Empty), received)
    }

    @Test
    fun `an unparsable page message is dropped`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        post(bridgeOf(page)!!, "not a json")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a paused page stops delivering messages`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()
        val bridge = bridgeOf(page)!!

        page.pause()

        // removeJavascriptInterface does not affect an already-loaded page, so the native side
        // of the bridge must go deaf by itself while paused.
        post(bridge, """{"type":"ready","height":104}""")
        assertTrue(received.isEmpty())
    }

    @Test
    fun `resume after pause reconnects the page without a reload`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()
        page.pause()
        page.resume()

        post(bridgeOf(page)!!, """{"type":"ready","height":104}""")

        assertEquals(1, received.size)
    }

    @Test
    fun `page finish starts the dom readiness poll when a flag is configured`() {
        val page = pageWithFlag()
        page.load()
        val webView = page.view as WebView

        shadowOf(webView).webViewClient.onPageFinished(webView, "https://example.com/stories.html")

        val probe = shadowOf(webView).lastEvaluatedJavascript
        assertNotNull(probe)
        assertTrue(probe.contains("storiesReady"))
    }

    @Test
    fun `without a flag the page is not polled at all`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Html("<html></html>"))
        page.load()
        val webView = page.view as WebView

        shadowOf(webView).webViewClient.onPageFinished(webView, null)

        // The mock page answers over the bridge; polling it would spin every 200ms forever.
        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun `a positive dom probe result becomes a Ready message`() {
        val page = pageWithFlag()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        page.onDomReadyProbeResult(probe = "probe", result = "96")

        assertEquals(
            listOf<TempEmbeddedBlockPageMessage>(TempEmbeddedBlockPageMessage.Ready(heightCssPx = 96.0)),
            received,
        )

        // The flag stays "true" forever — the latch must not replay Ready on a re-poll.
        page.onDomReadyProbeResult(probe = "probe", result = "96")
        assertEquals(1, received.size)
    }

    @Test
    fun `a zero dom probe result keeps polling instead of reporting`() {
        val page = pageWithFlag()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        page.onDomReadyProbeResult(probe = "probe", result = "0")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a dom probe result after pause is ignored`() {
        val page = pageWithFlag()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()
        page.pause()

        page.onDomReadyProbeResult(probe = "probe", result = "96")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a main-frame load error is reported as a page error`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        val errors = mutableListOf<String>()
        page.onPageError = { errors.add(it) }
        page.load()

        val webView = page.view as WebView
        // The pre-API-23 callback — the framework only ever calls it for the main frame.
        @Suppress("DEPRECATION")
        shadowOf(webView).webViewClient.onReceivedError(
            webView,
            -2,
            "net::ERR_NAME_NOT_RESOLVED",
            "https://example.com/feed",
        )

        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("-2"))
    }

    @Test
    fun `a sub-frame load error does not fail the block`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        val errors = mutableListOf<String>()
        page.onPageError = { errors.add(it) }
        page.load()

        val webView = page.view as WebView
        shadowOf(webView).webViewClient.onReceivedError(
            webView,
            subFrameRequest("https://cdn.example.com/pixel.gif"),
            mockk(relaxed = true),
        )

        // A tracking pixel or a lazy iframe failing is not the block failing.
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `navigation away from the block page is blocked`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        page.load()
        val webView = page.view as WebView

        val handled = shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
            webView,
            mainFrameRequest("https://evil.example/phish"),
        )

        // A block is a piece of the host's screen, not a browser: it must never navigate.
        assertTrue(handled)
    }

    @Test
    fun `navigation is blocked on the pre-API-24 callback too`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        page.load()
        val webView = page.view as WebView

        @Suppress("DEPRECATION")
        val handled = shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
            webView,
            "https://evil.example/phish",
        )

        // API 21-23 call this overload instead, and its default *allows* the navigation.
        assertTrue(handled)
    }

    @Test
    fun `an inner frame is allowed to load`() {
        val page = page(EmbeddedBlockWebViewPage.Source.Url("https://example.com/feed"))
        page.load()
        val webView = page.view as WebView

        val handled = shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
            webView,
            subFrameRequest("about:blank"),
        )

        // Dynamic iframes legitimately start at about:blank — blocking them breaks real pages.
        assertFalse(handled)
    }

    @Test
    fun `release destroys the WebView for good`() {
        val page = page()
        page.load()

        page.release()

        assertTrue(shadowOf(page.view as WebView).wasDestroyCalled())
    }

    @Test
    fun `a released page ignores further lifecycle calls`() {
        val page = page()
        page.load()
        page.release()

        // A destroyed WebView must never be touched again — every call is a no-op, no crash.
        page.load()
        page.resume()
        page.pause()
    }

    @Test
    fun `a released page delivers nothing`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()
        val bridge = bridgeOf(page)!!

        page.release()
        post(bridge, """{"type":"ready","height":104}""")

        // The callbacks point at a container that is already gone.
        assertTrue(received.isEmpty())
    }

    @Test
    fun `oversized bridge message is dropped`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        val huge = """{"type":"ready","height":""" + "1".repeat(20_000) + "}"
        post(bridgeOf(page)!!, huge)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a page flooding the bridge is throttled`() {
        val page = page()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()
        val bridge = bridgeOf(page)!!

        repeat(100) { post(bridge, """{"type":"heightChanged","height":120}""") }

        // Every accepted message posts a main-looper runnable: an unthrottled loop in the page
        // would ANR the whole host app.
        assertTrue("expected throttling, got ${received.size}", received.size < 100)
    }

    @Test
    fun `repeated load registers the bridge once`() {
        val page = page()
        page.load()
        page.load()

        assertNotNull(bridgeOf(page))
    }

    @Test
    fun `a message outside the common protocol goes to the mechanic raw`() {
        val page = page()
        val common = mutableListOf<TempEmbeddedBlockPageMessage>()
        val mechanic = mutableListOf<org.json.JSONObject>()
        page.onMessage = { common.add(it) }
        page.onMechanicMessage = { mechanic.add(it) }
        page.load()

        // The stories dialect — the generic layer must not understand it, only route it.
        post(bridgeOf(page)!!, """{"type":"storyTap","storyId":"sales"}""")

        assertTrue(common.isEmpty())
        assertEquals("sales", mechanic.single().optString("storyId"))
    }

    @Test
    fun `a common protocol message does not reach the mechanic`() {
        val page = page()
        val common = mutableListOf<TempEmbeddedBlockPageMessage>()
        val mechanic = mutableListOf<org.json.JSONObject>()
        page.onMessage = { common.add(it) }
        page.onMechanicMessage = { mechanic.add(it) }
        page.load()

        post(bridgeOf(page)!!, """{"type":"ready","height":104}""")

        assertTrue(mechanic.isEmpty())
        assertEquals(1, common.size)
    }

    @Test
    fun `a mechanic message with nobody to take it is dropped quietly`() {
        val page = page()
        page.load()

        post(bridgeOf(page)!!, """{"type":"storyTap","storyId":"sales"}""")
    }

    @Test
    fun `a bridge ready resolves the page and ends the dom poll`() {
        // A dual-protocol setup: the flag is configured, but the page answers over the bridge
        // (the mock page). The bridge answer must latch the poll, or the probe would spin
        // every 200ms for as long as the block is on screen.
        val page = page(EmbeddedBlockWebViewPage.Source.Html("<html></html>"), domReadyFlag = "storiesReady")
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        post(bridgeOf(page)!!, """{"type":"ready","height":104}""")

        // A late probe result must neither replay Ready nor reschedule the poll.
        page.onDomReadyProbeResult(probe = "probe", result = "96")
        assertEquals(1, received.size)
    }

    @Test
    fun `a page error ends the dom poll`() {
        val page = pageWithFlag()
        val received = mutableListOf<TempEmbeddedBlockPageMessage>()
        page.onMessage = { received.add(it) }
        page.load()

        val webView = page.view as WebView
        @Suppress("DEPRECATION")
        shadowOf(webView).webViewClient.onReceivedError(
            webView,
            -2,
            "net::ERR_FAILED",
            "https://example.com/stories.html",
        )

        // The outcome is known — polling a broken page for readiness is pointless.
        page.onDomReadyProbeResult(probe = "probe", result = "96")
        assertTrue(received.isEmpty())
    }

    @Test
    fun `release after a renderer crash still destroys the WebView`() {
        val page = page()
        page.load()
        val webView = page.view as WebView

        shadowOf(webView).webViewClient.onRenderProcessGone(
            webView,
            object : android.webkit.RenderProcessGoneDetail() {
                override fun didCrash() = true

                override fun rendererPriorityAtExit() = 0
            },
        )
        page.release()

        // The renderer's death must not block the platform contract: a crashed WebView is
        // still detached and destroyed, not left to the GC.
        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    @Test
    fun `a dead renderer is reported and taken off the screen`() {
        val page = page()
        val errors = mutableListOf<String>()
        page.onPageError = { errors.add(it) }
        page.load()
        val webView = page.view as WebView

        val handled = shadowOf(webView).webViewClient.onRenderProcessGone(
            webView,
            object : android.webkit.RenderProcessGoneDetail() {
                override fun didCrash() = true

                override fun rendererPriorityAtExit() = 0
            },
        )

        // Returning false here is what crashes the HOST app on API 26+.
        assertTrue(handled)
        assertEquals(1, errors.size)
    }

    private companion object {
        private const val BRIDGE_NAME = "testBridge"
    }
}
