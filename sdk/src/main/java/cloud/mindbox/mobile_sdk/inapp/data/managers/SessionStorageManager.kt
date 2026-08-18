package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.newConcurrentSet
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    @Volatile var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var unShownOperationalInApps: ConcurrentHashMap<String, MutableList<InApp>> = ConcurrentHashMap()
    var operationalInApps: ConcurrentHashMap<String, MutableList<InApp>> = ConcurrentHashMap()
    var inAppMessageShownInSession: MutableList<String> = CopyOnWriteArrayList()

    /**
     * In-apps whose embedded block has already reported `Inapp.Show` this session. Separate from
     * [inAppMessageShownInSession]: that one is not written for `unlimited`, while the show
     * operation ships once per session whatever the frequency.
     */
    val blockShowsReportedInSession: MutableSet<String> = newConcurrentSet()
    val placeTargetingReportedInSession: MutableSet<String> = newConcurrentSet()
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var inAppProductSegmentations: MutableMap<Pair<String, String>, Set<ProductSegmentationResponseWrapper>> =
        ConcurrentHashMap()
    var processedProductSegmentations: MutableMap<Pair<String, String>, ProductSegmentationFetchStatus> = ConcurrentHashMap()
    var lastTargetingErrors: MutableMap<TargetingErrorKey, String> = ConcurrentHashMap()

    @Volatile var currentSessionInApps: List<InApp> = emptyList()
    var shownInAppIdsWithEvents: ConcurrentHashMap<String, MutableSet<Int>> = ConcurrentHashMap()
    var configFetchingError: Boolean = false
    var sessionTime: Duration = 0L.milliseconds
    var inAppShowLimitsSettings: InAppShowLimitsSettings = InAppShowLimitsSettings()
    var lastTrackVisitData: TrackVisitData? = null
    var inAppTriggerEvent: InAppEventType? = null

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

    fun clearSessionData() {
        inAppCustomerSegmentations = null
        unShownOperationalInApps.clear()
        operationalInApps.clear()
        inAppMessageShownInSession.clear()
        blockShowsReportedInSession.clear()
        placeTargetingReportedInSession.clear()
        customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        inAppProductSegmentations.clear()
        processedProductSegmentations.clear()
        lastTargetingErrors.clear()
        currentSessionInApps = emptyList()
        shownInAppIdsWithEvents.clear()
        configFetchingError = false
        sessionTime = 0L.milliseconds
        inAppShowLimitsSettings = InAppShowLimitsSettings()
        inAppTriggerEvent = null
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
