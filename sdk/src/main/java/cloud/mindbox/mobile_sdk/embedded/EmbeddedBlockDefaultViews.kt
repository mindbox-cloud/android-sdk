package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.px

internal object EmbeddedBlockDefaultViews {

    fun placeholder(context: Context): View = PulsingPlaceholderView(context)

    private fun roundedRect(color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = CORNER_RADIUS_DP.px.toFloat()
            setColor(color)
        }

    private class PulsingPlaceholderView(context: Context) : FrameLayout(context) {

        // Resolved from resources, so values-night handles the dark screen: a light rectangle
        // there reads as a lit block, not as content on its way.
        private val fill = View(context).apply {
            background = roundedRect(
                ContextCompat.getColor(context, R.color.mindbox_embedded_block_placeholder),
            )
        }

        init {
            addView(
                fill,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    INSET_DP.px.let { setMargins(it, it, it, it) }
                },
            )
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            fill.startAnimation(
                AlphaAnimation(1f, PULSE_MIN_ALPHA).apply {
                    duration = PULSE_DURATION_MS
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                },
            )
        }

        override fun onDetachedFromWindow() {
            fill.clearAnimation()
            super.onDetachedFromWindow()
        }
    }

    private const val CORNER_RADIUS_DP = 8.0
    private const val INSET_DP = 6

    private const val PULSE_MIN_ALPHA = 0.45f
    private const val PULSE_DURATION_MS = 700L
}
