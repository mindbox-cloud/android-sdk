package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppToShow
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeErrorCode
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewInAppViewHolder
import cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume

// No Dispatchers.setMain: the manager's main step has to queue on the same Robolectric looper the holders run on.
@RunWith(RobolectricTestRunner::class)
internal class InAppSupersedeEndToEndTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val realGson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private val gatewayManager: GatewayManager = mockk()
    private val inAppInteractor: InAppInteractor = mockk(relaxUnitFun = true)
    private val inAppFailureTracker: InAppFailureTracker = mockk(relaxed = true)
    private val host: InAppCallback = mockk(relaxed = true)
    private val root = FrameLayout(application)

    private lateinit var displayer: InAppMessageViewDisplayerImpl
    private lateinit var manager: InAppMessageManagerImpl

    private val stories: Map<String, InApp> = listOf("story-1", "story-2", "story-3").associateWith { id ->
        InAppStub.getInApp().copy(id = id, form = Form(variants = listOf(storyPage(id))))
    }

    private fun storyPage(id: String) = InAppType.WebView(
        inAppId = id,
        type = "webview",
        layers = listOf(
            Layer.WebViewLayer(
                baseUrl = "https://stories.local",
                contentUrl = contentUrlOf(id),
                type = "webview",
                params = emptyMap(),
            )
        ),
    )

    private fun contentUrlOf(id: String) = "https://stories.local/$id.html"

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { gson } returns realGson
            every { gatewayManager } returns this@InAppSupersedeEndToEndTest.gatewayManager
            every { appContext } returns application
            every { inAppFailureTracker } returns this@InAppSupersedeEndToEndTest.inAppFailureTracker
            every { inAppInteractor } returns this@InAppSupersedeEndToEndTest.inAppInteractor
            every { inAppMessageManager } answers { manager }
            every { webViewCachePolicy } returns mockk<InAppWebViewCachePolicy> {
                every { isCacheEnabled } returns false
            }
        }
        mockkObject(DbManager)
        mockkObject(MindboxLoggerImpl)
        every { DbManager.listenConfigurations() } returns flowOf(mockk<Configuration>(relaxed = true))
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html>story</html>"
        stories.forEach { (id, inApp) ->
            coEvery { inAppInteractor.getInAppToShowById(id) } returns InAppToShow(inApp, inApp.form.variants.first())
        }
        coEvery { inAppInteractor.getInAppToShowById("missing") } returns null

        displayer = InAppMessageViewDisplayerImpl(mockk())
        manager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer = displayer,
            inAppInteractor = inAppInteractor,
            defaultDispatcher = Dispatchers.Unconfined,
            monitoringInteractor = mockk(),
            sessionStorageManager = mockk<SessionStorageManager>(relaxed = true),
            userVisitManager = mockk(),
            inAppMessageDelayedManager = mockk(),
            timeProvider = SystemTimeProvider(),
            featureToggleManager = mockk { every { isEnabled(any()) } returns false },
        )
        manager.registerInAppCallback(host)
        manager.registerCurrentActivity(
            mockk<Activity>(relaxed = true) {
                every { isFinishing } returns false
                every { window } returns mockk { every { decorView } returns root }
            }
        )
    }

    @After
    fun tearDown() {
        displayer.dismissCurrentInApp()
        shadowOf(Looper.getMainLooper()).idle()
        unmockkAll()
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

    private fun awaitNothingShownFor(inAppId: String) = await {
        runCatching {
            verify { MindboxLoggerImpl.i(any(), "The page that asked for in-app $inAppId is gone, showing nothing") }
        }.isSuccess
    }

    private fun askToShow(inAppId: String): List<ShowInAppOutcome> {
        val outcomes = mutableListOf<ShowInAppOutcome>()
        manager.showInAppById(inAppId, emptyMap(), requesterIsActive = { true }) { outcome -> outcomes.add(outcome) }
        return outcomes
    }

    private class HeldLookup {
        @Volatile var parked: CancellableContinuation<Unit>? = null
    }

    private fun holdLookupOf(inAppId: String): HeldLookup {
        val held = HeldLookup()
        val inApp = stories.getValue(inAppId)
        coEvery { inAppInteractor.getInAppToShowById(inAppId) } coAnswers {
            suspendCancellableCoroutine<Unit> { continuation -> held.parked = continuation }
            InAppToShow(inApp, inApp.form.variants.first())
        }
        return held
    }

    private fun HeldLookup.awaitParked() = await { parked != null }

    private fun HeldLookup.release() {
        awaitParked()
        parked?.resume(Unit)
    }

    private fun onScreen(): InAppViewHolder<*>? =
        InAppMessageViewDisplayerImpl::class.java.getDeclaredField("currentHolder")
            .apply { isAccessible = true }
            .get(displayer) as InAppViewHolder<*>?

    private fun onScreenId(): String? = onScreen()?.wrapper?.inAppType?.inAppId

    private fun initTimeoutOf(holder: InAppViewHolder<*>): Any? =
        WebViewInAppViewHolder::class.java.getDeclaredField("initTimeout")
            .apply { isAccessible = true }
            .get(holder)

    private fun awaitLoadedPageOf(inAppId: String): WebView {
        await { onScreenId() == inAppId }
        val controller = WebViewInAppViewHolder::class.java.getDeclaredField("webViewController")
            .apply { isAccessible = true }
            .get(onScreen()) as WebViewController
        val page = controller.view as WebView
        await { shadowOf(page).lastLoadDataWithBaseURL != null }
        return page
    }

    private fun WebView.post(action: String, id: String, payload: String = "{}") {
        val json = """{"type":"request","action":"$action","payload":${Gson().toJson(payload)},"id":"$id","version":1,"timestamp":1}"""
        val bridge = shadowOf(this).getJavascriptInterface("SdkBridge")
        bridge.javaClass.getDeclaredMethod("postMessage", String::class.java)
            .apply { isAccessible = true }
            .invoke(bridge, json)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun WebView.askForNext(inAppId: String, id: String) =
        post(action = "showInApp", id = id, payload = """{"inappId":"$inAppId"}""")

    private fun WebView.lastOutgoingMessage(): JsonObject? {
        val script = shadowOf(this).lastEvaluatedJavascript ?: return null
        val quoted = Regex("""emit\((".*")\);return""").find(script)?.groupValues?.get(1) ?: return null
        return JsonParser.parseString(JSONTokener(quoted).nextValue() as String).asJsonObject
    }

    private fun WebView.lastOutgoingPayload(): JsonObject? =
        lastOutgoingMessage()?.get("payload")?.asString?.let { JsonParser.parseString(it).asJsonObject }

    @Test
    fun `a story replaced after its init keeps its success, is dismissed with its cooldown and leaves one window`() {
        val first = askToShow("story-1")
        val firstPage = awaitLoadedPageOf("story-1")
        firstPage.post(action = "init", id = "init-1")
        assertEquals(listOf(ShowInAppOutcome.Shown), first)

        askToShow("story-2")
        awaitLoadedPageOf("story-2")

        assertEquals(listOf(ShowInAppOutcome.Shown), first)
        verify(exactly = 1) { host.onInAppDismissed("story-1") }
        verify(exactly = 1) { inAppInteractor.saveInAppDismissTime(stories.getValue("story-1"), any()) }
        assertEquals(1, root.childCount)
        assertNull(firstPage.parent)
    }

    @Test
    fun `a story replaced before its init answers show_failed and is still dismissed`() {
        val first = askToShow("story-1")
        val firstPage = awaitLoadedPageOf("story-1")

        askToShow("story-2")
        awaitLoadedPageOf("story-2")

        assertEquals(listOf(ShowInAppOutcome.NotShown(BridgeErrorCode.SHOW_FAILED)), first)
        verify(exactly = 1) { host.onInAppDismissed("story-1") }
        verify(exactly = 0) { inAppInteractor.saveInAppDismissTime(any(), any()) }
        assertEquals(1, root.childCount)
        assertNull(firstPage.parent)
    }

    @Test
    fun `a story that opens the next is closed without an answer and the next one shows with its params`() {
        askToShow("story-1")
        val firstPage = awaitLoadedPageOf("story-1")
        firstPage.post(action = "init", id = "init-1")

        firstPage.post(action = "showInApp", id = "next-1", payload = """{"inappId":"story-2","params":{"from":"story-1"}}""")
        val nextPage = awaitLoadedPageOf("story-2")
        nextPage.post(action = "ready", id = "ready-2")
        await { nextPage.lastOutgoingMessage()?.get("id")?.asString == "ready-2" }
        val nextStartPayload = nextPage.lastOutgoingPayload()
        nextPage.post(action = "init", id = "init-2")
        await { nextPage.lastOutgoingMessage()?.get("id")?.asString == "init-2" }

        assertEquals("init-1", firstPage.lastOutgoingMessage()?.get("id")?.asString)
        assertNull(firstPage.parent)
        verify(exactly = 1) { host.onInAppDismissed("story-1") }
        assertEquals("story-1", nextStartPayload?.get("from")?.asString)
        assertEquals(1, root.childCount)
    }

    @Test
    fun `a story closed by its own page before init is dismissed and answers show_failed`() {
        val first = askToShow("story-1")
        awaitLoadedPageOf("story-1").post(action = "close", id = "close-1")

        assertEquals(listOf(ShowInAppOutcome.NotShown(BridgeErrorCode.SHOW_FAILED)), first)
        verify(exactly = 1) { host.onInAppDismissed("story-1") }
        assertEquals(0, root.childCount)
    }

    @Test
    fun `a story that asks for the next before its own init is replaced the same way`() {
        val first = askToShow("story-1")
        val firstPage = awaitLoadedPageOf("story-1")
        firstPage.askForNext("story-2", id = "next-1")

        val nextPage = awaitLoadedPageOf("story-2")
        nextPage.post(action = "init", id = "init-2")
        await { nextPage.lastOutgoingMessage()?.get("id")?.asString == "init-2" }

        assertEquals(listOf(ShowInAppOutcome.NotShown(BridgeErrorCode.SHOW_FAILED)), first)
        verify(exactly = 1) { host.onInAppDismissed("story-1") }
        assertNull(firstPage.parent)
        assertNull(firstPage.lastOutgoingMessage())
        assertEquals(1, root.childCount)
    }

    @Test
    fun `a story asking for an in-app nothing resolves stays on screen and hears unknown_inapp`() {
        askToShow("story-1")
        val page = awaitLoadedPageOf("story-1")
        page.post(action = "init", id = "init-1")

        page.askForNext("missing", id = "next-1")
        await { page.lastOutgoingMessage()?.get("id")?.asString == "next-1" }

        assertEquals("error", page.lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("unknown_inapp", page.lastOutgoingPayload()?.get("error")?.asString)
        assertEquals("story-1", onScreenId())
        verify(exactly = 0) { host.onInAppDismissed(any()) }
    }

    @Test
    fun `a story closed while its request for the next waited opens nothing`() {
        askToShow("story-1")
        val page = awaitLoadedPageOf("story-1")
        page.post(action = "init", id = "init-1")
        val lookup = holdLookupOf("story-2")
        page.askForNext("story-2", id = "next-1")
        lookup.awaitParked()

        page.post(action = "close", id = "close-1")
        lookup.release()
        awaitNothingShownFor("story-2")

        assertNull(onScreen())
        assertEquals(0, root.childCount)
        verify(exactly = 1) { host.onInAppDismissed(any()) }
    }

    @Test
    fun `a story replaced while its request waited leaves the replacement on screen`() {
        askToShow("story-1")
        val page = awaitLoadedPageOf("story-1")
        page.post(action = "init", id = "init-1")
        val lookup = holdLookupOf("story-2")
        page.askForNext("story-2", id = "next-1")
        lookup.awaitParked()

        askToShow("story-3")
        awaitLoadedPageOf("story-3")
        lookup.release()
        awaitNothingShownFor("story-2")

        assertEquals("story-3", onScreenId())
        assertEquals(1, root.childCount)
        verify(exactly = 0) { host.onInAppDismissed("story-3") }
    }

    @Test
    fun `two requests from one story open only the one that reaches the main thread first`() {
        askToShow("story-1")
        val page = awaitLoadedPageOf("story-1")
        page.post(action = "init", id = "init-1")
        val toSecond = holdLookupOf("story-2")
        val toThird = holdLookupOf("story-3")
        page.askForNext("story-2", id = "next-2")
        page.askForNext("story-3", id = "next-3")
        toThird.awaitParked()

        toSecond.release()
        awaitLoadedPageOf("story-2")
        toThird.release()
        awaitNothingShownFor("story-3")

        assertEquals("story-2", onScreenId())
        assertEquals(1, root.childCount)
        verify(exactly = 0) { host.onInAppDismissed("story-2") }
    }

    @Test
    fun `two requests from one story that both reach the main thread before it runs open only the first`() {
        askToShow("story-1")
        val page = awaitLoadedPageOf("story-1")
        page.post(action = "init", id = "init-1")
        val toSecond = holdLookupOf("story-2")
        val toThird = holdLookupOf("story-3")
        page.askForNext("story-2", id = "next-2")
        page.askForNext("story-3", id = "next-3")
        toSecond.awaitParked()
        toThird.awaitParked()

        toSecond.release()
        toThird.release()
        awaitNothingShownFor("story-3")

        assertEquals("story-2", onScreenId())
        assertEquals(1, root.childCount)
        verify(exactly = 0) { host.onInAppDismissed("story-2") }
    }

    @Test
    fun `a content failure reaching the main thread after its story was replaced reports nothing`() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(Dispatchers.Unconfined)
        val firstContent = CompletableDeferred<String>()
        coEvery { gatewayManager.fetchWebViewContent(contentUrlOf("story-1")) } coAnswers { firstContent.await() }
        askToShow("story-1")
        await { onScreenId() == "story-1" }

        askToShow("story-2")
        firstContent.completeExceptionally(IOException("content fetch timed out"))
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { inAppFailureTracker.sendFailure("story-1", any(), any(), any()) }
        assertEquals("story-2", onScreenId())
        assertEquals(1, root.childCount)
        verify(exactly = 0) { host.onInAppDismissed("story-2") }
    }

    @Test
    fun `a story's loaded content reaches its page on the main thread`() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(Dispatchers.Unconfined)
        val content = CompletableDeferred<String>()
        coEvery { gatewayManager.fetchWebViewContent(contentUrlOf("story-1")) } coAnswers { content.await() }
        askToShow("story-1")
        await { onScreenId() == "story-1" }
        val story = onScreen()!!

        content.complete("<html>story</html>")
        val armedBeforeTheMainThreadRan = initTimeoutOf(story) != null
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(armedBeforeTheMainThreadRan)
        assertNotNull(initTimeoutOf(story))
    }

    @Test
    fun `a story whose page never sends init times out once and closes`() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(Dispatchers.Unconfined)
        askToShow("story-1")
        await { onScreenId() == "story-1" }
        await { initTimeoutOf(onScreen()!!) != null }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Constants.WebView.readyTimeout.interval + 1_000))

        verify(exactly = 1) { inAppFailureTracker.sendFailure("story-1", any(), any(), any()) }
        assertNull(onScreenId())
    }

    @Test
    fun `a story replaced before its init timeout reports nothing when the timeout comes due`() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(Dispatchers.Unconfined)
        askToShow("story-1")
        await { onScreenId() == "story-1" }
        await { initTimeoutOf(onScreen()!!) != null }

        askToShow("story-2")
        await { onScreenId() == "story-2" }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Constants.WebView.readyTimeout.interval + 1_000))

        verify(exactly = 0) { inAppFailureTracker.sendFailure("story-1", any(), any(), any()) }
    }
}
