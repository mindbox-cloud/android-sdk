package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.updateMargins
import cloud.mindbox.mobile_sdk.R

class CrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    lateinit var ivClose: View

    init {
        layoutParams.height = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_size).toInt()
        layoutParams.width = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_size).toInt()

        val size = context.resources.getDimension(R.dimen.mindbox_debug_snackbar_close_size).toInt()
        val image = parent as ImageView
        val marginTop: Int = ((image.width / 100) * 0.025f).toInt()
        val marginRight: Int = ((image.width / 100) * 0.025f).toInt()

        (layoutParams as ViewGroup.MarginLayoutParams).updateMargins(0, marginTop, marginRight, 0)
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
