package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.clearPreferences
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import cloud.mindbox.mobile_sdk.setDatabaseTestMode
import cloud.mindbox.mobile_sdk.utils.Constants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The whole real chain — block view → content controller → WebView holder → WebView controller →
 * `android.webkit.WebView` with a page that speaks the bridge — must be collectable once the block
 * leaves, in every way a block can leave: after the page rendered, mid-fetch (without waiting for
 * the download), mid-page-load, after
 * a storm of re-deliveries, after a silent page's timeout, with a data-push ack pending, after a
 * background/foreground round trip, and twenty at once. Releases are awaited through the real main
 * looper (the test thread only polls and collects), never by sleeping on main.
 *
 * Pages come from a loopback HTTP server inside the test process; the SDK's DI is the real one
 * (`Mindbox.initComponents`), only the registry that hands content to the block is a fake, so the
 * test does not depend on a mobile config.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedBlockChainReleaseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private lateinit var server: PageServer

    // Weak: a test field holding the finished Activity would pin its view tree — and through the
    // block view's content, the WebView and the holder (F3) — which is exactly what is under test.
    private lateinit var screen: WeakReference<ChainReleaseHostActivity>
    private val activity: ChainReleaseHostActivity
        get() = checkNotNull(screen.get()) { "the screen is gone" }
    private val registry = HandleRegistry()
    private val loads = AtomicInteger()
    private val fails = AtomicInteger()
    private var nextInAppId = 0

    @Before
    fun setUp() {
        setDatabaseTestMode(true)
        Mindbox.initComponents(context)
        server = PageServer().also { it.start() }
        DbManager.saveConfigurations(
            Configuration(
                previousInstallationId = "",
                previousDeviceUUID = "",
                endpointId = "chain-release",
                domain = server.base,
                packageName = context.packageName,
                versionName = "1",
                versionCode = "1",
                subscribeCustomerIfCreated = false,
                shouldCreateCustomer = false,
            ),
        )
        screen = WeakReference(launchHost())
    }

    @After
    fun tearDown() {
        if (screen.get()?.isDestroyed == false) leave()
        server.stop()
        clearPreferences()
    }

    private fun launchHost(): ChainReleaseHostActivity =
        instrumentation.startActivitySync(
            Intent(context, ChainReleaseHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ChainReleaseHostActivity

    /** Finishes the screen the way a user leaving it does and waits for its destruction on main. */
    private fun leave() {
        onMain { screen.get()?.finish() }
        await(timeoutMs = 10_000) { screen.get()?.isDestroyed != false }
        instrumentation.waitForIdleSync()
    }

    /** Home and back, as a user would: the window goes away and returns, the screen stays. */
    private fun backgroundAndForeground() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        await(timeoutMs = 10_000) { !activity.hasWindowFocus() }
        sleepOffMain(1_000)
        context.startActivity(
            Intent(context, ChainReleaseHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        await(timeoutMs = 10_000) { activity.hasWindowFocus() }
        sleepOffMain(500)
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test
    fun leavingAfterThePageRenderedFreesTheChain() {
        val block = attachBlock()
        val chain = deliver(block, server.page(count = 2))
        awaitLoads(1)

        leave()

        assertReleased(chain)
        assertRegistriesEmpty()
    }

    @Test
    fun leavingWhileTheContentIsStillBeingFetchedFreesTheChainWithoutWaitingForTheFetch() {
        val block = attachBlock()
        val chain = deliver(block, server.page(count = 1, fetchDelayMs = 10_000))
        sleepOffMain(300)

        leave()

        assertReleased(chain, withinMs = 4_000)
        assertRegistriesEmpty()
    }

    @Test
    fun leavingWhileThePageIsStillLoadingItsScriptFreesTheChain() {
        val block = attachBlock()
        val chain = deliver(block, server.page(count = 1, slowScript = true))
        sleepOffMain(500)

        leave()

        assertReleased(chain)
        assertRegistriesEmpty()
    }

    @Test
    fun tenRedeliveriesWithAChangingPageAddressLeaveNothingBehind() {
        val block = attachBlock()
        val chains = (1..10).map { version -> deliver(block, server.page(count = 1, version = version)) }
        awaitLoads(1)

        leave()

        chains.forEach { chain -> assertReleased(chain) }
        assertRegistriesEmpty()
    }

    @Test
    fun aPageThatNeverAnswersIsFreedAfterItsTimeoutAndTheLeave() {
        val block = attachBlock(readyTimeout = Milliseconds(1_000L))
        val chain = deliver(block, server.page(count = 1, silent = true))
        await(timeoutMs = 5_000) { fails.get() == 1 }

        leave()

        assertReleased(chain)
        assertRegistriesEmpty()
    }

    @Test
    fun leavingWhileADataPushWaitsForItsAckFreesTheChain() {
        val block = attachBlock()
        val page = server.page(count = 1, ackDelayMs = 3_000)
        val chain = deliver(block, page, params = mapOf("v" to "1"))
        awaitLoads(1)
        deliver(block, page, params = mapOf("v" to "2"))
        sleepOffMain(300)

        leave()

        assertReleased(chain)
        assertRegistriesEmpty()
    }

    @Test
    fun backgroundAndForegroundKeepTheChainAndTheLeaveFreesIt() {
        val block = attachBlock()
        val chain = deliver(block, server.page(count = 1))
        awaitLoads(1)

        backgroundAndForeground()

        collect()
        assertEquals(CHAIN_LINKS, chain.alive())
        assertEquals(1, loads.get())

        leave()

        assertReleased(chain)
        assertRegistriesEmpty()
    }

    @Test
    fun twentyBlocksCreatedAndDestroyedLeaveTheRegistriesEmpty() {
        val blocks = attachBlocks(count = 20)
        val chains = blocks.map { block -> deliver(block, server.page(count = 1)) }
        awaitLoads(20, timeoutMs = 30_000)

        leave()

        chains.forEach { chain -> assertReleased(chain) }
        assertRegistriesEmpty()
    }

    // --- the block under test -------------------------------------------------------------

    /** Weak on purpose: a strong local in the test method would keep the view — and its chain — alive. */
    private fun attachBlock(readyTimeout: Milliseconds = Constants.WebView.readyTimeout): WeakReference<MindboxEmbeddedBlockView> {
        lateinit var block: WeakReference<MindboxEmbeddedBlockView>
        onMain {
            val created = newBlock(activity, readyTimeout)
            activity.container.addView(created, ViewGroup.LayoutParams.MATCH_PARENT, BLOCK_HEIGHT_PX)
            block = WeakReference(created)
        }
        instrumentation.waitForIdleSync()
        return block
    }

    private fun attachBlocks(count: Int): List<WeakReference<MindboxEmbeddedBlockView>> {
        val blocks = mutableListOf<WeakReference<MindboxEmbeddedBlockView>>()
        onMain {
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            repeat(count) {
                val block = newBlock(activity, Constants.WebView.readyTimeout)
                column.addView(block, ViewGroup.LayoutParams.MATCH_PARENT, BLOCK_HEIGHT_PX)
                blocks.add(WeakReference(block))
            }
            activity.container.addView(
                ScrollView(activity).apply { addView(column) },
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        instrumentation.waitForIdleSync()
        return blocks
    }

    private fun newBlock(activity: Activity, readyTimeout: Milliseconds): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            context = activity,
            attrs = null,
            placeSystemName = PLACE,
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
            contentController = EmbeddedBlockContentController(
                placeSystemName = PLACE,
                readyTimeout = readyTimeout,
                providerFactory = { content, selectionTime ->
                    EmbeddedBlockContentFactory.createProvider(activity, content, selectionTime)
                },
                blocksRegistry = { registry },
            ),
            contentStore = { MindboxDI.appModule.embeddedBlockContentStore },
        ).apply {
            setListener(
                object : MindboxEmbeddedBlockListener {
                    override fun onLoad(view: MindboxEmbeddedBlockView) {
                        loads.incrementAndGet()
                    }

                    override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {
                        fails.incrementAndGet()
                    }
                },
            )
        }

    /** Hands [contentUrl] to the block the way the registry would and captures the chain it builds. */
    private fun deliver(
        blockReference: WeakReference<MindboxEmbeddedBlockView>,
        contentUrl: String,
        params: Map<String, String> = emptyMap(),
    ): Chain {
        val content = InAppType.Embedded(
            inAppId = "chain-${nextInAppId++}",
            placeSystemName = PlaceKey.of(PLACE),
            layers = listOf(
                Layer.WebViewLayer(baseUrl = server.base, contentUrl = contentUrl, type = "webview", params = params),
            ),
            frequency = Frequency(Frequency.Delay.Unlimited),
            tags = null,
            isPriority = false,
        )
        lateinit var chain: Chain
        onMain {
            val block = checkNotNull(blockReference.get()) { "the block was collected while still on screen" }
            registry.handleOf(block).onContentResolved(
                content,
                EmbeddedPlaceAnswer(sessionEpoch = SessionEpoch(0L), isByOperation = false, selectionTime = Milliseconds(0L)),
            )
            chain = Chain.of(block)
        }
        instrumentation.waitForIdleSync()
        return chain
    }

    private fun awaitLoads(expected: Int, timeoutMs: Long = 15_000) {
        await(timeoutMs) { loads.get() >= expected }
    }

    // --- release checks --------------------------------------------------------------------

    private class Chain(private val links: Map<String, WeakReference<Any>>) {

        fun alive(): List<String> = links.filterValues { reference -> reference.get() != null }.keys.toList()

        companion object {
            fun of(block: MindboxEmbeddedBlockView): Chain {
                val links = linkedMapOf<String, WeakReference<Any>>("view" to WeakReference(block))
                val controller = block.field("contentController")
                controller?.let { links["controller"] = WeakReference(it) }
                val holder = controller?.field("provider")
                holder?.let { links["holder"] = WeakReference(it) }
                val webViewController = holder?.field("webViewController")
                webViewController?.let { links["webViewController"] = WeakReference(it) }
                webViewController?.field("webView")?.let { links["webView"] = WeakReference(it) }
                return Chain(links)
            }
        }
    }

    private fun assertReleased(chain: Chain, withinMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + withinMs
        while (chain.alive().isNotEmpty() && System.currentTimeMillis() < deadline) {
            collect()
        }
        assertEquals("still reachable after ${withinMs}ms", emptyList<String>(), chain.alive())
    }

    private fun assertRegistriesEmpty() {
        val appModule = MindboxDI.appModule
        val pages = appModule.webPageRegistry.field("pages") as List<*>
        val livePages = pages.count { (it as? WeakReference<*>)?.get() != null }
        assertEquals("web pages still registered", 0, livePages)
        assertEquals("kept contents", 0, appModule.embeddedBlockContentStore.size)
        assertTrue("handles still registered: ${registry.size}", registry.size == 0)
    }

    /** A few collections with the main looper running freely in between. */
    private fun collect() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            sleepOffMain(150)
        }
        instrumentation.waitForIdleSync()
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("condition not met within ${timeoutMs}ms")
            sleepOffMain(50)
        }
    }

    private fun sleepOffMain(ms: Long) {
        check(Thread.currentThread() !== instrumentation.targetContext.mainLooper.thread) { "never sleep on main" }
        Thread.sleep(ms)
    }

    // --- fakes -----------------------------------------------------------------------------

    private class HandleRegistry : EmbeddedBlocksRegistry {
        private val handles = linkedMapOf<EmbeddedBlockHandle, Unit>()

        val size: Int get() = synchronized(handles) { handles.size }

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            synchronized(handles) { handles[handle] = Unit }
            return Closeable { synchronized(handles) { handles.remove(handle) } }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) = Unit

        override fun onBlockContentDropped(placeSystemName: PlaceKey) = Unit

        override fun isLiveSession(sessionEpoch: SessionEpoch): Boolean = true

        override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer) =
            PlaceShowReservation.RESERVED

        override fun giveBackParkedShow(placeSystemName: PlaceKey, sessionEpoch: SessionEpoch) = Unit

        override fun deferUntilReturnChecked(): Boolean = false

        override fun onAppResumedOn(activity: Activity) = Unit

        override fun onReturnCheckOver() = Unit

        override fun startListening() = Unit

        fun handleOf(block: MindboxEmbeddedBlockView): EmbeddedBlockHandle {
            val controller = block.field("contentController")
            return synchronized(handles) { handles.keys.first { handle -> handle === controller } }
        }
    }

    /** Loopback HTTP: `/page?count=&silent=&ackDelay=&slowjs=&v=&delay=` and `/slow.js?delay=`. */
    private class PageServer {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool()

        @Volatile
        private var isRunning = true

        val base: String get() = "http://127.0.0.1:${socket.localPort}/"

        fun page(
            count: Int,
            silent: Boolean = false,
            ackDelayMs: Long = 0,
            slowScript: Boolean = false,
            version: Int = 0,
            fetchDelayMs: Long = 0,
        ): String = base + "page?count=$count&silent=${if (silent) 1 else 0}&ackDelay=$ackDelayMs" +
            "&slowjs=${if (slowScript) 1 else 0}&v=$version&delay=$fetchDelayMs"

        fun start() {
            Thread {
                while (isRunning) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    // A WebView torn down mid-download closes its side first: a broken pipe here is
                    // the scenario working, not a failure of the server.
                    pool.execute { runCatching { serve(client) } }
                }
            }.start()
        }

        fun stop() {
            isRunning = false
            runCatching { socket.close() }
            pool.shutdownNow()
        }

        private fun serve(client: Socket): Unit = client.use { connection ->
            val reader = connection.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return@use
            while (!reader.readLine().isNullOrEmpty()) Unit
            val target = requestLine.split(" ").getOrNull(1) ?: return@use
            val route = target.substringBefore("?")
            val query = target.substringAfter("?", "")
            val params = query.split("&").filter { it.contains("=") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
            params["delay"]?.toLongOrNull()?.takeIf { it > 0 }?.let { Thread.sleep(it) }
            val (status, type, body) = when (route) {
                "/page" -> Triple(200, "text/html", html(params))
                "/slow.js" -> Triple(200, "application/javascript", "window.slowScriptLoaded=true;")
                else -> Triple(404, "text/plain", "")
            }
            val bytes = body.toByteArray()
            val output = connection.getOutputStream()
            output.write(
                ("HTTP/1.1 $status OK\r\nContent-Type: $type; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            output.write(bytes)
            output.flush()
        }

        private fun html(params: Map<String, String>): String {
            val count = params["count"]?.toIntOrNull() ?: 1
            val silent = params["silent"] == "1"
            val ackDelay = params["ackDelay"]?.toLongOrNull() ?: 0L
            val slowScript = if (params["slowjs"] == "1") """<script src="slow.js?delay=2000"></script>""" else ""
            return """<!doctype html><html><head><meta charset="utf-8"></head><body><div id="s">chain</div>
$slowScript
<script>
(function(){
  var pending = {};
  function uuid(){ return 'id-' + Math.random().toString(16).slice(2) + '-' + Date.now(); }
  function post(m){ SdkBridge.postMessage(JSON.stringify(m)); }
  function req(a, p, cb){ var id = uuid(); if (cb) pending[id] = cb;
    post({version:1, type:'request', action:a, payload:JSON.stringify(p || {}), id:id, timestamp:Date.now()}); }
  function resp(m){ post({version:1, type:'response', action:m.action, payload:'{"success":true}', id:m.id, timestamp:Date.now()}); }
  window.bridgeMessagesHandlers = { emit: function(j){
    var m = JSON.parse(j);
    if (m.type === 'response' || m.type === 'error') { var c = pending[m.id]; delete pending[m.id]; if (c) c(m); return; }
    if (m.type === 'request') {
      if (m.action === 'initDataUpdated' && $ackDelay > 0) { setTimeout(function(){ resp(m); }, $ackDelay); return; }
      resp(m);
    }
  }};
  if (!$silent) { req('ready', {}, function(){ req('init', {}, function(){ req('contentRendered', {count: $count}); }); }); }
})();
</script></body></html>"""
        }
    }

    private companion object {
        const val PLACE = "chain-release"
        const val BLOCK_HEIGHT_PX = 300
        val CHAIN_LINKS = listOf("view", "controller", "holder", "webViewController", "webView")

        fun Any.field(name: String): Any? {
            var type: Class<*>? = javaClass
            while (type != null) {
                val found = runCatching { type!!.getDeclaredField(name) }.getOrNull()
                if (found != null) {
                    found.isAccessible = true
                    return found.get(this)
                }
                type = type.superclass
            }
            return null
        }
    }
}
