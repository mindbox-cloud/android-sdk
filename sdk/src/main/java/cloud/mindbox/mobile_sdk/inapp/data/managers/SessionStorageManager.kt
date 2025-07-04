package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var unShownOperationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    var operationalInApps: HashMap<String, MutableList<InApp>> = hashMapOf()
    var inAppMessageShownInSession: MutableList<String> = mutableListOf()
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var inAppProductSegmentations: HashMap<Pair<String, String>, Set<ProductSegmentationResponseWrapper>> =
        HashMap()
    var processedProductSegmentations: MutableMap<Pair<String, String>, ProductSegmentationFetchStatus> = mutableMapOf()
    var currentSessionInApps: List<InApp> = emptyList()
    var shownInAppIdsWithEvents = mutableMapOf<String, MutableSet<Int>>()
    var configFetchingError: Boolean = false
    var sessionTime: Duration = 0L.milliseconds
    var inAppShowLimitsSettings: InAppShowLimitsSettings = InAppShowLimitsSettings()

    val lastTrackVisitSendTime: AtomicLong = AtomicLong(0L)

    private val sessionExpirationListeners = mutableListOf<SessionExpirationListener>()

    private var wasSessionExpiredOnLastCheck: Boolean = false

    fun addSessionExpirationListener(listener: SessionExpirationListener) {
        sessionExpirationListeners.add(listener)
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

    fun isSessionActive() = !wasSessionExpiredOnLastCheck

    fun clearSessionData() {
        inAppCustomerSegmentations = null
        unShownOperationalInApps.clear()
        operationalInApps.clear()
        inAppMessageShownInSession.clear()
        customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        inAppProductSegmentations.clear()
        processedProductSegmentations.clear()
        currentSessionInApps = emptyList()
        shownInAppIdsWithEvents.clear()
        configFetchingError = false
        sessionTime = 0L.milliseconds
        inAppShowLimitsSettings = InAppShowLimitsSettings()
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
