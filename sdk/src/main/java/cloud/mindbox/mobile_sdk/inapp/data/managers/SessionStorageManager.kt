package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetOwner
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    @Volatile private var state: SessionState = SessionState()

    val showBudgetLock = Any()

    var lastTrackVisitData: TrackVisitData? = null

    val lastTrackVisitSendTime: AtomicLong = AtomicLong(0L)

    var inAppCustomerSegmentations: SegmentationCheckWrapper?
        get() = state.inAppCustomerSegmentations
        set(value) {
            state.inAppCustomerSegmentations = value
        }
    var unShownOperationalInApps: ConcurrentHashMap<String, MutableList<InApp>>
        get() = state.unShownOperationalInApps
        set(value) {
            state.unShownOperationalInApps = value
        }
    var operationalInApps: ConcurrentHashMap<String, MutableList<InApp>>
        get() = state.operationalInApps
        set(value) {
            state.operationalInApps = value
        }
    var inAppMessageShownInSession: MutableList<String>
        get() = state.inAppMessageShownInSession
        set(value) {
            state.inAppMessageShownInSession = value
        }
    val embeddedLastShownByPlace: ConcurrentHashMap<PlaceKey, String> get() = state.embeddedLastShownByPlace
    val embeddedLastTargetedByPlace: ConcurrentHashMap<PlaceKey, String> get() = state.embeddedLastTargetedByPlace
    val embeddedLastOperationByPlace: ConcurrentHashMap<PlaceKey, InAppEventType.OrdinalEvent>
        get() = state.embeddedLastOperationByPlace
    val placeTargetingReportedInSession: MutableSet<String> get() = state.placeTargetingReportedInSession
    val embeddedDelaysWaitedOut: MutableSet<String> get() = state.embeddedDelaysWaitedOut
    val requestedInAppTargetingReportedInSession: MutableSet<String>
        get() = state.requestedInAppTargetingReportedInSession
    val waitBudgetReportedPlaces: MutableSet<PlaceKey> get() = state.waitBudgetReportedPlaces
    val reportedShowFailures: MutableSet<String> get() = state.reportedShowFailures
    val showReservations: MutableMap<ShowBudgetOwner, ShowReservation> get() = state.showReservations
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus
        get() = state.customerSegmentationFetchStatus
        set(value) {
            state.customerSegmentationFetchStatus = value
        }
    var geoFetchStatus: GeoFetchStatus
        get() = state.geoFetchStatus
        set(value) {
            state.geoFetchStatus = value
        }
    var inAppProductSegmentations: MutableMap<Pair<String, String>, Set<ProductSegmentationResponseWrapper>>
        get() = state.inAppProductSegmentations
        set(value) {
            state.inAppProductSegmentations = value
        }
    var processedProductSegmentations: MutableMap<Pair<String, String>, ProductSegmentationFetchStatus>
        get() = state.processedProductSegmentations
        set(value) {
            state.processedProductSegmentations = value
        }
    var lastTargetingErrors: MutableMap<TargetingErrorKey, String>
        get() = state.lastTargetingErrors
        set(value) {
            state.lastTargetingErrors = value
        }
    var currentSessionInApps: List<InApp>
        get() = state.currentSessionInApps
        set(value) {
            state.currentSessionInApps = value
        }
    var shownInAppIdsWithEvents: ConcurrentHashMap<String, MutableSet<Int>>
        get() = state.shownInAppIdsWithEvents
        set(value) {
            state.shownInAppIdsWithEvents = value
        }
    var configFetchingError: Boolean
        get() = state.configFetchingError
        set(value) {
            state.configFetchingError = value
        }
    var sessionTime: Duration
        get() = state.sessionTime
        set(value) {
            state.sessionTime = value
        }
    var inAppShowLimitsSettings: InAppShowLimitsSettings
        get() = state.inAppShowLimitsSettings
        set(value) {
            state.inAppShowLimitsSettings = value
        }
    var inAppTriggerEvent: InAppEventType?
        get() = state.inAppTriggerEvent
        set(value) {
            state.inAppTriggerEvent = value
        }

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
        val currentSessionTime = sessionTime.inWholeMilliseconds
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
