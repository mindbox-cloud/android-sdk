package cloud.mindbox.mobile_sdk.embedded

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.Constants

internal open class EmbeddedBlockRevealAnimation(
    val duration: Milliseconds = Constants.Embedded.revealAnimationDuration,
    private val areSystemAnimationsEnabled: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    },
) {

    /** The accessibility "remove animations" setting zeroes the animator scale — respect it. */
    val isEnabled: Boolean
        get() = areSystemAnimationsEnabled()

    private val revealInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    open fun fadeIn(view: View, onEnd: () -> Unit): Animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            val rise = view.resources.displayMetrics.density * CONTENT_RISE_DP
            view.alpha = 0f
            view.translationY = rise
            this.duration = this@EmbeddedBlockRevealAnimation.duration.interval
            interpolator = revealInterpolator
            addUpdateListener { animator ->
                val shown = animator.animatedValue as Float
                view.alpha = shown
                view.translationY = rise * (1f - shown)
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.alpha = 1f
                        view.translationY = 0f
                        onEnd()
                    }
                },
            )
            start()
        }

    open fun growHeight(view: View, targetHeight: Int, onEnd: () -> Unit): Animator =
        ValueAnimator.ofInt(0, targetHeight).apply {
            this.duration = this@EmbeddedBlockRevealAnimation.duration.interval
            interpolator = revealInterpolator
            addUpdateListener { animator ->
                view.layoutParams?.let { params ->
                    params.height = animator.animatedValue as Int
                    view.requestLayout()
                }
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.layoutParams?.let { params ->
                            params.height = targetHeight
                            view.requestLayout()
                        }
                        onEnd()
                    }
                },
            )
            start()
        }

    private companion object {
        private const val CONTENT_RISE_DP = 16f
    }
}
