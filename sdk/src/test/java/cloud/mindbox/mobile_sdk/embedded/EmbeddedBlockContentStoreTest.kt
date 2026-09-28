package cloud.mindbox.mobile_sdk.embedded

import android.content.ComponentCallbacks2
import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PlaceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.Closeable

/**
 * The store of contents kept for screens that left the window but not the back stack: who gets
 * what back, when a kept content is freed, and how the limit is applied.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentStoreTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) = Unit

        override fun onBlockContentDropped(placeSystemName: PlaceKey) = Unit

        override fun startListening() = Unit
    }

    private class FakeProvider : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View? = View(ApplicationProvider.getApplicationContext())
        var releaseCount = 0

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() {
            releaseCount++
        }
    }

    private class Screen : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }

        override val lifecycle: Lifecycle
            get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private class Kept(val controller: EmbeddedBlockContentController, val provider: FakeProvider)

    private fun readyContent(place: PlaceKey = PLACE): Kept {
        val registry = FakeBlocksRegistry()
        lateinit var provider: FakeProvider
        val controller = EmbeddedBlockContentController(
            placeSystemName = place.value,
            providerFactory = { _, _ -> FakeProvider().also { provider = it } },
            blocksRegistry = { registry },
        )
        controller.start()
        registry.lastHandle?.onContentResolved(InAppStub.getEmbedded() as InAppType.Embedded)
        idle()
        return Kept(controller, provider)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val store = EmbeddedBlockContentStore(maxRetained = 3)

    @Test
    fun `a kept content comes back to the same screen and place`() {
        val screen = Screen()
        val kept = readyContent()

        store.retain(screen, PLACE, kept.controller, activity = null)

        assertSame(kept.controller, store.reclaim(screen, PLACE, activity = null))
        assertEquals(0, store.size)
        assertEquals(0, kept.provider.releaseCount)
    }

    @Test
    fun `content that stopped being showable while kept is freed instead of handed back`() {
        val screen = Screen()
        val kept = readyContent()
        store.retain(screen, PLACE, kept.controller, activity = null)

        kept.provider.onStateChange?.invoke(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))

        assertNull(store.reclaim(screen, PLACE, activity = null))
        assertEquals(0, store.size)
        assertEquals(1, kept.provider.releaseCount)
    }

    @Test
    fun `another screen or another place gets nothing`() {
        val screen = Screen()
        store.retain(screen, PLACE, readyContent().controller, activity = null)

        assertNull(store.reclaim(Screen(), PLACE, activity = null))
        assertNull(store.reclaim(screen, PlaceKey.of("other-place"), activity = null))
        assertEquals(1, store.size)
    }

    @Test
    fun `a screen with two blocks on one place gets its contents back last kept first`() {
        // The blocks leave in reverse order of attachment and come back in attachment order.
        val screen = Screen()
        val lower = readyContent()
        val upper = readyContent()
        store.retain(screen, PLACE, lower.controller, activity = null)
        store.retain(screen, PLACE, upper.controller, activity = null)

        assertSame(upper.controller, store.reclaim(screen, PLACE, activity = null))
        assertSame(lower.controller, store.reclaim(screen, PLACE, activity = null))
        assertNull(store.reclaim(screen, PLACE, activity = null))
    }

    @Test
    fun `the screen leaving the back stack frees its content`() {
        val screen = Screen()
        val kept = readyContent()
        store.retain(screen, PLACE, kept.controller, activity = null)

        screen.destroy()

        assertEquals(0, store.size)
        assertEquals(1, kept.provider.releaseCount)
        assertNull(store.reclaim(screen, PLACE, activity = null))
    }

    @Test
    fun `a content for a screen that is already destroyed is freed at once`() {
        val screen = Screen().apply { destroy() }
        val kept = readyContent()

        store.retain(screen, PLACE, kept.controller, activity = null)

        assertEquals(0, store.size)
        assertEquals(1, kept.provider.releaseCount)
    }

    @Test
    fun `over the limit the content untouched the longest goes, a frame later`() {
        val screens = List(4) { Screen() }
        val contents = List(4) { readyContent() }
        screens.zip(contents).forEach { (screen, kept) ->
            store.retain(screen, PLACE, kept.controller, activity = null)
        }
        // The limit waits for the next frame: a popped screen's content passes through the store
        // for a moment and must not push out a content a live screen is waiting for.
        assertEquals(4, store.size)

        idle()

        assertEquals(3, store.size)
        assertEquals(1, contents[0].provider.releaseCount)
        assertNull(store.reclaim(screens[0], PLACE, activity = null))
        assertSame(contents[1].controller, store.reclaim(screens[1], PLACE, activity = null))
    }

    @Test
    fun `reclaiming and keeping again makes a content the freshest`() {
        val screens = List(4) { Screen() }
        val contents = List(4) { readyContent() }
        (0..2).forEach { index -> store.retain(screens[index], PLACE, contents[index].controller, activity = null) }

        // The first screen comes back and leaves again: its content is now the most recent touch.
        store.reclaim(screens[0], PLACE, activity = null)
        store.retain(screens[0], PLACE, contents[0].controller, activity = null)
        store.retain(screens[3], PLACE, contents[3].controller, activity = null)
        idle()

        assertEquals(3, store.size)
        assertEquals(1, contents[1].provider.releaseCount)
        assertEquals(0, contents[0].provider.releaseCount)
        assertSame(contents[0].controller, store.reclaim(screens[0], PLACE, activity = null))
    }

    @Test
    fun `a popped screen passing through the store does not evict a live one`() {
        val live = List(3) { Screen() }
        val liveContents = List(3) { readyContent() }
        live.zip(liveContents).forEach { (screen, kept) -> store.retain(screen, PLACE, kept.controller, activity = null) }
        val popped = Screen()
        val poppedContent = readyContent()

        store.retain(popped, PLACE, poppedContent.controller, activity = null)
        popped.destroy()
        idle()

        assertEquals(3, store.size)
        assertEquals(1, poppedContent.provider.releaseCount)
        liveContents.forEach { kept -> assertEquals(0, kept.provider.releaseCount) }
    }

    @Test
    fun `memory pressure in the background frees everything, merely hiding the UI frees nothing`() {
        val contents = List(2) { readyContent() }
        contents.forEach { kept -> store.retain(Screen(), PLACE, kept.controller, activity = null) }

        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(2, store.size)

        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertEquals(0, store.size)
        contents.forEach { kept -> assertEquals(1, kept.provider.releaseCount) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `running low on memory in the foreground frees everything, moderate pressure frees nothing`() {
        val contents = List(2) { readyContent() }
        contents.forEach { kept -> store.retain(Screen(), PLACE, kept.controller, activity = null) }

        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(2, store.size)

        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(0, store.size)
        contents.forEach { kept -> assertEquals(1, kept.provider.releaseCount) }
    }

    @Test
    fun `a content kept on one activity is not handed to a block on another`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val other = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val screen = Screen()
        store.retain(screen, PLACE, readyContent().controller, activity)

        assertNull(store.reclaim(screen, PLACE, other))
        assertEquals(1, store.size)
    }

    @Test
    fun `the activity being destroyed frees the content kept on it`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val kept = readyContent()
        store.retain(Screen(), PLACE, kept.controller, controller.get())

        controller.pause().stop().destroy()

        assertEquals(0, store.size)
        assertEquals(1, kept.provider.releaseCount)
    }

    private companion object {
        val PLACE = PlaceKey.of("main-screen-top")
    }
}
