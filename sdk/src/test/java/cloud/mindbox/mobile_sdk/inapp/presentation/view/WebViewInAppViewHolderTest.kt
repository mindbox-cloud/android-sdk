package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.inapp.presentation.OnShowInAppOutcome
import cloud.mindbox.mobile_sdk.inapp.presentation.ShowInAppOutcome
import cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WebViewInAppViewHolderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val realGson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private val gatewayManager: GatewayManager = mockk()
    private val inAppFailureTracker: InAppFailureTracker = mockk(relaxed = true)
    private val inAppCallback: InAppCallback = mockk(relaxed = true)
    private val inAppActionCallbacks: InAppActionCallbacks = mockk(relaxed = true)
    private val inAppInteractor: InAppInteractor = mockk()
    private val inAppMessageManager: InAppMessageManager = mockk()
    private val onBackPress = slot<() -> Unit>()
    private val backPressRegistrar: BackPressRegistrar = mockk {
        every { register(any(), capture(onBackPress)) } returns BackRegistration {}
    }

    private var closeCount = 0
    private lateinit var holder: WebViewInAppViewHolder
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { gson } returns realGson
            every { gatewayManager } returns this@WebViewInAppViewHolderTest.gatewayManager
            every { appContext } returns application
            every { inAppFailureTracker } returns this@WebViewInAppViewHolderTest.inAppFailureTracker
            every { inAppInteractor } returns this@WebViewInAppViewHolderTest.inAppInteractor
            every { inAppMessageManager } returns this@WebViewInAppViewHolderTest.inAppMessageManager
            every { webViewCachePolicy } returns mockk<InAppWebViewCachePolicy> {
                every { isCacheEnabled } returns false
            }
        }
        mockkObject(DbManager)
        mockkObject(MindboxLoggerImpl)
        every { DbManager.listenConfigurations() } returns flowOf(mockk<Configuration>(relaxed = true))
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html>inapp</html>"

        holder = WebViewInAppViewHolder(
            wrapper = InAppTypeWrapper(
                inAppType = InAppStub.getWebView().copy(inAppId = "inapp-id"),
                inAppActionCallbacks = inAppActionCallbacks,
                onRenderStart = {},
            ),
            controller = InAppViewHolder.InAppController {
                closeCount++
                holder.onClose()
            },
            inAppCallback = inAppCallback,
        )
    }

    @After
    fun tearDown() {
        holder.onClose()
        unmockkObject(DbManager)
        unmockkObject(MindboxLoggerImpl)
    }

    private fun showAndAwaitPageLoad() {
        holder.show(object : MindboxView {
            override val container: ViewGroup = FrameLayout(application)
            override val backPressRegistrar: BackPressRegistrar = this@WebViewInAppViewHolderTest.backPressRegistrar

            override fun requestPermission() = Unit
        })
        val field = WebViewInAppViewHolder::class.java.getDeclaredField("webViewController")
        field.isAccessible = true
        webView = (field.get(holder) as WebViewController).view as WebView
        await { shadowOf(webView).lastLoadDataWithBaseURL != null }
    }

    private fun await(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun postFromPage(
        action: String,
        id: String,
        type: String = "request",
        payload: String = "{}",
    ) {
        queueFromPage(action = action, id = id, type = type, payload = payload)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun queueFromPage(
        action: String,
        id: String,
        type: String = "request",
        payload: String = "{}",
    ) {
        val json = """{"type":"$type","action":"$action","payload":${Gson().toJson(payload)},"id":"$id","version":1,"timestamp":1}"""
        val bridge = shadowOf(webView).getJavascriptInterface("SdkBridge")
        val postMessage = bridge.javaClass.getDeclaredMethod("postMessage", String::class.java)
        postMessage.isAccessible = true
        postMessage.invoke(bridge, json)
    }

    private fun lastOutgoingMessage(): JsonObject? {
        val script = shadowOf(webView).lastEvaluatedJavascript ?: return null
        val quoted = Regex("""emit\((".*")\);return""").find(script)?.groupValues?.get(1) ?: return null
        val json = JSONTokener(quoted).nextValue() as String
        return JsonParser.parseString(json).asJsonObject
    }

    private fun lastOutgoingPayload(): JsonObject? =
        lastOutgoingMessage()?.get("payload")?.asString?.let { JsonParser.parseString(it).asJsonObject }

    private fun failLastDelivery() {
        shadowOf(webView).lastEvaluatedJavascriptCallback.onReceiveValue("false")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a message the page did not take after the show sends no failure and keeps the in-app`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")
        verify(exactly = 1) { inAppActionCallbacks.onInAppShown.onShown() }
        postFromPage(action = "log", id = "log-1")
        await { lastOutgoingMessage()?.get("id")?.asString == "log-1" }

        failLastDelivery()

        verify(exactly = 0) { inAppFailureTracker.sendFailure(any(), any(), any(), any()) }
        assertEquals(0, closeCount)
    }

    @Test
    fun `a back the page did not take still closes the in-app and sends no failure`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        onBackPress.captured()
        await { lastOutgoingMessage()?.get("action")?.asString == "back" }
        failLastDelivery()

        assertEquals(1, closeCount)
        verify(exactly = 1) { inAppCallback.onInAppDismissed("inapp-id") }
        verify(exactly = 0) { inAppFailureTracker.sendFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `a page error closes the in-app, tells the host once and sends no failure`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "localState.changed", id = "changed-1", type = "error")

        assertEquals(1, closeCount)
        verify(exactly = 1) { inAppCallback.onInAppDismissed("inapp-id") }
        verify(exactly = 0) { inAppFailureTracker.sendFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `a page close and a page error queued together tell the host once`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        queueFromPage(action = "close", id = "close-1")
        queueFromPage(action = "localState.changed", id = "changed-1", type = "error")
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { inAppCallback.onInAppDismissed("inapp-id") }
    }

    @Test
    fun `a request the overlay does not serve is answered with not_served, and its detail goes to the log`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "navigationIntercepted", id = "navigation-1")
        await { lastOutgoingMessage()?.get("id")?.asString == "navigation-1" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("""{"error":"not_served"}""", lastOutgoingPayload().toString())
        verify(exactly = 1) {
            MindboxLoggerImpl.e(
                holder,
                "[WebView] Bridge: 'NAVIGATION_INTERCEPTED' navigation-1 refused with not_served for 'inapp-id': " +
                    "Action NAVIGATION_INTERCEPTED is not served on this surface",
            )
        }
    }

    @Test
    fun `filterShowableInapps from the overlay page is answered with the selection made for the overlay`() {
        coEvery { inAppInteractor.filterShowableInAppIds("inapp-id", listOf("story-2", "story-3")) } returns listOf("story-2")
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "filterShowableInapps", id = "filter-1", payload = """{"inappIds":["story-2","story-3"]}""")
        await { lastOutgoingMessage()?.get("id")?.asString == "filter-1" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals(listOf("story-2"), lastOutgoingPayload()?.getAsJsonArray("inappIds")?.map { id -> id.asString })
    }

    @Test
    fun `showInApp of an in-app the overlay cannot open keeps the overlay and refuses the page`() {
        val outcome = slot<OnShowInAppOutcome>()
        every { inAppMessageManager.showInAppById("missing", any(), any(), capture(outcome)) } just runs
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "showInApp", id = "show-1", payload = """{"inappId":"missing"}""")
        await { outcome.isCaptured }
        outcome.captured.onOutcome(ShowInAppOutcome.NotShown(BridgeErrorCode.UNKNOWN_INAPP))
        await { lastOutgoingMessage()?.get("id")?.asString == "show-1" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("""{"error":"unknown_inapp"}""", lastOutgoingPayload().toString())
        assertEquals(0, closeCount)
    }

    @Test
    fun `showInApp without an inappId is refused as invalid_payload and keeps the overlay`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "showInApp", id = "show-1", payload = """{"params":{}}""")
        await { lastOutgoingMessage()?.get("id")?.asString == "show-1" }

        assertEquals("""{"error":"invalid_payload"}""", lastOutgoingPayload().toString())
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any(), any(), any()) }
        assertEquals(0, closeCount)
    }

    @Test
    fun `the overlay stops being the asker of its showInApp once it closes`() {
        val askerAlive = slot<() -> Boolean>()
        every { inAppMessageManager.showInAppById("story-2", any(), capture(askerAlive), any()) } just runs
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")
        postFromPage(action = "showInApp", id = "show-1", payload = """{"inappId":"story-2"}""")
        await { askerAlive.isCaptured }
        assertTrue(askerAlive.captured())

        postFromPage(action = "close", id = "close-1")

        assertFalse(askerAlive.captured())
    }

    @Test
    fun `back sent by the page is refused as not_served`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        postFromPage(action = "back", id = "back-1")
        await { lastOutgoingMessage()?.get("id")?.asString == "back-1" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("back", lastOutgoingMessage()?.get("action")?.asString)
        assertEquals("""{"error":"not_served"}""", lastOutgoingMessage()?.get("payload")?.asString)
        assertEquals(0, closeCount)
    }

    @Test
    fun `an action this SDK does not know is refused with the name the page sent`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")

        listOf("show", "", "OPEN_LINK").forEachIndexed { index, action ->
            postFromPage(action = action, id = "unknown-$index")
            await { lastOutgoingMessage()?.get("id")?.asString == "unknown-$index" }

            assertEquals("type for '$action'", "error", lastOutgoingMessage()?.get("type")?.asString)
            assertEquals("action for '$action'", action, lastOutgoingMessage()?.get("action")?.asString)
            assertEquals("payload for '$action'", """{"error":"unknown_action"}""", lastOutgoingMessage()?.get("payload")?.asString)
        }
        assertEquals(0, closeCount)
    }

    @Test
    fun `a message the page did not take is logged with its action and id`() {
        showAndAwaitPageLoad()
        postFromPage(action = "init", id = "init-1")
        postFromPage(action = "log", id = "log-1")
        await { lastOutgoingMessage()?.get("id")?.asString == "log-1" }

        failLastDelivery()

        verify(exactly = 1) {
            MindboxLoggerImpl.w(holder, "[WebView] Bridge: 'LOG' log-1 (response) did not reach the page: false")
        }
    }

    @Test
    fun `an init still queued when the in-app closes is dropped before any handler`() {
        showAndAwaitPageLoad()

        queueFromPage(action = "init", id = "init-1")
        holder.onClose()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) {
            MindboxLoggerImpl.w(any(), "Dropping a page message that reached WebView In-App inapp-id after it was closed")
        }
    }
}
