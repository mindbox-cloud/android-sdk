package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import android.view.View

internal object EmbeddedBlockDefaultViews {

    fun placeholder(context: Context): View = EmbeddedBlockShimmerView(context)
}
