package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.ConfigVersion
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import cloud.mindbox.mobile_sdk.models.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface InAppInteractor {

    val sessionEpoch: SessionEpoch

    fun isLiveSession(sessionEpoch: SessionEpoch): Boolean

    fun listenSessionEpoch(): StateFlow<SessionEpoch>

    val returnCheckPending: StateFlow<Boolean>

    suspend fun awaitReturnChecked()

    suspend fun listenToTargetingEvents()

    fun listenConfigVersions(): Flow<ConfigVersion>

    suspend fun selectInAppForPlace(
        placeSystemName: PlaceKey,
        triggerEvent: InAppEventType,
    ): EmbeddedResolveOutcome

    suspend fun getInAppToShowById(inAppId: String): InAppToShow?

    fun listenEmbeddedPlaceEvents(): Flow<EmbeddedPlaceEvent>

    suspend fun filterShowableInAppIds(hostInAppId: String, inAppIds: List<String>): List<String>?

    suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>>

    fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    )

    fun recordBlockShow(
        placeSystemName: PlaceKey,
        inAppId: String,
        frequency: Frequency,
        timeToDisplay: Milliseconds,
        tags: Map<String, String>?,
        sessionEpoch: SessionEpoch,
    ): Boolean

    fun markEmbeddedDelayWaitedOut(placeSystemName: PlaceKey, inAppId: String, sessionEpoch: SessionEpoch): Boolean

    fun reservePlaceShow(placeSystemName: PlaceKey, content: InAppType.Embedded, sessionEpoch: SessionEpoch): PlaceShowReservation

    fun releasePlaceShow(placeSystemName: PlaceKey)

    fun releasePlaceShow(placeSystemName: PlaceKey, sessionEpoch: SessionEpoch): Boolean

    fun reserveOverlayShow(inApp: InApp): ShowReservationOutcome

    fun releaseOverlayShow(inAppId: String)

    fun sendInAppClicked(inAppId: String, tags: Map<String, String>?)

    suspend fun fetchMobileConfig()

    fun beginNewSession()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveInAppDismissTime(inApp: InApp, shownAt: Timestamp)
}

internal enum class PlaceShowReservation { RESERVED, REFUSED, STALE }

internal data class InAppToShow(
    val inApp: InApp,
    val variant: InAppType,
)

internal sealed class EmbeddedResolveOutcome {

    abstract val configVersion: ConfigVersion

    data class Content(
        val variant: InAppType.Embedded,
        val delayTime: Milliseconds?,
        override val configVersion: ConfigVersion,
    ) : EmbeddedResolveOutcome()

    data class Empty(override val configVersion: ConfigVersion) : EmbeddedResolveOutcome()

    data class ConfigUnavailable(override val configVersion: ConfigVersion) : EmbeddedResolveOutcome()
}
