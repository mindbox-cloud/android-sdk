package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.dp
import cloud.mindbox.mobile_sdk.inapp.domain.models.Size


internal class InAppImageView(context: Context) : ImageView(context) {

    companion object {
        private const val MODAL_WINDOW_ASPECT_RATIO = "H,3:4"
    }

    init {
        id = generateViewId()
    }

    fun prepareViewForSnackBar(size: Size) {
        adjustViewBounds = true
        layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.widthPixels / size.width) * size.height
        )
        scaleType = ScaleType.CENTER_CROP
        maxHeight = resources.displayMetrics.heightPixels / 3
    }

    fun prepareViewForModalWindow(currentDialog: InAppConstraintLayout) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(currentDialog)
        constraintSet.setDimensionRatio(id, MODAL_WINDOW_ASPECT_RATIO)
       updateLayoutParams {
           width = 100.dp
           height = 100.dp
       }
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