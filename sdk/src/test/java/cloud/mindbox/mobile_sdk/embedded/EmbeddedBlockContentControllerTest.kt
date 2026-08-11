package cloud.mindbox.mobile_sdk.embedded

import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentControllerTest {

    private class FakeProvider : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        var view: View? = null
        override val contentView: View?
            get() = view
        var startCount = 0
        var pauseCount = 0
        var releaseCount = 0

        override fun start() {
            startCount++
        }

        override fun pause() {
            pauseCount++
        }

        override fun release() {
            releaseCount++
        }

        fun report(state: EmbeddedBlockState) {
            onStateChange?.invoke(state)
        }
    }

    private val configFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val sessionListeners = mutableListOf<() -> Unit>()
    private val requestedPlaces = mutableListOf<String>()
    private val states = mutableListOf<EmbeddedBlockState>()

    private val sessionStorage: SessionStorageManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher())

        mockkObject(MindboxPreferences)
        every { MindboxPreferences.inAppConfigFlow } returns configFlow

        mockkObject(MindboxEventManager)
        every { MindboxEventManager.embeddedPlaceRequested(any()) } answers {
            requestedPlaces.add(firstArg())
            Unit
        }

        every { sessionStorage.addSessionExpirationListener(any()) } answers {
            sessionListeners.add(firstArg())
            Unit
        }
        every { sessionStorage.removeSessionExpirationListener(any()) } answers {
            sessionListeners.remove(firstArg())
            Unit
        }
        val appModule: AppModule = mockk(relaxed = true)
        every { appModule.sessionStorageManager } returns sessionStorage
        mockkObject(MindboxDI)
        every { MindboxDI.isInitialized() } returns true
        every { MindboxDI.appModule } returns appModule
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun controller(
        timeout: Milliseconds = Milliseconds(10_000L),
        placeSystemName: String? = "test-place",
        resolve: () -> EmbeddedContentResolution,
    ): EmbeddedBlockContentController = EmbeddedBlockContentController(
        resolveFactory = resolve,
        placeSystemName = placeSystemName,
        readyTimeout = timeout,
    ).apply { onStateChange = { states.add(it) } }

    private fun content(provider: EmbeddedContentProvider): EmbeddedContentResolution =
        EmbeddedContentResolution.Content(provider)

    /** The SDK is not up yet: no graph to take a session listener from. */
    private fun withoutDi() {
        every { MindboxDI.isInitialized() } returns false
    }

    private fun deliverConfig() {
        configFlow.tryEmit("{}")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun expireSession() {
        sessionListeners.toList().forEach { it() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `resolved content is started and its states are forwarded`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }

        controller.start()
        provider.report(EmbeddedBlockState.Ready)

        assertEquals(1, provider.startCount)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `the content view is the provider's own`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()

        assertNull(controller.contentView)

        provider.view = View(ApplicationProvider.getApplicationContext())
        assertNotNull(controller.contentView)
    }

    @Test
    fun `a place with nothing configured reports Empty`() {
        val controller = controller { EmbeddedContentResolution.NothingToShow }

        controller.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty), states)
        assertNull(controller.contentView)
    }

    @Test
    fun `a resolution that throws reports Failed`() {
        val controller = controller { error("factory blew up") }

        controller.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Failed), states)
    }

    @Test
    fun `a config that has not arrived keeps the block loading instead of failing it`() {
        val controller = controller { EmbeddedContentResolution.NotReadyYet }

        controller.start()

        // The SDK may still be starting up: an absent config is not an empty place, and the
        // block must not burn its one public failure on it.
        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
        assertEquals(1, configFlow.subscriptionCount.value)
    }

    @Test
    fun `the arriving config fills the place without a re-attach`() {
        val provider = FakeProvider()
        var ready = false
        val controller = controller {
            if (ready) content(provider) else EmbeddedContentResolution.NotReadyYet
        }
        controller.start()
        assertEquals(0, provider.startCount)

        ready = true
        deliverConfig()

        assertEquals(1, provider.startCount)
        // The subscription did its job and is dropped — the block is not a permanent listener.
        assertEquals(0, configFlow.subscriptionCount.value)
    }

    @Test
    fun `a config arriving while the block is off screen resolves the content but keeps it paused`() {
        val provider = FakeProvider()
        var ready = false
        val controller = controller {
            if (ready) content(provider) else EmbeddedContentResolution.NotReadyYet
        }
        controller.start()
        controller.pause()

        ready = true
        deliverConfig()

        // Resolved so the next appearance is instant, but nothing runs off screen.
        assertEquals(0, provider.startCount)
        assertEquals(1, provider.pauseCount)

        controller.start()
        assertEquals(1, provider.startCount)
    }

    @Test
    fun `a config arriving after release cannot resurrect the block`() {
        val provider = FakeProvider()
        var ready = false
        val controller = controller {
            if (ready) content(provider) else EmbeddedContentResolution.NotReadyYet
        }
        controller.start()
        controller.release()

        ready = true
        deliverConfig()

        // The host screen is gone: building a WebView for it would leak it outright.
        assertEquals(0, provider.startCount)
        assertEquals(0, configFlow.subscriptionCount.value)
    }

    @Test
    fun `waiting for the config takes exactly one subscription`() {
        val controller = controller { EmbeddedContentResolution.NotReadyYet }

        controller.start()
        controller.pause()
        controller.start()

        assertEquals(1, configFlow.subscriptionCount.value)
    }

    @Test
    fun `a config source that blows up does not break the block`() {
        every { MindboxPreferences.inAppConfigFlow } throws IllegalStateException("no preferences")
        val controller = controller { EmbeddedContentResolution.NotReadyYet }

        controller.start()
        controller.release()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
    }

    @Test
    fun `resolved content is reused across appearances`() {
        val provider = FakeProvider()
        var resolves = 0
        val controller = controller {
            resolves++
            content(provider)
        }

        controller.start()
        controller.pause()
        controller.start()

        // A pause is not a teardown: the same page is resumed, never resolved twice.
        assertEquals(1, resolves)
        assertEquals(2, provider.startCount)
    }

    @Test
    fun `the place asks for content only while it has none`() {
        val provider = FakeProvider()
        var ready = false
        val controller = controller {
            if (ready) content(provider) else EmbeddedContentResolution.NotReadyYet
        }

        controller.start()
        controller.pause()
        controller.start()
        assertEquals(listOf("test-place", "test-place"), requestedPlaces)

        // Once the place is filled there is nothing left to ask for.
        ready = true
        deliverConfig()
        controller.pause()
        controller.start()

        assertEquals(listOf("test-place", "test-place"), requestedPlaces)
    }

    @Test
    fun `a nameless block has nothing to ask about`() {
        val controller = controller(placeSystemName = null) { EmbeddedContentResolution.NothingToShow }

        controller.start()

        assertTrue(requestedPlaces.isEmpty())
    }

    @Test
    fun `pause quiets the content`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()

        controller.pause()

        assertEquals(1, provider.pauseCount)
    }

    @Test
    fun `release frees the content and unsubscribes from everything`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()

        controller.release()

        assertEquals(1, provider.releaseCount)
        assertTrue(sessionListeners.isEmpty())
    }

    @Test
    fun `a released block stays released`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()
        controller.release()
        states.clear()

        controller.start()
        expireSession()

        // release() is the host screen's death — nothing may bring the block back.
        assertEquals(1, provider.startCount)
        assertTrue(states.isEmpty())
    }

    @Test
    fun `a new session reloads the content live`() {
        val providers = mutableListOf<FakeProvider>()
        val controller = controller { content(FakeProvider().also { providers.add(it) }) }
        controller.start()

        expireSession()

        // The old page is released for good (not paused — nobody will resume it) and a fresh
        // one takes over without a re-attach.
        assertEquals(2, providers.size)
        assertEquals(1, providers[0].releaseCount)
        assertEquals(1, providers[1].startCount)
    }

    @Test
    fun `a new session while off screen drops the content and reloads it on the next appearance`() {
        val providers = mutableListOf<FakeProvider>()
        val controller = controller { content(FakeProvider().also { providers.add(it) }) }
        controller.start()
        controller.pause()

        expireSession()

        // Nothing loads off screen…
        assertEquals(1, providers.size)
        assertEquals(1, providers[0].releaseCount)

        controller.start()

        // …but the stale content is gone, so the next appearance resolves fresh.
        assertEquals(2, providers.size)
        assertEquals(1, providers[1].startCount)
    }

    @Test
    fun `a new session asks the place what to show now`() {
        val controller = controller { content(FakeProvider()) }
        controller.start()
        requestedPlaces.clear()

        expireSession()

        assertEquals(listOf("test-place"), requestedPlaces)
    }

    @Test
    fun `a block that started before the SDK still catches the session listener later`() {
        withoutDi()
        val provider = FakeProvider()
        var ready = false
        val controller = controller {
            if (ready) content(provider) else EmbeddedContentResolution.NotReadyYet
        }
        controller.start()
        assertTrue(sessionListeners.isEmpty())

        // A config can only arrive through a live SDK, so the graph is up by the time it does.
        every { MindboxDI.isInitialized() } returns true
        ready = true
        deliverConfig()

        // Without this the block would never reload on a new session: it sits on screen and has
        // no next appearance to retry the subscription on.
        assertEquals(1, sessionListeners.size)
    }

    @Test
    fun `a missing DI graph does not break the block`() {
        withoutDi()
        val provider = FakeProvider()
        val controller = controller { content(provider) }

        controller.start()
        controller.release()

        assertEquals(1, provider.startCount)
        assertEquals(1, provider.releaseCount)
    }

    @Test
    fun `a silent page times out into a failure and stops listening for sessions`() {
        val provider = FakeProvider()
        val controller = controller(timeout = Milliseconds(1_000L)) { content(provider) }
        controller.start()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100L))

        // Paused before the failure is reported, so a late page cannot resurrect the block —
        // and a new session must not restart a block the host already saw fail.
        assertEquals(1, provider.pauseCount)
        assertEquals(EmbeddedBlockState.Failed, states.last())
        assertTrue(sessionListeners.isEmpty())
    }

    @Test
    fun `a page that answers in time never times out`() {
        val provider = FakeProvider()
        val controller = controller(timeout = Milliseconds(1_000L)) { content(provider) }
        controller.start()
        provider.report(EmbeddedBlockState.Ready)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000L))

        assertEquals(0, provider.pauseCount)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `leaving the screen cancels the pending timeout`() {
        val provider = FakeProvider()
        val controller = controller(timeout = Milliseconds(1_000L)) { content(provider) }
        controller.start()

        controller.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000L))

        // Only the pause from leaving the screen — a block nobody looks at cannot be late.
        assertEquals(1, provider.pauseCount)
        assertTrue(states.none { it is EmbeddedBlockState.Failed })
    }

    @Test
    fun `waiting for the config is not on the page's clock`() {
        val controller = controller(timeout = Milliseconds(1_000L)) {
            EmbeddedContentResolution.NotReadyYet
        }
        controller.start()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5_000L))

        // The budget covers rendering a page, and there is no page yet: an SDK that starts up
        // slowly must not turn every block on the screen into a failure.
        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
    }

    @Test
    fun `content that replaces the old one gets a full budget, not its leftovers`() {
        val controller = controller(timeout = Milliseconds(1_000L)) { content(FakeProvider()) }
        controller.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600L))

        // The session turns over mid-load: the old page's clock dies with it.
        expireSession()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600L))

        // The old budget would have expired by now; the fresh page still has time left.
        assertTrue(states.none { it is EmbeddedBlockState.Failed })

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500L))
        assertEquals(EmbeddedBlockState.Failed, states.last())
    }

    @Test
    fun `the same loading state is not reported twice`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }

        controller.start()
        provider.report(EmbeddedBlockState.Loading)
        provider.report(EmbeddedBlockState.Loading)

        // The container turns a Loading into a placeholder swap; repeating it is pure churn.
        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
    }

    @Test
    fun `a page reporting its height again does not re-report Ready`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()

        provider.report(EmbeddedBlockState.Ready)
        repeat(5) { provider.report(EmbeddedBlockState.Ready) }

        // A live page reports its height on every relayout; the container is already showing it.
        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Ready), states)
    }

    @Test
    fun `a resumed page does not re-report the state the container already shows`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()
        provider.report(EmbeddedBlockState.Ready)
        controller.pause()
        states.clear()

        // start() resumes the page, and the page replays where it stands.
        controller.start()
        provider.report(EmbeddedBlockState.Ready)

        assertTrue(states.isEmpty())
    }

    @Test
    fun `loading after a resolved state is reported again`() {
        val provider = FakeProvider()
        val controller = controller { content(provider) }
        controller.start()
        provider.report(EmbeddedBlockState.Ready)

        provider.report(EmbeddedBlockState.Loading)

        // A reload is real news for the host: the block goes back to the placeholder.
        assertEquals(EmbeddedBlockState.Loading, states.last())
    }
}
