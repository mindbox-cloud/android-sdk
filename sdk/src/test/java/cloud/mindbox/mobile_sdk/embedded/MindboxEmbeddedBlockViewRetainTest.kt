package cloud.mindbox.mobile_sdk.embedded

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
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.After
import org.junit.Assert.assertEquals
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

        override fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable {
            handles.add(handle)
            return Closeable { handles.remove(handle) }
        }

        override fun onBlockAppeared(placeSystemName: String) = Unit

        override fun onBlockContentDropped(placeSystemName: String) {
            droppedCount++
        }

        override fun startListening() = Unit
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

    private fun newBlock(context: Context): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            context,
            null,
            PLACE,
            contentController = EmbeddedBlockContentController(
                placeSystemName = PLACE,
                providerFactory = { _, _ -> FakeProvider(context).also { providers.add(it) } },
                blocksRegistry = { blocksRegistry },
            ),
            contentStore = { store },
        ).apply {
            setListener(
                object : MindboxEmbeddedBlockListener {
                    override fun onLoad(view: MindboxEmbeddedBlockView) {
                        loads.add(view)
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

    private companion object {
        const val PLACE = "main-screen-top"
    }
}
