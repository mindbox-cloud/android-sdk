package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.dp
import cloud.mindbox.mobile_sdk.px


internal class InAppImageView(context: Context) : ImageView(context), InAppView {


    companion object {
        private const val MODAL_WINDOW_ASPECT_RATIO = "H,3:4"
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
        constraintSet.setDimensionRatio(imageViewId, MODAL_WINDOW_ASPECT_RATIO)
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
        constraintSet.connect(
            imageViewId,
            ConstraintSet.START,
            currentDialog.id,
            ConstraintSet.START,
            0
        )
        constraintSet.connect(
            imageViewId,
            ConstraintSet.BOTTOM,
            currentDialog.id,
            ConstraintSet.BOTTOM,
            0
        )
        constraintSet.applyTo(currentDialog)
    }

    override fun updateView(currentDialog: InAppFrameLayout) {
        val imageViewId = generateViewId()
        scaleType = ScaleType.CENTER_INSIDE
        id = imageViewId
        adjustViewBounds = true
        updateLayoutParams<MarginLayoutParams> {
            setMargins(0, 0, 0, 0)
            width = MATCH_PARENT
            height = WRAP_CONTENT
            maxHeight = resources.displayMetrics.heightPixels / 3
        }

    }

}