package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    @Volatile var state: SessionState = SessionState()
        private set

    val showBudgetLock = Any()

    private val sessionEpochs = MutableStateFlow(0L)

    val sessionEpoch: Long
        get() = sessionEpochs.value

    @Volatile var isSessionEnding: Boolean = false
        private set

    private val isReturnCheckPending = MutableStateFlow(false)

    val returnCheckPending: StateFlow<Boolean>
        get() = isReturnCheckPending

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
        val check = synchronized(showBudgetLock) { checkSession() }
        if (check.isExpired) notifySessionExpired()
        mindboxLogI(check.log)
    }

    private class SessionCheck(val isExpired: Boolean, val log: String)

    private fun checkSession(): SessionCheck {
        wasSessionExpiredOnLastCheck = false
        val currentTime = timeProvider.currentTimeMillis()
        val oldLastTrackVisitSendTime = lastTrackVisitSendTime.getAndSet(currentTime)
        val timeBetweenVisits = currentTime - oldLastTrackVisitSendTime
        val currentSessionTime = state.sessionTime.inWholeMilliseconds
        val isExpired = oldLastTrackVisitSendTime != 0L && currentSessionTime > 0L && timeBetweenVisits > currentSessionTime
        val checkingSessionResultLog = when {
            oldLastTrackVisitSendTime == 0L -> "First track visit on sdk init"

            currentSessionTime < 0L -> "Session time is incorrect. Session time is $currentSessionTime ms. Skip checking session expiration"

            currentSessionTime == 0L -> "Session time is not set. Skip checking session expiration"

            isExpired -> {
                wasSessionExpiredOnLastCheck = true
                if (sessionExpirationListeners.isNotEmpty()) isSessionEnding = true
                "Session expired. Needs to open a new session. Time between trackVisits is $timeBetweenVisits ms. Session time is $currentSessionTime ms"
            }

            else -> {
                "Session active. Updating lastTrackVisitSendTime. Time between trackVisits is $timeBetweenVisits ms. Session time is $currentSessionTime ms"
            }
        }
        return SessionCheck(isExpired, "$checkingSessionResultLog. New lastTrackVisitSendTime = $currentTime")
    }

    fun isSessionExpiredOnLastCheck() = wasSessionExpiredOnLastCheck

    fun clearSessionData() = synchronized(showBudgetLock) {
        state = SessionState()
        sessionEpochs.value = sessionEpochs.value + 1
        isSessionEnding = false
    }

    fun listenSessionEpoch(): StateFlow<Long> = sessionEpochs

    fun onAppLeftForeground() {
        isReturnCheckPending.value = true
    }

    fun onReturnChecked() {
        isReturnCheckPending.value = false
    }

    suspend fun awaitReturnChecked() {
        isReturnCheckPending.first { isPending -> !isPending }
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
