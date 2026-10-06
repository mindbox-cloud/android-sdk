package cloud.mindbox.mobile_sdk.embedded

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
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

    open fun crossfade(incoming: View, outgoing: View?, onEnd: () -> Unit): Animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            incoming.alpha = 0f
            this.duration = this@EmbeddedBlockRevealAnimation.duration.interval
            addUpdateListener { animator ->
                val shown = animator.animatedValue as Float
                incoming.alpha = shown
                outgoing?.alpha = 1f - shown
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        incoming.alpha = 1f
                        outgoing?.alpha = 0f
                        onEnd()
                    }
                },
            )
            start()
        }

    open fun growHeight(view: View, targetHeight: Int, onEnd: () -> Unit): Animator =
        ValueAnimator.ofInt(0, targetHeight).apply {
            this.duration = this@EmbeddedBlockRevealAnimation.duration.interval
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
}
