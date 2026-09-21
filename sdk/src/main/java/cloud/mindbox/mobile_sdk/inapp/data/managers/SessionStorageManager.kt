package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    @Volatile var state: SessionState = SessionState()
        private set

    val showBudgetLock = Any()

    var lastTrackVisitData: TrackVisitData? = null

    val lastTrackVisitSendTime: AtomicLong = AtomicLong(0L)

    private val sessionExpirationListeners = CopyOnWriteArrayList<SessionExpirationListener>()

    private var wasSessionExpiredOnLastCheck: Boolean = false

    fun addSessionExpirationListener(listener: SessionExpirationListener) {
        sessionExpirationListeners.add(listener)
    }

    fun removeSessionExpirationListener(listener: SessionExpirationListener) {
        sessionExpirationListeners.remove(listener)
    }

    fun hasSessionExpired() {
        wasSessionExpiredOnLastCheck = false
        val currentTime = timeProvider.currentTimeMillis()
        val oldLastTrackVisitSendTime = lastTrackVisitSendTime.getAndSet(currentTime)
        val timeBetweenVisits = currentTime - oldLastTrackVisitSendTime
        val currentSessionTime = state.sessionTime.inWholeMilliseconds
        val checkingSessionResultLog = when {
            oldLastTrackVisitSendTime == 0L -> "First track visit on sdk init"

            currentSessionTime < 0L -> "Session time is incorrect. Session time is $currentSessionTime ms. Skip checking session expiration"

            currentSessionTime == 0L -> "Session time is not set. Skip checking session expiration"

            timeBetweenVisits > currentSessionTime -> {
                wasSessionExpiredOnLastCheck = true
                notifySessionExpired()
                "Session expired. Needs to open a new session. Time between trackVisits is $timeBetweenVisits ms. Session time is $currentSessionTime ms"
            }

            else -> {
                "Session active. Updating lastTrackVisitSendTime. Time between trackVisits is $timeBetweenVisits ms. Session time is $currentSessionTime ms"
            }
        }
        mindboxLogI("$checkingSessionResultLog. New lastTrackVisitSendTime = $currentTime")
    }

    fun isSessionExpiredOnLastCheck() = wasSessionExpiredOnLastCheck

    fun clearSessionData() = synchronized(showBudgetLock) {
        state = SessionState()
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
