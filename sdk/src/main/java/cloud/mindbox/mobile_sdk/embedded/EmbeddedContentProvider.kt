package cloud.mindbox.mobile_sdk.embedded

import android.view.View
import androidx.annotation.MainThread

internal interface EmbeddedContentProvider {

    var onStateChange: ((EmbeddedBlockState) -> Unit)?

    val contentView: View?

    @get:MainThread
    val isAttemptAlive: Boolean
        get() = true

    @MainThread
    fun start()

    @MainThread
    fun pause()

    @MainThread
    fun release()
}
