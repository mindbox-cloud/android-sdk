package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

private typealias SessionExpirationListener = () -> Unit

internal class SessionStorageManager(private val timeProvider: TimeProvider) {

    var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var unShownOperationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    var operationalInApps: HashMap<String, MutableList<InApp>> = hashMapOf()
    var isInAppMessageShown: Boolean = false
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var productSegmentationFetchStatus: ProductSegmentationFetchStatus =
        ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var inAppProductSegmentations: HashMap<String, Set<ProductSegmentationResponseWrapper>> =
        HashMap()
    var currentSessionInApps: List<InApp> = emptyList()
    var shownInAppIdsWithEvents = mutableMapOf<String, MutableSet<Int>>()
    var configFetchingError: Boolean = false
    var lastTrackVisitSendTime: Long = 0L
    var sessionTime: Long = 0L

    private val sessionExpirationListeners = mutableListOf<SessionExpirationListener>()

    fun addSessionExpirationListener(listener: SessionExpirationListener) {
        sessionExpirationListeners.add(listener)
    }

    fun hasSessionExpired() {
        val currentTime = timeProvider.currentTimeMillis()
        val timeBetweenVisits = currentTime - lastTrackVisitSendTime
        val checkingSessionResultLog = when {
            lastTrackVisitSendTime == 0L -> "First track visit on sdk init"

            sessionTime < 0L -> "Session time is incorrect. Session time is $sessionTime ms. Skip checking session expiration"

            sessionTime == 0L -> "Session time is not set. Skip checking session expiration"

            timeBetweenVisits > sessionTime -> {
                notifySessionExpired()
                "Session expired. Needs to open a new session. Time between trackVisits is $timeBetweenVisits ms. Session time is $sessionTime ms"
            }

            else -> {
                "Session active. Updating lastTrackVisitSendTime. Time between trackVisits is $timeBetweenVisits ms. Session time is $sessionTime ms"
            }
        }
        lastTrackVisitSendTime = currentTime
        mindboxLogI("$checkingSessionResultLog. New lastTrackVisitSendTime = $currentTime")
    }

    fun clearSessionData() {
        inAppCustomerSegmentations = null
        unShownOperationalInApps.clear()
        operationalInApps.clear()
        isInAppMessageShown = false
        customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        productSegmentationFetchStatus = ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        inAppProductSegmentations.clear()
        currentSessionInApps = emptyList()
        shownInAppIdsWithEvents.clear()
        configFetchingError = false
        sessionTime = 0L
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
