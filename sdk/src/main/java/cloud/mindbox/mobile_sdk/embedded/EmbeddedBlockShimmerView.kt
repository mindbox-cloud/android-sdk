package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.animation.Interpolator
import androidx.core.content.ContextCompat
import androidx.core.view.animation.PathInterpolatorCompat
import cloud.mindbox.mobile_sdk.R

/**
 * The design's numbers for the embedded block shimmer. Positions are fractions of the block's
 * width (`W`), so the same figures serve an 80 dp avatar and a full-width banner.
 *
 * The shimmer is a mask, not a tile: one tint whose opacity alone changes. The tint sits at 8%
 * everywhere but the highlight, where it dips to 4% in the light theme (a lighter spot) and peaks
 * to 16% in the dark one (a brighter spot). A gradient layer almost three blocks wide carries the
 * highlight from off the leading edge to off the trailing one, so at both rests the block shows a
 * flat tint and the jump back to the start is invisible.
 */
internal object EmbeddedBlockShimmerDesign {

    /** Where the tint changes opacity, as fractions of the gradient layer's width. */
    val stops: FloatArray = floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f)

    /** The gradient layer's width in `W`. */
    const val LAYER_WIDTH = 2.96f

    /** The gradient layer's left edge at rest before the sweep, in `W`. */
    const val START_X = -1.88f

    /** The gradient layer's left edge at rest after the sweep, in `W`. */
    const val END_X = -0.083f

    /** Flat at the start before the highlight sets off. */
    const val PAUSE_AT_START_MS = 600L

    /** The sweep itself, ease-in: the highlight leaves slowly and exits fast. */
    const val SWEEP_MS = 1000L

    /** Flat at the end; then the layer jumps back to the start. */
    const val PAUSE_AT_END_MS = 600L

    const val CYCLE_MS: Long = PAUSE_AT_START_MS + SWEEP_MS + PAUSE_AT_END_MS

    /** Figma's "ease-in". */
    val easeIn: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 1f, 1f)

    /** The rest tint at every stop but the middle one, which carries the highlight. */
    fun colors(rest: Int, highlight: Int): IntArray = intArrayOf(rest, rest, highlight, rest, rest)

    /** How far into the cycle a clock reading is, given the instant every shimmer counts from. */
    fun elapsedInCycle(nowMs: Long, epochMs: Long): Long = (nowMs - epochMs).mod(CYCLE_MS)

    /** The gradient layer's left edge, in `W`, at a point of the cycle. */
    fun layerLeft(elapsedInCycleMs: Long): Float {
        val sweepElapsed = elapsedInCycleMs - PAUSE_AT_START_MS
        return when {
            sweepElapsed <= 0 -> START_X
            sweepElapsed >= SWEEP_MS -> END_X
            else -> START_X + (END_X - START_X) * easeIn.getInterpolation(sweepElapsed.toFloat() / SWEEP_MS)
        }
    }

    /** The stops in the block's own coordinates for a layer whose left edge is at [layerLeft]. */
    fun blockStops(layerLeft: Float): FloatArray =
        FloatArray(stops.size) { index -> layerLeft + LAYER_WIDTH * stops[index] }
}

/**
 * The default embedded block placeholder — the Mindbox UI Library "Shimmer".
 *
 * Fills the block frame entirely: the SDK knows nothing about the layout of the content to come,
 * so the placeholder does not depict it and simply marks the reserved spot as "loading". It paints
 * nothing of its own under the tint, so it reads the same on a white screen, a brand color and a
 * dark theme. A host that needs a skeleton of its own layout sets the block's placeholder view.
 *
 * Several shimmers on one screen move to one beat: every instance derives its frame from the same
 * process-wide clock and the same epoch, so a block that appears later joins the sweep already in
 * progress instead of starting its own.
 *
 * The design's layer blur (about 5.6% of the block's width, a tenth of the ramp) is deliberately
 * left out: it would move an opacity that is 8% to begin with by a few hundredths, and the result
 * would be indistinguishable from the plain linear ramp.
 */
internal class EmbeddedBlockShimmerView(
    context: Context,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val epochMs: Long = beatEpochMs,
) : View(context) {

    private val paint = Paint()
    private val shaderMatrix = Matrix()
    private var shader: LinearGradient? = null
    private var colors: IntArray = loadColors()

    /** Whether frames are being requested — true between attach and detach. */
    var isSweeping: Boolean = false
        private set

    init {
        // A mask over the host's background: no background of its own underneath the tint.
        background = null
        isClickable = false
        isFocusable = false
    }

    /** The gradient layer's left edge right now, in fractions of the block's width. */
    fun currentLayerLeft(): Float =
        EmbeddedBlockShimmerDesign.layerLeft(EmbeddedBlockShimmerDesign.elapsedInCycle(clock(), epochMs))

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSweeping = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        isSweeping = false
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        colors = loadColors()
        rebuildShader()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shader = shader ?: return
        shaderMatrix.setTranslate(currentLayerLeft() * width, 0f)
        shader.setLocalMatrix(shaderMatrix)
        // Set again on purpose: a hardware canvas does not pick a matrix change up otherwise.
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (isSweeping) postInvalidateOnAnimation()
    }

    private fun loadColors(): IntArray =
        EmbeddedBlockShimmerDesign.colors(
            rest = ContextCompat.getColor(context, R.color.mindbox_embedded_block_shimmer_rest),
            highlight = ContextCompat.getColor(context, R.color.mindbox_embedded_block_shimmer_highlight),
        )

    /** One horizontal gradient the width of the design's layer; CLAMP keeps the flat 8% beyond it. */
    private fun rebuildShader() {
        shader = if (width > 0) {
            LinearGradient(
                0f,
                0f,
                width * EmbeddedBlockShimmerDesign.LAYER_WIDTH,
                0f,
                colors,
                EmbeddedBlockShimmerDesign.stops,
                Shader.TileMode.CLAMP,
            )
        } else {
            null
        }
        paint.shader = shader
    }

    companion object {
        /**
         * The instant every shimmer counts its cycle from. One per process: two blocks side by
         * side — or a block that shows up a screen later — are at the same point of the same cycle.
         */
        val beatEpochMs: Long = SystemClock.uptimeMillis()
    }
}
