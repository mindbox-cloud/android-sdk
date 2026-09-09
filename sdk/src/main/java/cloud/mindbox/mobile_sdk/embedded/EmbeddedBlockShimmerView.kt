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
import cloud.mindbox.mobile_sdk.models.Milliseconds

internal object EmbeddedBlockShimmerDesign {

    val stops: FloatArray = floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f)

    const val LAYER_WIDTH = 2.96f

    const val START_X = -1.88f

    const val END_X = -0.083f

    val pauseAtStart: Milliseconds = Milliseconds(600L)

    val sweep: Milliseconds = Milliseconds(1000L)

    val pauseAtEnd: Milliseconds = Milliseconds(600L)

    val cycle: Milliseconds = Milliseconds(pauseAtStart.interval + sweep.interval + pauseAtEnd.interval)

    val easeIn: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 1f, 1f)

    fun colors(rest: Int, highlight: Int): IntArray = intArrayOf(rest, rest, highlight, rest, rest)

    fun elapsedInCycle(now: Milliseconds, epoch: Milliseconds): Milliseconds =
        Milliseconds((now.interval - epoch.interval).mod(cycle.interval))

    fun layerLeft(elapsedInCycle: Milliseconds): Float {
        val sweepElapsed = elapsedInCycle.interval - pauseAtStart.interval
        return when {
            sweepElapsed <= 0 -> START_X
            sweepElapsed >= sweep.interval -> END_X
            else -> START_X + (END_X - START_X) * easeIn.getInterpolation(sweepElapsed.toFloat() / sweep.interval)
        }
    }

    fun blockStops(layerLeft: Float): FloatArray =
        FloatArray(stops.size) { index -> layerLeft + LAYER_WIDTH * stops[index] }
}

internal class EmbeddedBlockShimmerView(
    context: Context,
    private val clock: () -> Milliseconds = { Milliseconds(SystemClock.uptimeMillis()) },
    private val epoch: Milliseconds = beatEpoch,
) : View(context) {

    private val paint = Paint()
    private val shaderMatrix = Matrix()
    private var shader: LinearGradient? = null
    private var colors: IntArray = loadColors()

    var isSweeping: Boolean = false
        private set

    init {
        background = null
        isClickable = false
        isFocusable = false
    }

    fun currentLayerLeft(): Float =
        EmbeddedBlockShimmerDesign.layerLeft(EmbeddedBlockShimmerDesign.elapsedInCycle(clock(), epoch))

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
        val beatEpoch: Milliseconds = Milliseconds(SystemClock.uptimeMillis())
    }
}
