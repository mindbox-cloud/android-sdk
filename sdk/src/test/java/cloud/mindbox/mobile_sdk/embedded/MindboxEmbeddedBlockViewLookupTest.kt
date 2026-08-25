package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The public-constructor path: the block resolves its content through the real config lookup
 * (no injected fake), exactly as a host app creates it. Nothing is initialized in this test
 * process — which is precisely the case a host hits when it inflates a screen before the SDK
 * finished starting up.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewLookupTest {

    private class RecordingListener : MindboxEmbeddedBlockListener {
        val events = mutableListOf<String>()

        override fun onLoad(view: MindboxEmbeddedBlockView) {
            events.add("load")
        }

        override fun onFail(view: MindboxEmbeddedBlockView) {
            events.add("fail")
        }
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun attach(view: MindboxEmbeddedBlockView) {
        activity.setContentView(
            LinearLayout(activity).apply { addView(view, 500, 300) },
        )
        shadowOf(Looper.getMainLooper()).idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a block created before the config arrived holds the placeholder within the timeout`() {
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top")
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29L))

        // No config yet is not an empty place: the block holds its placeholder and stays silent
        // for as long as the config timeout allows.
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(listener.events.isEmpty())
        assertEquals("main-screen-top", view.placeSystemName)
    }

    @Test
    fun `no config within the timeout collapses the block to empty`() {
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top")
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31L))

        // 30 seconds without a config → Empty (a collapse, not a failure state); a late config
        // would still expand the block because the registration survives.
        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a timeout given in code is the one the block waits out`() {
        // The wrapper path: no XML to carry `mindboxTimeoutMs`, so the budget comes as an argument.
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top", timeoutMs = 5_000L)
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4L))

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(listener.events.isEmpty())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2L))

        // Six seconds in — long past the five it was given, and long before the default thirty.
        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a block given no timeout in code still reads the one from xml`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.mindboxPlaceSystemName, "main-screen-top")
            .addAttribute(R.attr.mindboxTimeoutMs, "5000")
            .build()

        val view = MindboxEmbeddedBlockView(activity, attrs)
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6L))

        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a non-positive timeout is not a budget, and the default stands`() {
        // Honoured literally it would collapse every block before the config had a chance.
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top", timeoutMs = 0L)
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29L))

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(listener.events.isEmpty())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2L))

        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a block without a place name hides itself`() {
        // XML without the attribute (or a programmatic view with no name): nothing to match
        // against the config — the block hides itself, never a crash.
        val view = MindboxEmbeddedBlockView(activity)
        val listener = RecordingListener()
        view.setListener(listener)

        attach(view)

        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("fail"), listener.events)
        assertNull(view.placeSystemName)
    }

    @Test
    fun `the place name is read from the xml attribute`() {
        // The layout path: a host marks the place in XML and never touches the SDK in code.
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.mindboxPlaceSystemName, "main-screen-top")
            .build()

        val view = MindboxEmbeddedBlockView(activity, attrs)

        assertEquals("main-screen-top", view.placeSystemName)
    }

    @Test
    fun `a layout without the attribute leaves the block nameless`() {
        val view = MindboxEmbeddedBlockView(activity, Robolectric.buildAttributeSet().build())

        assertNull(view.placeSystemName)
    }

    @Test
    fun `a blank place name is the same as none`() {
        val view = MindboxEmbeddedBlockView(activity, "   ")

        attach(view)

        assertEquals(View.GONE, view.visibility)
        assertNull(view.placeSystemName)
    }

    @Test
    fun `the block behaves with no listener at all`() {
        // The show/hide behavior belongs to the block, not to the host's callbacks.
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top")

        attach(view)

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `releasing a block that never resolved anything is safe`() {
        val view = MindboxEmbeddedBlockView(activity, "main-screen-top")
        attach(view)

        (view.parent as LinearLayout).removeView(view)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
