package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintSet
import cloud.mindbox.mobile_sdk.dp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType


internal class InAppImageView(context: Context) : ImageView(context), InAppView {

    companion object {
        private const val MODAL_WINDOW_ASPECT_RATIO = "H,3:4"
        private const val SNACKBAR_ASPECT_RATIO = "H,3:1"
    }

    init {
        layoutParams = ViewGroup.LayoutParams(0.dp, 0.dp)
        scaleType = ScaleType.CENTER_CROP
        val imageViewId = generateViewId()
        id = imageViewId
    }

    override fun setInAppParams(inApp: InAppType, currentDialog: InAppConstraintLayout) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(currentDialog)
        when (inApp) {
            is InAppType.ModalWindow -> {
                constraintSet.setDimensionRatio(id, MODAL_WINDOW_ASPECT_RATIO)
            }

            is InAppType.Snackbar -> {
                constraintSet.setDimensionRatio(id, SNACKBAR_ASPECT_RATIO)
            }
        }
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