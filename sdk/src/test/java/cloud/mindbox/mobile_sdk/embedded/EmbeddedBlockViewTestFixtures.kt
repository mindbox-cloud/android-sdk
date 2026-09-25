package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.models.PlaceKey
import org.robolectric.Shadows.shadowOf
import java.io.Closeable

internal class RecordingBlocksRegistry : EmbeddedBlocksRegistry {
    var lastHandle: EmbeddedBlockHandle? = null

    override fun onBlockContentDropped(placeSystemName: PlaceKey) = Unit

    override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
        lastHandle = handle
        return Closeable { lastHandle = null }
    }

    override fun onBlockAppeared(placeSystemName: PlaceKey) = Unit

    override fun startListening() = Unit
}

internal class RecordingListener : MindboxEmbeddedBlockListener {
    val events = mutableListOf<String>()

    override fun onLoad(view: MindboxEmbeddedBlockView) {
        events.add("load")
    }

    override fun onEmpty(view: MindboxEmbeddedBlockView) {
        events.add("empty")
    }

    override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {
        events.add("fail:${reason.value}")
    }
}

internal fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
}

internal fun Activity.attachBlock(view: MindboxEmbeddedBlockView) {
    setContentView(LinearLayout(this).apply { addView(view, 500, 300) })
    idleMainLooper()
    dispatchWindowVisibility(view, View.VISIBLE)
    idleMainLooper()
}

internal fun leaveAndReturn(view: MindboxEmbeddedBlockView) {
    dispatchWindowVisibility(view, View.GONE)
    idleMainLooper()
    dispatchWindowVisibility(view, View.VISIBLE)
    idleMainLooper()
}
