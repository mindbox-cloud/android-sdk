package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import org.robolectric.Shadows.shadowOf
import java.io.Closeable

internal fun placeAnswer(
    sessionEpoch: SessionEpoch = SessionEpoch(0L),
    isByOperation: Boolean = false,
    selectionTime: Milliseconds = Milliseconds(0L),
): EmbeddedPlaceAnswer = EmbeddedPlaceAnswer(sessionEpoch, isByOperation, selectionTime)

internal fun EmbeddedBlockHandle.onContentResolved(content: InAppType.Embedded?) = onContentResolved(content, placeAnswer())

internal fun EmbeddedBlockHandle.onContentResolved(content: InAppType.Embedded?, selectionTime: Milliseconds) =
    onContentResolved(content, placeAnswer(selectionTime = selectionTime))

internal fun EmbeddedBlockHandle.onConfigUnavailable() = onConfigUnavailable(placeAnswer())

internal class RecordingBlocksRegistry : EmbeddedBlocksRegistry {
    var lastHandle: EmbeddedBlockHandle? = null

    override fun onBlockContentDropped(placeSystemName: PlaceKey) = Unit

    var endedSessions = setOf<SessionEpoch>()

    override fun isLiveSession(sessionEpoch: SessionEpoch): Boolean = sessionEpoch !in endedSessions

    override fun deferUntilReturnChecked(): Boolean = false

    override fun onAppResumedOn(activity: Activity) = Unit

    override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer) =
        PlaceShowReservation.RESERVED

    override fun giveBackParkedShow(placeSystemName: PlaceKey, sessionEpoch: SessionEpoch) = Unit

    override fun onReturnCheckOver() = Unit

    override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
        lastHandle = handle
        return Closeable { lastHandle = null }
    }

    var appearedCount = 0

    override fun onBlockAppeared(placeSystemName: PlaceKey) {
        appearedCount++
    }

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
