package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.lang.ref.WeakReference

internal class EmbeddedBlockContentStore(
    private val maxRetained: Int = Constants.Embedded.MAX_RETAINED_CONTENTS,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : ComponentCallbacks2 {

    private inner class Entry(
        owner: LifecycleOwner,
        val placeSystemName: PlaceKey,
        val controller: EmbeddedBlockContentController,
        activity: Activity?,
    ) {
        private val ownerReference = WeakReference(owner)
        private val activityReference = WeakReference(activity)
        private val ownerLifecycle: Lifecycle = owner.lifecycle
        private val activityLifecycle: Lifecycle? = (activity as? LifecycleOwner)?.lifecycle

        private val ownerObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) release(this, "the screen left the back stack")
        }
        private val activityObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) release(this, "the activity was destroyed")
        }

        val owner: LifecycleOwner?
            get() = ownerReference.get()

        val isScreenGone: Boolean
            get() = owner == null || ownerLifecycle.currentState == Lifecycle.State.DESTROYED

        fun matches(owner: LifecycleOwner, placeSystemName: PlaceKey, activity: Activity?): Boolean =
            this.owner === owner && this.placeSystemName == placeSystemName && activityReference.get() === activity

        fun watch() {
            ownerLifecycle.addObserver(ownerObserver)
            activityLifecycle?.addObserver(activityObserver)
        }

        fun unwatch() {
            ownerLifecycle.removeObserver(ownerObserver)
            activityLifecycle?.removeObserver(activityObserver)
        }
    }

    private val entries = ArrayDeque<Entry>()

    private var isTrimScheduled = false
    private val trimToLimitRunnable = Runnable { trimToLimit() }

    val size: Int
        get() = entries.size

    fun retain(
        owner: LifecycleOwner,
        placeSystemName: PlaceKey,
        controller: EmbeddedBlockContentController,
        activity: Activity?,
    ) {
        releaseGoneScreens()
        val isActivityGone = (activity as? LifecycleOwner)?.lifecycle?.currentState == Lifecycle.State.DESTROYED
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED || isActivityGone) {
            mindboxLogI("[EmbeddedBlock] The screen of '$placeSystemName' is already destroyed, freeing its content")
            loggingRunCatching { controller.release() }
            return
        }
        val entry = Entry(owner, placeSystemName, controller, activity)
        entry.watch()
        entries.addLast(entry)
        mindboxLogI("[EmbeddedBlock] Keeping the content of '$placeSystemName' for its screen ($size of $maxRetained kept)")
        if (entries.size > maxRetained && !isTrimScheduled) {
            isTrimScheduled = true
            mainHandler.post(trimToLimitRunnable)
        }
    }

    fun trimToLimit() {
        isTrimScheduled = false
        releaseGoneScreens()
        while (entries.size > maxRetained) {
            val oldest = entries.removeFirst()
            oldest.unwatch()
            mindboxLogI(
                "[EmbeddedBlock] Kept contents over the limit of $maxRetained, freeing the oldest " +
                    "('${oldest.placeSystemName}')",
            )
            loggingRunCatching { oldest.controller.release() }
        }
    }

    fun reclaim(
        owner: LifecycleOwner,
        placeSystemName: PlaceKey,
        activity: Activity?,
    ): EmbeddedBlockContentController? {
        releaseGoneScreens()
        val entry = entries.lastOrNull { kept -> kept.matches(owner, placeSystemName, activity) } ?: return null
        entries.remove(entry)
        entry.unwatch()
        if (!entry.controller.isRetainable) {
            mindboxLogI("[EmbeddedBlock] The kept content of '$placeSystemName' is no longer showable, freeing it")
            loggingRunCatching { entry.controller.release() }
            return null
        }
        mindboxLogI("[EmbeddedBlock] Handing the kept content of '$placeSystemName' back to its screen ($size kept)")
        return entry.controller
    }

    fun releaseAll(reason: String) {
        if (entries.isEmpty()) return
        val kept = entries.toList()
        entries.clear()
        mindboxLogI("[EmbeddedBlock] Freeing ${kept.size} kept content(s): $reason")
        kept.forEach { entry ->
            entry.unwatch()
            loggingRunCatching { entry.controller.release() }
        }
    }

    override fun onTrimMemory(level: Int) {
        if (isMemoryPressure(level)) releaseAll("memory trim, level $level")
    }

    @Suppress("DEPRECATION")
    private fun isMemoryPressure(level: Int): Boolean =
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun release(entry: Entry, reason: String) {
        if (!entries.remove(entry)) return
        entry.unwatch()
        mindboxLogI("[EmbeddedBlock] Freeing the kept content of '${entry.placeSystemName}': $reason")
        loggingRunCatching { entry.controller.release() }
    }

    private fun releaseGoneScreens() {
        entries.filter { entry -> entry.isScreenGone }.forEach { goneScreen -> release(goneScreen, "the screen is gone") }
    }
}
