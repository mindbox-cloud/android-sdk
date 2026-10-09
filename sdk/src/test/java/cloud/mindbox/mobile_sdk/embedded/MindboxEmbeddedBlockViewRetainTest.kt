package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import cloud.mindbox.mobile_sdk.findActivity
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PlaceKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.Closeable

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewRetainTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        val handles = mutableListOf<EmbeddedBlockHandle>()
        var droppedCount = 0
        var appearedCount = 0

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            handles.add(handle)
            return Closeable { handles.remove(handle) }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) {
            appearedCount++
        }

        override fun onBlockContentDropped(placeSystemName: PlaceKey) {
            droppedCount++
        }

        override fun startListening() = Unit

        override fun isLiveSession(sessionEpoch: Long): Boolean = true

        override fun deferUntilReturnChecked(): Boolean = false

        override fun onAppResumedOn(activity: Activity) = Unit

        override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer) =
            PlaceShowReservation.RESERVED

        override fun onReturnCheckOver() = Unit
    }

    private class FakeProvider(context: Context) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)
        var startCount = 0
        var pauseCount = 0
        var releaseCount = 0

        override fun start() {
            startCount++
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() {
            pauseCount++
        }

        override fun release() {
            releaseCount++
        }
    }

    class BlockFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View = FrameLayout(requireContext()).apply {
            addView(blockFactory(requireContext()), 500, 300)
        }

        val block: MindboxEmbeddedBlockView
            get() = (requireView() as FrameLayout).getChildAt(0) as MindboxEmbeddedBlockView

        companion object {
            var blockFactory: (Context) -> MindboxEmbeddedBlockView = { context -> MindboxEmbeddedBlockView(context) }
        }
    }

    class DetailFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View = View(requireContext())
    }

    private val controller: ActivityController<FragmentActivity> =
        Robolectric.buildActivity(FragmentActivity::class.java).setup()
    private val activity: FragmentActivity = controller.get()
    private val container = FragmentContainerView(activity).apply { id = View.generateViewId() }
    private val fragmentManager get() = activity.supportFragmentManager

    private val blocksRegistry = FakeBlocksRegistry()
    private val store = EmbeddedBlockContentStore(maxRetained = 3)
    private val providers = mutableListOf<FakeProvider>()
    private val loads = mutableListOf<MindboxEmbeddedBlockView>()
    private val empties = mutableListOf<MindboxEmbeddedBlockView>()
    private var placeName = PLACE
    private var isAppInForeground = true
    private var placeRecords = ""
    private val placeMemory = EmbeddedBlockPlaceMemory(
        readRecordsJson = { placeRecords },
        writeRecordsJson = { json -> placeRecords = json },
    )

    private fun newBlock(context: Context): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            context,
            null,
            placeName,
            contentController = EmbeddedBlockContentController(
                placeSystemName = placeName,
                providerFactory = { _, _ -> FakeProvider(context).also { providers.add(it) } },
                blocksRegistry = { blocksRegistry },
                hostActivity = { context.findActivity() },
                isAppInForeground = { isAppInForeground },
            ),
            contentStore = { store },
            placeMemory = placeMemory,
        ).apply {
            setListener(
                object : MindboxEmbeddedBlockListener {
                    override fun onLoad(view: MindboxEmbeddedBlockView) {
                        loads.add(view)
                    }

                    override fun onEmpty(view: MindboxEmbeddedBlockView) {
                        empties.add(view)
                    }
                },
            )
        }

    init {
        BlockFragment.blockFactory = ::newBlock
        activity.setContentView(container)
    }

    @After
    fun tearDown() {
        BlockFragment.blockFactory = { context -> MindboxEmbeddedBlockView(context) }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun show(view: View) {
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
    }

    private fun openHome(): BlockFragment {
        val fragment = BlockFragment()
        fragmentManager.beginTransaction().add(container.id, fragment).commitNow()
        idle()
        show(fragment.block)
        return fragment
    }

    private fun deliverContent() {
        blocksRegistry.handles.toList().forEach { handle ->
            handle.onContentResolved(InAppStub.getEmbedded())
        }
        idle()
    }

    private fun goForward(next: Fragment = DetailFragment()) {
        fragmentManager.beginTransaction().replace(container.id, next).addToBackStack(null).commit()
        fragmentManager.executePendingTransactions()
        idle()
    }

    private fun goBack() {
        fragmentManager.popBackStackImmediate()
        idle()
    }

    private fun goToBackground(block: MindboxEmbeddedBlockView): EmbeddedBlockHandle {
        isAppInForeground = false
        dispatchWindowVisibility(block, View.GONE)
        idle()
        val handle = blocksRegistry.handles.single()
        assertTrue(handle.isPausedForBackground)
        return handle
    }

    private fun EmbeddedBlockHandle.refuseInNewSession() {
        onContentResolved(null, placeAnswer(sessionEpoch = 1L))
        idle()
    }

    private fun refuseThePlace() {
        blocksRegistry.handles.single().onContentResolved(null)
        idle()
    }

    private fun remembersThePlace(): Boolean = placeMemory.hasShownContent(PlaceKey.of(PLACE))

    @Test
    fun `the content stays with the fragment and comes back without a second load`() {
        val home = openHome()
        deliverContent()
        val firstBlock = home.block
        assertEquals(1, providers.size)
        assertEquals(listOf(firstBlock), loads)

        goForward()

        assertEquals(1, store.size)
        assertTrue(providers.single().pauseCount > 0)
        assertEquals(0, providers.single().releaseCount)
        assertEquals(1, blocksRegistry.handles.size)
        assertEquals(0, blocksRegistry.droppedCount)

        goBack()
        val secondBlock = home.block
        show(secondBlock)

        assertNotSame(firstBlock, secondBlock)
        assertEquals(1, providers.size)
        assertEquals(0, store.size)
        assertSame(secondBlock, providers.single().contentView.parent)
        assertEquals(listOf(firstBlock, secondBlock), loads)
    }

    @Test
    fun `the kept content comes back to a block naming the place in another letter case`() {
        placeName = "Main-Screen-Top"
        val home = openHome()
        deliverContent()
        val firstBlock = home.block

        goForward()
        assertEquals(1, store.size)

        placeName = PLACE
        goBack()
        val secondBlock = home.block
        show(secondBlock)

        assertNotSame(firstBlock, secondBlock)
        assertEquals(1, providers.size)
        assertEquals(0, store.size)
        assertSame(secondBlock, providers.single().contentView.parent)
    }

    @Test
    fun `content still loading is not kept`() {
        openHome()

        goForward()

        assertEquals(0, store.size)
        assertTrue(providers.isEmpty())
        assertTrue(blocksRegistry.handles.isEmpty())
    }

    @Test
    fun `a released block leaves nothing behind`() {
        val home = openHome()
        deliverContent()

        home.block.release()
        goForward()

        assertEquals(0, store.size)
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `a fragment popped for good frees its content`() {
        openHome()
        deliverContent()
        val catalog = BlockFragment()
        goForward(catalog)
        show(catalog.block)
        deliverContent()
        assertEquals(2, providers.size)
        assertEquals(1, store.size)

        goBack()

        assertEquals(1, providers[1].releaseCount)
        assertEquals(0, providers[0].releaseCount)
        assertEquals(0, store.size)
    }

    @Test
    fun `a block straight in an activity keeps nothing when the activity dies`() {
        val block = newBlock(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(block, 500, 300) })
        show(block)
        deliverContent()

        controller.pause().stop().destroy()
        idle()

        assertEquals(0, store.size)
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `an activity recreated on a configuration change keeps nothing`() {
        openHome()
        deliverContent()

        controller.recreate()
        idle()

        assertEquals(0, store.size)
        assertEquals("kept=${store.size} providers=${providers.size}", 1, providers.first().releaseCount)
    }

    @Test
    fun `letting a block go while its screen is still in front frees it`() {
        val home = openHome()
        deliverContent()

        home.block.releaseOrRetain()

        assertEquals(0, store.size)
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `letting a block go on a screen that stepped back keeps the content for it`() {
        val home = openHome()
        deliverContent()
        fragmentManager.beginTransaction().setMaxLifecycle(home, Lifecycle.State.STARTED).commitNow()

        home.block.releaseOrRetain()

        assertEquals(1, store.size)
        assertEquals(0, providers.single().releaseCount)
        assertTrue(providers.single().pauseCount > 0)
    }

    @Test
    fun `a late release on the view that left its content behind does not free it`() {
        val home = openHome()
        deliverContent()
        val firstBlock = home.block
        goForward()
        assertEquals(1, store.size)

        firstBlock.release()

        assertEquals(1, store.size)
        assertEquals(0, providers.single().releaseCount)
        goBack()
        show(home.block)
        assertSame(home.block, providers.single().contentView.parent)
    }

    @Test
    fun `letting a block go with nothing to keep releases it`() {
        val home = openHome()

        home.block.releaseOrRetain()

        assertEquals(0, store.size)
        assertTrue(blocksRegistry.handles.isEmpty())
    }

    @Test
    fun `a block away in background whose fragment is replaced has left its screen and comes back collapsed before its first frame when its place was refused meanwhile`() {
        val home = openHome()
        deliverContent()
        val handle = goToBackground(home.block)

        goForward()

        assertFalse(handle.isPausedForBackground)
        assertTrue(handle.isLeftBehind)
        assertEquals(1, store.size)

        handle.refuseInNewSession()
        isAppInForeground = true
        goBack()
        val returned = home.block
        dispatchWindowVisibility(returned, View.VISIBLE)

        assertEquals(View.GONE, returned.visibility)
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `a block away in background whose fragment is hidden has left its screen, stays stopped while hidden and comes back collapsed before its first frame when its place was refused meanwhile`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        val handle = goToBackground(block)

        fragmentManager.beginTransaction().hide(home).commitNow()
        idle()

        assertFalse(handle.isPausedForBackground)
        assertTrue(handle.isLeftBehind)

        isAppInForeground = true
        dispatchWindowVisibility(block, View.VISIBLE)
        idle()

        assertFalse(handle.isActive)

        handle.refuseInNewSession()
        fragmentManager.beginTransaction().show(home).commitNow()

        assertEquals(View.GONE, block.visibility)
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `a block let go by its wrapper while the app is in background leaves its screen once its view is taken out of the window`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        val handle = goToBackground(block)
        fragmentManager.beginTransaction().setMaxLifecycle(home, Lifecycle.State.STARTED).commitNow()
        block.releaseOrRetain()
        assertEquals(1, store.size)
        assertTrue(handle.isPausedForBackground)

        (home.requireView() as ViewGroup).removeView(block)

        assertFalse(handle.isPausedForBackground)
        assertTrue(handle.isLeftBehind)
    }

    @Test
    fun `an activity recreated for a configuration change while the app is in background does not count as its block leaving the screen`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        blocksRegistry.handles.single().onContentResolved(null)
        idle()
        goToBackground(block)

        controller.recreate()
        idle()

        assertEquals(View.VISIBLE, block.visibility)
        assertTrue(empties.isEmpty())
    }

    @Test
    fun `a block holding the collapse of its refused place tells its host it is empty as the user moves on to another fragment`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        refuseThePlace()
        assertEquals(View.VISIBLE, block.visibility)

        goForward()

        assertEquals(listOf(block), empties)
        assertFalse(remembersThePlace())
    }

    @Test
    fun `a block holding the collapse of its refused place tells its host it is empty when its wrapper lets it go after it left the window`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        refuseThePlace()

        (home.requireView() as ViewGroup).removeView(block)
        block.releaseOrRetain()
        idle()

        assertEquals(listOf(block), empties)
    }

    @Test
    fun `a block holding the collapse of its refused place forgets its place when its activity is destroyed in background`() {
        val block = newBlock(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(block, 500, 300) })
        show(block)
        deliverContent()
        refuseThePlace()
        goToBackground(block)
        assertTrue(remembersThePlace())

        controller.pause().stop().destroy()
        idle()

        assertFalse(remembersThePlace())
    }

    @Test
    fun `a block holding the collapse of its refused place collapses as its fragment is hidden in foreground and asks its place again once shown`() {
        val home = openHome()
        deliverContent()
        val block = home.block
        refuseThePlace()
        val appearedBeforeHiding = blocksRegistry.appearedCount

        fragmentManager.beginTransaction().hide(home).commitNow()
        idle()

        assertEquals(View.GONE, block.visibility)
        assertEquals(listOf(block), empties)

        fragmentManager.beginTransaction().show(home).commitNow()
        idle()

        assertEquals(appearedBeforeHiding + 1, blocksRegistry.appearedCount)
    }

    private companion object {
        const val PLACE = "main-screen-top"
    }
}
