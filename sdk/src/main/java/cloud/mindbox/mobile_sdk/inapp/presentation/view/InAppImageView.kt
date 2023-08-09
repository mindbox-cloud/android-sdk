package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.dp

internal class InAppImageView(context: Context) : ImageView(context), InAppView {


    companion object {
        private const val ASPECT_RATIO = "H,3:4"
    }

    override fun updateView(currentDialog: InAppConstraintLayout) {
        updateLayoutParams {
            width = 0.dp
            height = 0.dp
        }
        scaleType = ScaleType.CENTER_CROP
        val imageViewId = generateViewId()
        id = imageViewId
        val constraintSet = ConstraintSet()
        constraintSet.clone(currentDialog)
        constraintSet.setDimensionRatio(imageViewId, ASPECT_RATIO)
        constraintSet.connect(
            imageViewId,
            ConstraintSet.TOP,
            currentDialog.id,
            ConstraintSet.TOP,
            0
            )
        constraintSet.connect(
            imageViewId,
            ConstraintSet.END,
            currentDialog.id,
            ConstraintSet.END,
           0
        )
        constraintSet.connect(imageViewId,
            ConstraintSet.START,
            currentDialog.id,
            ConstraintSet.START,
            0)
        constraintSet.connect(imageViewId,
            ConstraintSet.BOTTOM,
            currentDialog.id,
            ConstraintSet.BOTTOM,
            0)
        constraintSet.applyTo(currentDialog)
    }

}