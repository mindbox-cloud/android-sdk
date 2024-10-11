package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.dp
import cloud.mindbox.mobile_sdk.inapp.domain.models.Size
import kotlin.math.roundToInt

internal class InAppImageView(context: Context) : ImageView(context) {

    companion object {
        private const val MODAL_WINDOW_ASPECT_RATIO = "H,3:4"
    }

    init {
        id = generateViewId()
    }

    fun prepareViewForSnackBar(size: Size, marginStart: Int, marginEnd: Int) {
        val oneThirdScreenHeight = resources.displayMetrics.heightPixels / 3
        val desiredHeight =
            (((resources.displayMetrics.widthPixels.toDouble() - marginStart.toDouble() - marginEnd.toDouble()) / (size.width.toDouble())) * size.height).roundToInt()
        layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            if (desiredHeight > oneThirdScreenHeight) oneThirdScreenHeight else desiredHeight
        )
        scaleType = ScaleType.CENTER_CROP
    }

    fun prepareViewForModalWindow(currentDialog: InAppConstraintLayout) {
        updateLayoutParams {
            width = 0.dp
            height = 0.dp
        }
        val constraintSet = ConstraintSet()
        constraintSet.clone(currentDialog)
        constraintSet.setDimensionRatio(id, MODAL_WINDOW_ASPECT_RATIO)
        scaleType = ScaleType.CENTER_CROP
        constraintSet.connect(
            id,
            ConstraintSet.TOP,
            currentDialog.id,
            ConstraintSet.TOP,
            0
        )
        constraintSet.connect(
            id,
            ConstraintSet.END,
            currentDialog.id,
            ConstraintSet.END,
            0
        )
        constraintSet.connect(
            id,
            ConstraintSet.START,
            currentDialog.id,
            ConstraintSet.START,
            0
        )
        constraintSet.connect(
            id,
            ConstraintSet.BOTTOM,
            currentDialog.id,
            ConstraintSet.BOTTOM,
            0
        )
        constraintSet.applyTo(currentDialog)
    }
}
