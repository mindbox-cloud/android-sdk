package cloud.mindbox.mobile_sdk.embedded

import android.view.View

internal interface EmbeddedContentProvider {

    var onStateChange: ((EmbeddedBlockState) -> Unit)?

    val contentView: View?

    fun start()

    fun pause()

    fun release()
}
