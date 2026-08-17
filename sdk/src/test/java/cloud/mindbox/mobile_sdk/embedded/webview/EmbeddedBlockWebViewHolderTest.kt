package cloud.mindbox.mobile_sdk.embedded.webview

import android.app.Application
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The feed holder against the real stories-page protocol, driven through the real bridge
 * transport (`SdkBridge` js interface): `ready` answers with `stories` as a JSON array,
 * `checkInappsTargeting` returns the interactor's subset, `contentRendered {count}` is the
 * readiness signal, and `initDataUpdated` refreshes the feed without recreating the webview.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWebViewHolderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val realGson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private val gatewayManager: GatewayManager = mockk()
    private val inAppInteractor: InAppInteractor = mockk()
    private val permissionManager: PermissionManager = mockk {
        every { getCameraPermissionStatus() } returns PermissionStatus.DENIED
        every { getLocationPermissionStatus() } returns PermissionStatus.DENIED
        every { getMicrophonePermissionStatus() } returns PermissionStatus.DENIED
        every { getNotificationPermissionStatus() } returns PermissionStatus.DENIED
        every { getPhotoLibraryPermissionStatus() } returns PermissionStatus.DENIED
    }

    private val states = mutableListOf<EmbeddedBlockState>()

    private lateinit var holder: EmbeddedBlockWebViewHolder

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { gson } returns realGson
            every { gatewayManager } returns this@EmbeddedBlockWebViewHolderTest.gatewayManager
            every { inAppInteractor } returns this@EmbeddedBlockWebViewHolderTest.inAppInteractor
            every { permissionManager } returns this@EmbeddedBlockWebViewHolderTest.permissionManager
            every { appContext } returns application
            every { webViewCachePolicy } returns mockk<InAppWebViewCachePolicy> {
                every { isCacheEnabled } returns false
            }
        }
        mockkObject(DbManager)
        every { DbManager.listenConfigurations() } returns flowOf(
            mockk<Configuration>(relaxed = true) {
                every { endpointId } returns "endpoint-id"
                every { versionName } returns "1.0"
            }
        )
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html>feed</html>"

        holder = EmbeddedBlockWebViewHolder(
            inAppId = "embedded-id",
            layer = InAppStub.getEmbeddedWebViewLayer(),
            context = application,
        )
        holder.onStateChange = { state -> states.add(state) }
    }

    @After
    fun tearDown() {
        holder.release()
        unmockkObject(DbManager)
    }

    private val webView: WebView
        get() = holder.run {
            // The content view is gated by readiness; reach the raw view through the bridge
            // registration instead.
            webViewField()
        }

    private fun EmbeddedBlockWebViewHolder.webViewField(): WebView {
        val field = EmbeddedBlockWebViewHolder::class.java.getDeclaredField("webViewController")
        field.isAccessible = true
        val controller = field.get(this) as cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
        return controller.view as WebView
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

    private fun startAndAwaitPageLoad() {
        holder.start()
        await { shadowOf(webView).lastLoadDataWithBaseURL != null }
    }

    private fun bridge(): Any = shadowOf(webView).getJavascriptInterface("SdkBridge")

    private fun postFromPage(json: String) {
        val bridge = bridge()
        val postMessage = bridge.javaClass.getDeclaredMethod("postMessage", String::class.java)
        postMessage.isAccessible = true
        postMessage.invoke(bridge, json)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun request(action: String, payload: String, id: String = "req-1"): String =
        """{"type":"request","action":"$action","payload":${com.google.gson.Gson().toJson(payload)},"id":"$id","version":1,"timestamp":1}"""

    /** Unwraps the JS bridge call back into the outgoing message payload. */
    private fun lastOutgoingMessage(): JsonObject? {
        val script = shadowOf(webView).lastEvaluatedJavascript ?: return null
        val quoted = Regex("""emit\((".*")\);return""").find(script)?.groupValues?.get(1) ?: return null
        val json = JSONTokener(quoted).nextValue() as String
        return JsonParser.parseString(json).asJsonObject
    }

    private fun lastOutgoingPayload(): JsonObject? =
        lastOutgoingMessage()?.get("payload")?.asString?.let { JsonParser.parseString(it).asJsonObject }

    @Test
    fun `content page is fetched through the gateway and loaded with the base url`() {
        startAndAwaitPageLoad()

        val loaded = shadowOf(webView).lastLoadDataWithBaseURL
        assertEquals("<html>feed</html>", loaded?.data)
        assertEquals("https://feed.local/base", loaded?.baseUrl)
    }

    @Test
    fun `ready response contains stories as json array not string`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "ready", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "ready" }

        val payload = lastOutgoingPayload()!!
        assertTrue(payload.get("stories").isJsonArray)
        assertEquals(
            "story-1",
            payload.getAsJsonArray("stories").get(0).asJsonObject.get("inAppId").asString
        )
        assertEquals("endpoint-id", payload.get("endpointId").asString)
    }

    @Test
    fun `checkInappsTargeting returns subset from the interactor`() {
        coEvery { inAppInteractor.filterShowableInAppIds(listOf("story-1", "story-2")) } returns
            listOf("story-1")
        startAndAwaitPageLoad()

        postFromPage(request(action = "checkInappsTargeting", payload = """{"inappIds":["story-1","story-2"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "checkInappsTargeting" }

        val payload = lastOutgoingPayload()!!
        assertEquals(1, payload.getAsJsonArray("inappIds").size())
        assertEquals("story-1", payload.getAsJsonArray("inappIds").get(0).asString)
    }

    @Test
    fun `contentRendered with positive count switches state to Ready and counts the show`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        assertTrue(holder.contentView != null)
        // Content on screen is a show, counted like any other in-app's; the frequency decides
        // inside the interactor whether there is anything to write.
        coVerify(timeout = 5_000L) { inAppInteractor.recordBlockShow("embedded-id", any(), any()) }
    }

    @Test
    fun `contentRendered with zero count switches state to Empty and reports nothing`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":0}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Empty }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
    }

    @Test
    fun `contentRendered without a readable count fails the block and reports the failure`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
    }

    @Test
    fun `the show is reported once per content instance`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        coVerify(exactly = 1, timeout = 5_000L) { inAppInteractor.recordBlockShow("embedded-id", any(), any()) }
    }

    @Test
    fun `showInApp from the page is acknowledged and reports nothing`() {
        // The circle is not drawn yet: neither the show operation nor the local history may be
        // spent on it. Both ship with the JS-bridge task, where the story actually opens.
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "showInApp", payload = """{"inappId":"story-1","index":0,"params":{}}""")
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
    }

    @Test
    fun `initDataUpdated refreshes the feed without recreating the webview`() {
        startAndAwaitPageLoad()
        val viewBeforeUpdate = webView

        var updateResult: Boolean? = null
        holder.updateParams(mapOf("stories" to """[{"inAppId":"story-2"}]""")) { isUpdated ->
            updateResult = isUpdated
        }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        val outgoing = lastOutgoingMessage()!!
        assertTrue(lastOutgoingPayload()!!.get("stories").isJsonArray)

        // The page answers success — the very same webview keeps living.
        postFromPage(
            """{"type":"response","action":"initDataUpdated","payload":"{\"success\":true}",""" +
                """"id":${outgoing.get("id")},"version":1,"timestamp":2}"""
        )
        await { updateResult != null }
        assertEquals(true, updateResult)
        assertTrue(viewBeforeUpdate === webView)
    }

    @Test
    fun `unknown page action is answered with an error and ignored`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "close", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "close" }

        // The overlay protocol's close means nothing to the block: an error response goes
        // back and no state changes.
        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(states.none { state -> state == EmbeddedBlockState.Failed })
    }

    @Test
    fun `content fetch failure reports Failed`() {
        coEvery { gatewayManager.fetchWebViewContent(any()) } throws IllegalStateException("network down")

        holder.start()

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
    }

    @Test
    fun `updateParams before the page exists reports failure`() {
        var updateResult: Boolean? = null

        holder.updateParams(mapOf("stories" to "[]")) { isUpdated -> updateResult = isUpdated }

        assertEquals(false, updateResult)
    }

    @Test
    fun `local state get is served from the store`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "localState.get", payload = """{"keys":["inapp.completed.story-1"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.get" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `release destroys the webview and start after release does nothing`() {
        startAndAwaitPageLoad()
        val statesBefore = states.size

        holder.release()
        holder.start()

        assertEquals(statesBefore, states.size)
    }
}
