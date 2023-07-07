package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.TranslateAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.setSingleClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlin.math.abs


internal class SnackBarInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.SnackBar>,
    private val inAppCallback: InAppCallback,
) : InAppViewHolder<InAppType.SnackBar> {

    private lateinit var currentDialog: InAppConstraintLayout

    private var isInAppMessageActive = false
    private var typingView: View? = null

    override val isActive: Boolean
        get() = isInAppMessageActive

    private fun initView(currentRoot: ViewGroup) {
        val context = currentRoot.context
        val inflater = LayoutInflater.from(context)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm?.isAcceptingText == true) {
            typingView = currentRoot.findFocus()
            imm.hideSoftInputFromWindow(
                currentRoot.windowToken,
                0
            )
        }

        currentDialog = inflater.inflate(
            R.layout.snackbar_inapp_layout,
            currentRoot, false
        ) as InAppConstraintLayout
        currentDialog.setMargins(0,0,0,0)
    }

    fun View.setMargins(l: Int, t: Int, r: Int, b: Int) {
        if (layoutParams is MarginLayoutParams) {
            val p = layoutParams as MarginLayoutParams
            p.setMargins(l, t, r, b)
            requestLayout()
        }
    }

    private fun restoreKeyboard() {
        typingView?.let { view ->
            view.requestFocus()
            val imm =
                (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)
            imm?.showSoftInput(
                view,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun bind(currentRoot: ViewGroup) {


        currentDialog.setSingleClickListener {
            wrapper.onInAppClick.onClick()
            inAppCallback.onInAppClick(
                wrapper.inAppType.inAppId,
                wrapper.inAppType.redirectUrl,
                wrapper.inAppType.intentData
            )
            if (wrapper.inAppType.redirectUrl.isNotBlank() || wrapper.inAppType.intentData.isNotBlank()) {
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                mindboxLogI("In-app dismissed by click")
                isInAppMessageActive = false
                hide()
            }
        }
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            isInAppMessageActive = false
            hide()
        }

        currentDialog.mindboxLogI("In-app shown")
        wrapper.onInAppShown.onShown()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun show(currentRoot: ViewGroup) {
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        if (wrapper.inAppType.imageUrl.isBlank()) {
            mindboxLogI("In-app image url is blank")
            return
        }
        initView(currentRoot)
        isInAppMessageActive = true

        currentRoot.addView(currentDialog)
        currentDialog.requestFocus()

        with(currentRoot.findViewById<ImageView>(R.id.iv_content)) {
            mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")

//            val gestureDetector = GestureDetector(context, GestureListener(
//                { hide() }, { hide() }
//            ))

            var rightDX = 0f
            var rightDY = 0f
            setOnTouchListener { view, event ->
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {

                        //rightDX = view!!.y - event.rawX
                         rightDY = view!!.getY() - event.rawY;

                    }
                    MotionEvent.ACTION_MOVE -> {
                        val displacement = maxOf(0f,event.rawY + rightDY)

                        view!!.animate()
                            //.x(displacement)
                            .y(displacement)
                            .setDuration(0)
                            .start()

                    }
                    MotionEvent.ACTION_UP -> {
                        view!!.animate()
                            .y(0f)
                            .setDuration(100)
                            .start()
                    }
                    else -> { // Note the block
                        return@setOnTouchListener false
                    }
                }
                true
            }

            Glide
                .with(currentRoot.context.applicationContext)
                .load(wrapper.inAppType.imageUrl)
                .onlyRetrieveFromCache(true)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        this@SnackBarInAppViewHolder.mindboxLogE(
                            message = "Failed to load inapp image",
                            exception = e ?: RuntimeException("Failed to load inapp image")
                        )
                        hide()
                        isInAppMessageActive = false
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        bind(currentRoot)
                        slideUp(this@with) {
                            currentRoot.findViewById<ImageView>(R.id.iv_close)?.apply {
                                isVisible = true
                                setOnClickListener {
                                    mindboxLogI("In-app dismissed by close click")
                                    inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                                    hide()
                                    isInAppMessageActive = false
                                }
                            }
                        }
                        return false
                    }
                })
                .centerCrop()
                .into(this)
        }
    }

    override fun hide() {
        mindboxLogI("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")

        currentDialog.findViewById<View>(R.id.iv_close).isVisible = false

        slideDown(currentDialog.findViewById(R.id.iv_content)) {
            (currentDialog.parent as? ViewGroup?)?.apply {
                removeView(currentDialog)
            }
        }
    }

}

private fun slideUp(view: View, onCompleted: () -> Unit) {
    view.visibility = View.VISIBLE
    val animate = TranslateAnimation(
        0f,  // fromXDelta
        0f,  // toXDelta
        view.height.toFloat(),  // fromYDelta
        0f
    ) // toYDelta
    animate.duration = duration
    animate.fillAfter = true
    animate.interpolator = LinearOutSlowInInterpolator()
    animate.setAnimationListener(object: AnimationListener {
        override fun onAnimationStart(animation: Animation?) {
        }

        override fun onAnimationEnd(animation: Animation?) {
            onCompleted()
        }

        override fun onAnimationRepeat(animation: Animation?) {
        }

    })
    view.startAnimation(animate)
}

val duration = 1000L
// slide the view from its current position to below itself
private fun slideDown(view: View, onCompleted: () -> Unit) {
    val animate = TranslateAnimation(
        0f,  // fromXDelta
        0f,  // toXDelta
        0f,  // fromYDelta
        view.height.toFloat()
    ) // toYDelta
    animate.duration = duration
    animate.fillAfter = true
    animate.interpolator = FastOutLinearInInterpolator()
    animate.setAnimationListener(object: AnimationListener {
        override fun onAnimationStart(animation: Animation?) {
        }

        override fun onAnimationEnd(animation: Animation?) {
            onCompleted()
        }

        override fun onAnimationRepeat(animation: Animation?) {
        }

    })
    view.startAnimation(animate)
}

class GestureListener(
    val onSwipeUp: () -> Unit,
    val onSwipeDown: () -> Unit,
) : SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val distanceX = e2.x - e1.x
        val distanceY = e2.y - e1.y
        if (abs(distanceY) > abs(distanceX)
            && abs(distanceY) > SWIPE_DISTANCE_THRESHOLD
            && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
        ) {
            if (distanceX > 0) onSwipeUp() else onSwipeDown()
            return true
        }
        return false
    }

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}