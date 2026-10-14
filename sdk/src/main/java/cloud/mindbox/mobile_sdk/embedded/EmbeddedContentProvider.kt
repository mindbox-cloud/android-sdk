package cloud.mindbox.mobile_sdk.embedded

import android.view.View
import androidx.annotation.MainThread

internal interface EmbeddedContentProvider {

    var onStateChange: ((EmbeddedBlockState) -> Unit)?

    val contentView: View?

    @MainThread
    fun start()

    @MainThread
    fun pause()

    @MainThread
    fun release()
}
