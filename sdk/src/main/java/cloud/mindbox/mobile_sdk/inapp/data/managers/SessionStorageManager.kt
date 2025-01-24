package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class SessionStorageManager {

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
    var currentSessionInApps: List<InApp> = mutableListOf()
    var shownInAppIdsWithEvents = mutableMapOf<String, MutableSet<Int>>()
    var configFetchingError: Boolean = false
    var lastTrackVisitSendTime: Long = 0L
    var sessionTime: Long = 0

    private val sessionExpirationListeners = mutableSetOf<() -> Unit>()

    fun addSessionExpirationListener(listener: () -> Unit) {
        sessionExpirationListeners.add(listener)
    }

    fun hasSessionExpired(currentTime: Long) {
        val timeBetweenVisits = currentTime - lastTrackVisitSendTime
        val checkingSessionResultLog = when {
            lastTrackVisitSendTime == 0L -> "First track visit on sdk init"

            sessionTime == 0L -> "Session time is not set. Skip checking session expiration"

            timeBetweenVisits > sessionTime -> {
                notifySessionExpired()
                "Session expired. Needs to open a new session. Time between trackVisits is $timeBetweenVisits ms. Session time is $sessionTime ms"
            }

            else -> {
                "Session active. Updating lastTrackVisitSendTime. Time between trackVisits is $timeBetweenVisits ms. Session time is $sessionTime ms"
            }
        }
        updateLastTrackTime(currentTime, checkingSessionResultLog)
    }

    private fun updateLastTrackTime(currentTime: Long, logMessage: String) {
        lastTrackVisitSendTime = currentTime
        mindboxLogD("$logMessage. New lastTrackVisitSendTime = $currentTime")
    }

    private fun notifySessionExpired() {
        sessionExpirationListeners.forEach {
            loggingRunCatching {
                it.invoke()
            }
        }
    }
}
