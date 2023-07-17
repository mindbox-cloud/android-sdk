package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateMargins
import cloud.mindbox.mobile_sdk.R

class CrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    fun update() {
        layoutParams.height = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_size).toInt()
        layoutParams.width = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_size).toInt()

        val percentTop = ResourcesCompat.getFloat(context.resources, R.dimen.mindbox_debug_snackbar_close_margin_top)
        val percentRight = ResourcesCompat.getFloat(context.resources, R.dimen.mindbox_debug_snackbar_close_margin_right)

        val image = parent as InAppConstraintLayout
        val marginTop: Int = ((image.width / 100f) * percentTop).toInt()
        val marginRight: Int = ((image.width / 100f) * percentRight).toInt()

        (layoutParams as ViewGroup.MarginLayoutParams).updateMargins(0, marginTop, marginRight, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        update()
    }

    private val paint: Paint = Paint().apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_width)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(width.toFloat(), 0f, 0f, height.toFloat(), paint)
    }
}

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()
