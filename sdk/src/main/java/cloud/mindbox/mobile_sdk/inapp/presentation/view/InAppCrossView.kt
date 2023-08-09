package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType.ModalWindow.Element.CloseButton.Position.Kind.PROPORTION
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType.ModalWindow.Element.CloseButton.Size.Kind.DP
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.px
import kotlin.math.roundToInt

internal class InAppCrossView : View, InAppView {

    private val closeButtonElement: InAppType.ModalWindow.Element.CloseButton

    private val paint: Paint


    constructor(
        context: Context,
        closeButtonElement: InAppType.ModalWindow.Element.CloseButton
    ) : super(context) {
        this.closeButtonElement = closeButtonElement
        paint = Paint().apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = closeButtonElement.lineWidth.toFloat()
        }
    }

    constructor(context: Context) : super(context) {
        closeButtonElement = InAppType.ModalWindow.Element.CloseButton(
            color = "#000000",
            lineWidth = 1.0,
            size = InAppType.ModalWindow.Element.CloseButton.Size(
                width = 24.0,
                height = 24.0,
                kind = DP
            ),
            position = InAppType.ModalWindow.Element.CloseButton.Position(
                top = 0.03,
                right = 0.03,
                left = 0.03,
                bottom = 0.03,
                kind = PROPORTION
            )
        )
        paint = Paint().apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = closeButtonElement.lineWidth.toFloat()
        }
    }
    override fun updateView(currentDialog: InAppConstraintLayout) {
        val crossWidth = when (closeButtonElement.size.kind) {
            DP -> {
                closeButtonElement.size.width.toInt().px
            }
        }
        val crossHeight = when (closeButtonElement.size.kind) {
            DP -> {
                closeButtonElement.size.height.toInt().px
            }
        }
        updateLayoutParams {
            width = crossWidth
            height = crossHeight
            paint.color = Color.parseColor(closeButtonElement.color)
        }
        val crossViewId = generateViewId()
        id = crossViewId
        val constraintSet = ConstraintSet()
        val marginTop =
            ((currentDialog.height - crossHeight) * closeButtonElement.position.top).roundToInt()
        val marginEnd =
            ((currentDialog.width - crossWidth) * closeButtonElement.position.right).roundToInt()

        constraintSet.clone(currentDialog)
        constraintSet.connect(
            crossViewId,
            ConstraintSet.TOP,
            currentDialog.id,
            ConstraintSet.TOP,
            marginTop
        )
        constraintSet.connect(
            crossViewId,
            ConstraintSet.END,
            currentDialog.id,
            ConstraintSet.END,
            marginEnd
        )
        constraintSet.applyTo(currentDialog)
        mindboxLogD("InApp cross is shown with params:color = ${closeButtonElement.color}, lineWidth = ${closeButtonElement.lineWidth}, width = ${closeButtonElement.size.width}, height = ${closeButtonElement.size.height} with kind ${closeButtonElement.size.kind.name}. Margins: top = ${closeButtonElement.position.top}, bottom = ${closeButtonElement.position.bottom}, left = ${closeButtonElement.position.left}, right = ${closeButtonElement.position.right} and kind ${closeButtonElement.position.kind.name}")
        if (paint.strokeWidth == 0f || crossWidth == 0 || crossHeight == 0) isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(width.toFloat(), 0f, 0f, height.toFloat(), paint)
    }
}