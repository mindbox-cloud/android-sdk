package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.graphics.Color
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockShimmerDesign.END_X
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockShimmerDesign.START_X
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockShimmerDesign.cycle
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockShimmerDesign.pauseAtStart
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockShimmerDesign.sweep
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockShimmerViewTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun shimmer(now: () -> Long, epoch: Long = 0L) =
        EmbeddedBlockShimmerView(activity, clock = { Milliseconds(now()) }, epoch = Milliseconds(epoch))

    private fun layerLeftAt(elapsedMs: Long): Float = EmbeddedBlockShimmerDesign.layerLeft(Milliseconds(elapsedMs))

    private fun assertClose(expected: Float, actual: Float) = assertEquals(expected, actual, TOLERANCE)

    // MARK: - Color

    @Test
    fun `light theme is one near-black tint whose opacity dips in the highlight`() {
        val rest = ContextCompat.getColor(activity, R.color.mindbox_embedded_block_shimmer_rest)
        val highlight = ContextCompat.getColor(activity, R.color.mindbox_embedded_block_shimmer_highlight)

        assertEquals(0x282A2F, rest and RGB_MASK)
        assertEquals(0x282A2F, highlight and RGB_MASK)
        assertEquals(0x14, Color.alpha(rest))
        assertEquals(0x0A, Color.alpha(highlight))
        assertArrayEquals(
            intArrayOf(rest, rest, highlight, rest, rest),
            EmbeddedBlockShimmerDesign.colors(rest, highlight),
        )
    }

    @Test
    @Config(qualifiers = "night")
    fun `dark theme is one white tint whose opacity peaks in the highlight`() {
        val rest = ContextCompat.getColor(activity, R.color.mindbox_embedded_block_shimmer_rest)
        val highlight = ContextCompat.getColor(activity, R.color.mindbox_embedded_block_shimmer_highlight)

        assertEquals(0xFFFFFF, rest and RGB_MASK)
        assertEquals(0xFFFFFF, highlight and RGB_MASK)
        assertEquals(0x14, Color.alpha(rest))
        assertEquals(0x29, Color.alpha(highlight))
    }

    @Test
    fun `the view paints nothing under the tint - it is a mask over the host background`() {
        val view = shimmer(now = { 0L })

        assertNull(view.background)
        assertFalse(view.isClickable)
    }

    @Test
    fun `the default placeholder is the shimmer`() {
        assertTrue(EmbeddedBlockDefaultViews.placeholder(activity) is EmbeddedBlockShimmerView)
    }

    // MARK: - Geometry

    @Test
    fun `at both rest positions the ramp lies outside the block, which shows a flat tint`() {
        // The ramp is the three middle stops; the outer two are flat and may be anywhere.
        val atStart = EmbeddedBlockShimmerDesign.blockStops(START_X)
        val atEnd = EmbeddedBlockShimmerDesign.blockStops(END_X)

        assertTrue("before the sweep the ramp waits off the leading edge", atStart.slice(1..3).all { it < 0f })
        assertTrue("after the sweep the ramp has left past the trailing edge", atEnd.slice(1..3).all { it > 1f })
        // And the flat outer stops still cover the block from both sides at either rest.
        assertTrue(atStart.first() < 0f && atStart.last() > 1f)
        assertTrue(atEnd.first() < 0f && atEnd.last() > 1f)
    }

    // MARK: - Cycle

    @Test
    fun `the layer rests at the start for the first pause`() {
        assertClose(START_X, layerLeftAt(0L))
        assertClose(START_X, layerLeftAt(pauseAtStart.interval - 1))
        assertClose(START_X, layerLeftAt(pauseAtStart.interval))
    }

    @Test
    fun `the layer rests at the end for the second pause and jumps back at the end of the cycle`() {
        assertClose(END_X, layerLeftAt(pauseAtStart.interval + sweep.interval))
        assertClose(END_X, layerLeftAt(cycle.interval - 1))
        assertClose(START_X, EmbeddedBlockShimmerDesign.layerLeft(EmbeddedBlockShimmerDesign.elapsedInCycle(cycle, Milliseconds(0L))))
    }

    @Test
    fun `the sweep runs from the start to the end and eases in`() {
        val quarter = layerLeftAt(pauseAtStart.interval + sweep.interval / 4)
        val half = layerLeftAt(pauseAtStart.interval + sweep.interval / 2)
        val linearHalf = START_X + (END_X - START_X) / 2

        assertTrue("the highlight moves towards the trailing edge", START_X < quarter && quarter < half && half < END_X)
        assertTrue("ease-in: halfway through the time it is short of halfway through the distance", half < linearHalf)
    }

    @Test
    fun `one cycle is 0_6 s of rest, a 1 s sweep and 0_6 s of rest`() {
        assertEquals(2200L, cycle.interval)
        assertEquals(600L, pauseAtStart.interval)
        assertEquals(1000L, sweep.interval)
    }

    @Test
    fun `sweeps only while attached to a window`() {
        val view = shimmer(now = { 0L })
        val host = FrameLayout(activity)
        activity.setContentView(host)

        assertFalse(view.isSweeping)
        host.addView(view)
        assertTrue(view.isSweeping)
        host.removeView(view)
        assertFalse(view.isSweeping)
    }

    // MARK: - Shared beat

    @Test
    fun `every shimmer is at the same point of the cycle, whenever it appeared`() {
        var now = 0L
        val first = shimmer(now = { now }, epoch = 0L)
        now = 900L // the second one comes later, mid-sweep of the first
        val second = shimmer(now = { now }, epoch = 0L)

        now = 1_100L // still mid-sweep: the sweep runs from 600 to 1600 ms
        assertClose(first.currentLayerLeft(), second.currentLayerLeft())
        assertTrue("both are mid-sweep, not at a rest", first.currentLayerLeft() > START_X && first.currentLayerLeft() < END_X)
    }

    @Test
    fun `a clock reading before the epoch still lands inside the cycle`() {
        val elapsed = EmbeddedBlockShimmerDesign.elapsedInCycle(now = Milliseconds(100L), epoch = Milliseconds(400L))

        assertTrue(elapsed.interval in 0L until cycle.interval)
        assertEquals(cycle.interval - 300L, elapsed.interval)
    }

    @Test
    fun `the shared epoch is one per process and lies in the past`() {
        val epoch = EmbeddedBlockShimmerView.beatEpoch

        assertEquals(epoch, EmbeddedBlockShimmerView.beatEpoch)
        assertTrue(epoch.interval <= android.os.SystemClock.uptimeMillis())
    }

    private companion object {
        const val TOLERANCE = 0.0001f
        const val RGB_MASK = 0xFFFFFF
    }
}
