package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetOwner
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingErrorKey
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.newConcurrentSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class SessionState(
    @Volatile var inAppCustomerSegmentations: SegmentationCheckWrapper? = null,
    var unShownOperationalInApps: ConcurrentHashMap<String, MutableList<InApp>> = ConcurrentHashMap(),
    var operationalInApps: ConcurrentHashMap<String, MutableList<InApp>> = ConcurrentHashMap(),
    var inAppMessageShownInSession: MutableList<String> = CopyOnWriteArrayList(),
    val embeddedLastShownByPlace: ConcurrentHashMap<PlaceKey, String> = ConcurrentHashMap(),
    val embeddedLastTargetedByPlace: ConcurrentHashMap<PlaceKey, String> = ConcurrentHashMap(),
    val embeddedLastOperationByPlace: ConcurrentHashMap<PlaceKey, InAppEventType.OrdinalEvent> = ConcurrentHashMap(),
    val placeTargetingReportedInSession: MutableSet<String> = newConcurrentSet(),
    val embeddedDelaysWaitedOut: MutableSet<String> = newConcurrentSet(),
    val requestedInAppTargetingReportedInSession: MutableSet<String> = newConcurrentSet(),
    val waitBudgetReportedPlaces: MutableSet<PlaceKey> = newConcurrentSet(),
    val reportedShowFailures: MutableSet<String> = newConcurrentSet(),
    val showReservations: MutableMap<ShowBudgetOwner, ShowReservation> = ConcurrentHashMap(),
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED,
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED,
    var inAppProductSegmentations: MutableMap<Pair<String, String>, Set<ProductSegmentationResponseWrapper>> =
        ConcurrentHashMap(),
    var processedProductSegmentations: MutableMap<Pair<String, String>, ProductSegmentationFetchStatus> =
        ConcurrentHashMap(),
    var lastTargetingErrors: MutableMap<TargetingErrorKey, String> = ConcurrentHashMap(),
    @Volatile var currentSessionInApps: List<InApp> = emptyList(),
    var shownInAppIdsWithEvents: ConcurrentHashMap<String, MutableSet<Int>> = ConcurrentHashMap(),
    var configFetchingError: Boolean = false,
    var sessionTime: Duration = 0L.milliseconds,
    var inAppShowLimitsSettings: InAppShowLimitsSettings = InAppShowLimitsSettings(),
    var inAppTriggerEvent: InAppEventType? = null,
)
