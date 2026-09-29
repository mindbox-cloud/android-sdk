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
    fun `the fade carries the view from invisible to fully shown and reports the end`() {
        val view = View(activity)
        var ended = false

        animation.fadeIn(view) { ended = true }
        assertEquals(0f, view.alpha)
        assertTrue(view.translationY > 0f)

        runOut()

        assertEquals(1f, view.alpha)
        assertEquals(0f, view.translationY)
        assertTrue(ended)
    }

    @Test
    fun `a cancelled fade settles the view at fully shown`() {
        val view = View(activity)
        var ended = false

        val animator = animation.fadeIn(view) { ended = true }
        animator.cancel()

        assertEquals(1f, view.alpha)
        assertEquals(0f, view.translationY)
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
