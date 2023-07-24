package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.inapp.presentation.CrossParams
import cloud.mindbox.mobile_sdk.px


internal class CrossView constructor(context: Context) : View(context) {

    private val paint: Paint = Paint().apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = CrossParams.lineWidth
    }

    fun updateView(currentDialog: InAppConstraintLayout) {
        updateLayoutParams {
            width = CrossParams.width.px
            height = CrossParams.height.px
            paint.color = CrossParams.color
        }
        val crossViewId = generateViewId()
        id = crossViewId
        val constraintSet = ConstraintSet()
        constraintSet.clone(currentDialog)
        constraintSet.connect(
            crossViewId,
            ConstraintSet.TOP,
            currentDialog.id,
            ConstraintSet.TOP, CrossParams.marginTop.px
        )
        constraintSet.connect(
            crossViewId,
            ConstraintSet.END,
            currentDialog.id,
            ConstraintSet.END,
            CrossParams.marginEnd.px
        )
        constraintSet.applyTo(currentDialog)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(width.toFloat(), 0f, 0f, height.toFloat(), paint)
    }
}