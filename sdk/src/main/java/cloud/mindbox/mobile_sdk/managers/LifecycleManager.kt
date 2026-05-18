package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cloud.mindbox.mobile_sdk.Mindbox.logE
import cloud.mindbox.mobile_sdk.Mindbox.logW
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.DIRECT
import cloud.mindbox.mobile_sdk.models.LINK
import cloud.mindbox.mobile_sdk.models.PUSH
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager.IS_OPENED_FROM_PUSH_BUNDLE_KEY
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.Timer
import kotlin.concurrent.timer

internal class LifecycleManager internal constructor(
    private var currentActivityName: String?,
    private var currentIntent: Intent?,
    private var isAppInBackground: Boolean,
) : Application.ActivityLifecycleCallbacks, LifecycleEventObserver, MindboxLog {

    internal interface Callbacks {
        fun onActivityStarted(activity: Activity) {}

        fun onActivityPaused(activity: Activity) {}

        fun onActivityResumed(activity: Activity) {}

        fun onActivityStopped(activity: Activity) {}

        fun onTrackVisitReady(source: String?, requestUrl: String?) {}
    }

    companion object {

        private const val TIMER_PERIOD = 1_200_000L
        private const val MAX_INTENT_HASHES = 50

        @Volatile
        internal var instance: LifecycleManager? = null

        internal val isRegister: Boolean get() = instance != null

        internal fun register(context: Context) {
            if (instance != null) return

            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val activity = context as? Activity
            val application = context.applicationContext as? Application
            val isForegrounded = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

            if (isForegrounded && activity == null) {
                logE("Incorrect context type for calling init in this place")
            }
            if (isForegrounded || context !is Application) {
                logW(
                    "We recommend to call Mindbox.init() synchronously from " +
                        "Application.onCreate. If you can't do so, don't forget to " +
                        "call Mindbox.initPushServices from Application.onCreate",
                )
            }

            LifecycleManager(
                currentActivityName = activity?.javaClass?.name,
                currentIntent = activity?.intent,
                isAppInBackground = !isForegrounded,
            ).also { manager ->
                application?.registerActivityLifecycleCallbacks(manager)
                lifecycle.addObserver(manager)
                instance = manager
            }
        }
    }

    /**
     * True when a foreground transition happened before [callbacks] was set —
     * i.e. before [cloud.mindbox.mobile_sdk.Mindbox.init] was called.
     */
    @Volatile
    private var pendingVisit: Boolean = false

    @Volatile
    var callbacks: Callbacks? = null
        set(value) {
            field = value
            if (value != null && pendingVisit) {
                pendingVisit = false
                dispatchCurrentVisit(value)
            }
        }

    /**
     * True by default — Activity.onResume() fires before the manager is registered
     * when Mindbox.init() is called from Activity.onCreate().
     */
    var isCurrentActivityResumed: Boolean = true
        private set

    private var intentChanged = true
    private var keepaliveTimer: Timer? = null
    private val intentHashes = mutableListOf<Int>()
    private var skipNextTrackVisit = false

    /**
     * True when [onMovedToForeground] was called while [currentIntent] was still null —
     * i.e. the app foregrounded before the first [onActivityStarted] callback arrived.
     *
     * This happens in Case 3: no [MindboxLifecycleInitializer], [Mindbox.init] called from
     * [Application.onCreate]. [ProcessLifecycleOwnerInitializer] registers [LifecycleDispatcher]
     * first, so the process-level ON_START fires *before* [LifecycleManager.onActivityStarted]
     * updates [currentIntent]. The flag is cleared and the visit is dispatched inside
     * [onActivityStarted] once the intent becomes available.
     */
    @Volatile
    private var foregroundedWithoutIntent = false

    override fun onActivityCreated(activity: Activity, p1: Bundle?) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    override fun onActivityStarted(activity: Activity): Unit = loggingRunCatching {
        mindboxLogI("onActivityStarted. activity: ${activity.javaClass.simpleName}")
        callbacks?.onActivityStarted(activity)

        val sameActivity = currentActivityName == activity.javaClass.name
        val intent = activity.intent
        intentChanged = if (currentIntent != intent) {
            updateActivityState(activity)
            intent?.hashCode()?.let(::isNewHash) ?: true
        } else {
            false
        }

        if (isAppInBackground || !intentChanged) {
            isAppInBackground = false
            if (foregroundedWithoutIntent && intentChanged) {
                foregroundedWithoutIntent = false
                sendTrackVisit(intent ?: return@loggingRunCatching)
            }
            return@loggingRunCatching
        }

        sendTrackVisit(intent ?: return@loggingRunCatching, sameActivity)
    }

    override fun onActivityResumed(activity: Activity) {
        mindboxLogI("onActivityResumed. activity: ${activity.javaClass.simpleName}")
        isCurrentActivityResumed = true
        callbacks?.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        mindboxLogI("onActivityPaused. activity: ${activity.javaClass.simpleName}")
        isCurrentActivityResumed = false
        callbacks?.onActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        mindboxLogI("onActivityStopped. activity: ${activity.javaClass.simpleName}")
        if (currentIntent == null || currentActivityName == null) {
            updateActivityState(activity)
        }
        callbacks?.onActivityStopped(activity)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> onMovedToBackground()
            Lifecycle.Event.ON_START -> onMovedToForeground()
            else -> Unit
        }
    }

    fun isTrackVisitSent(): Boolean {
        currentIntent?.let { intent ->
            if (isNewHash(intent.hashCode())) {
                sendTrackVisit(intent)
            }
        }
        return currentIntent != null
    }

    /**
     * Schedules a track-visit to be dispatched the next time [callbacks] is assigned.
     *
     * Call this before replacing [callbacks] via [cloud.mindbox.mobile_sdk.Mindbox.init]
     * so the new endpoint receives a track-visit immediately upon reinitialisation.
     * The backend uses this signal to learn the device is now active in the new environment.
     */
    fun scheduleReinitTrackVisit() {
        pendingVisit = true
        mindboxLogI("Track visit scheduled for reinit")
    }

    fun onNewIntent(newIntent: Intent?) {
        val intent = newIntent ?: return
        val hasDeepLink = intent.data != null
        val isFromPush = intent.extras?.getBoolean(IS_OPENED_FROM_PUSH_BUNDLE_KEY) == true
        if (!hasDeepLink && !isFromPush) return

        intentChanged = isNewHash(intent.hashCode())
        sendTrackVisit(intent)
        skipNextTrackVisit = isAppInBackground
    }

    private fun onMovedToBackground(): Unit = loggingRunCatching {
        mindboxLogI("onAppMovedToBackground")
        isAppInBackground = true
        pendingVisit = false
        foregroundedWithoutIntent = false
        cancelKeepaliveTimer()
    }

    private fun onMovedToForeground(): Unit = loggingRunCatching {
        mindboxLogI("onAppMovedToForeground")
        if (skipNextTrackVisit) {
            skipNextTrackVisit = false
            return@loggingRunCatching
        }
        val intent = currentIntent
        if (intent != null) {
            sendTrackVisit(intent)
        } else {
            foregroundedWithoutIntent = true
            mindboxLogI("Track visit deferred — foregrounded before first activity")
        }
    }

    private fun updateActivityState(activity: Activity): Unit = loggingRunCatching {
        currentActivityName = activity.javaClass.name
        currentIntent = activity.intent
    }

    private fun sendTrackVisit(
        intent: Intent,
        sameActivity: Boolean = true,
    ): Unit = loggingRunCatching {
        val source = if (intentChanged) intentSource(intent) else DIRECT
        if (!sameActivity && source == DIRECT) return@loggingRunCatching

        val cb = callbacks
        if (cb == null) {
            pendingVisit = true
            mindboxLogI("Track visit pending (no callbacks yet)")
            return@loggingRunCatching
        }
        pendingVisit = false
        val requestUrl = if (source == LINK) intent.data?.toString() else null
        cb.onTrackVisitReady(source, requestUrl)
        startKeepaliveTimer()
        mindboxLogI("Track visit event with source $source and url $requestUrl")
    }

    /**
     * Derives source and URL from the already-stored [currentIntent]/[intentChanged] and
     * dispatches the track-visit through [cb].
     *
     * Called from the [callbacks] setter when [pendingVisit] is raised — the same pattern
     * iOS uses in `MBSessionManager` when `initializationCompleted` fires while `isActive` is true.
     */
    private fun dispatchCurrentVisit(cb: Callbacks): Unit = loggingRunCatching {
        val intent = currentIntent ?: return@loggingRunCatching
        val source = if (intentChanged) intentSource(intent) else DIRECT
        val requestUrl = if (source == LINK) intent.data?.toString() else null
        cb.onTrackVisitReady(source, requestUrl)
        startKeepaliveTimer()
        mindboxLogI("Track visit dispatched from pending state: source=$source url=$requestUrl")
    }

    private fun intentSource(intent: Intent): String = when {
        intent.scheme == "http" || intent.scheme == "https" -> LINK
        intent.extras?.getBoolean(IS_OPENED_FROM_PUSH_BUNDLE_KEY) == true -> PUSH
        else -> DIRECT
    }

    private fun isNewHash(hash: Int): Boolean = loggingRunCatching(defaultValue = true) {
        if (intentHashes.contains(hash)) return@loggingRunCatching false
        if (intentHashes.size >= MAX_INTENT_HASHES) intentHashes.removeAt(0)
        intentHashes.add(hash)
        true
    }

    private fun startKeepaliveTimer(): Unit = loggingRunCatching {
        cancelKeepaliveTimer()
        keepaliveTimer = timer(
            initialDelay = TIMER_PERIOD,
            period = TIMER_PERIOD,
            action = { callbacks?.onTrackVisitReady(null, null) },
        )
    }

    private fun cancelKeepaliveTimer(): Unit = loggingRunCatching {
        keepaliveTimer?.cancel()
        keepaliveTimer = null
    }
}
