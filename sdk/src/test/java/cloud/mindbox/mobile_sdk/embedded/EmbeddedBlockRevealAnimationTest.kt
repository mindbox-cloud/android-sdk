package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.models.Milliseconds
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The two animator runs behind the reveal, driven to their end on the test looper. */
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockRevealAnimationTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val animation = EmbeddedBlockRevealAnimation(duration = Milliseconds(100L))

    private fun runOut() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300L))
    }

    @Test
    fun `the crossfade shows the incoming view and hides the outgoing one in step`() {
        val incoming = View(activity)
        val outgoing = View(activity)
        var ended = false

        animation.crossfade(incoming, outgoing) { ended = true }
        assertEquals(0f, incoming.alpha)
        assertEquals(1f, outgoing.alpha)

        runOut()

        assertEquals(1f, incoming.alpha)
        assertEquals(0f, outgoing.alpha)
        assertTrue(ended)
    }

    @Test
    fun `a crossfade with nothing to hide still shows the incoming view`() {
        val incoming = View(activity)
        var ended = false

        animation.crossfade(incoming, outgoing = null) { ended = true }
        runOut()

        assertEquals(1f, incoming.alpha)
        assertTrue(ended)
    }

    @Test
    fun `a cancelled crossfade settles both views at their final look`() {
        val incoming = View(activity)
        val outgoing = View(activity)
        var ended = false

        val animator = animation.crossfade(incoming, outgoing) { ended = true }
        animator.cancel()

        assertEquals(1f, incoming.alpha)
        assertEquals(0f, outgoing.alpha)
        assertTrue(ended)
    }

    @Test
    fun `the growth lands on the target height and reports the end`() {
        val view = View(activity)
        LinearLayout(activity).addView(view, 500, 0)
        var ended = false

        animation.growHeight(view, targetHeight = 300) { ended = true }
        runOut()

        assertEquals(300, view.layoutParams.height)
        assertTrue(ended)
    }

    @Test
    fun `a cancelled growth restores the height the host gave`() {
        val view = View(activity)
        LinearLayout(activity).addView(view, 500, 0)

        val animator = animation.growHeight(view, targetHeight = 300) {}
        animator.cancel()

        assertEquals(300, view.layoutParams.height)
    }

    @Test
    fun `the system animation switch is the enabled seam`() {
        assertTrue(EmbeddedBlockRevealAnimation(areSystemAnimationsEnabled = { true }).isEnabled)
        assertFalse(EmbeddedBlockRevealAnimation(areSystemAnimationsEnabled = { false }).isEnabled)
    }
}
