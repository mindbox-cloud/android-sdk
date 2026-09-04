package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    suspend fun listenToTargetingEvents()

    /**
     * Emits after every parsed config update — unlike the raw preference flow, this fires only
     * when the new config is actually applied, so a re-resolve never reads the previous one.
     */
    fun listenConfigUpdates(): Flow<Unit>

    /**
     * Resolves content for an embedded place. The targeting pass runs first — place filter,
     * then targeting, over the full list (the cut A/B branch and the frequency-blocked
     * included, `directCall` out) — and sends targeting for everyone who matched: the losers once per
     * session, the winner through the place's "last targeted" slot (its show goes by the
     * "last shown" one, so the pair assembles itself). The
     * show itself still picks one winner through the A/B pool, the frequency, the priority
     * and the show limits — parity with the overlay. Only the `isInAppActive` lock and the
     * delayed queue stay out: those are overlay machinery. The pull side passes
     * [InAppEventType.EmbeddedPlaceRequested] as [triggerEvent]; the push side passes the
     * matched operation. Suspends until the config arrives.
     */
    suspend fun selectInAppForPlace(
        placeSystemName: String,
        triggerEvent: InAppEventType,
    ): EmbeddedResolveResult?

    /**
     * The in-app with [inAppId] and its overlay variant for a direct call: no restriction —
     * frequency, limits, `displayConditions`, targeting, the A/B pool — is checked. A drawn
     * element must open. Returns `null` only for an unknown id, an id filtered out by
     * `sdkVersion`, or a form with no overlay variant (embedded is drawn inside the host layout).
     */
    suspend fun getInAppToShowById(inAppId: String): InAppToShow?

    /**
     * The push side of the blocks registry: every **live** operation (the replay cache is
     * deliberately skipped — "the operation is happening right now") that matches the
     * operation-targeting of an embedded in-app is emitted as a place-event candidate. No
     * resolve happens here: the registry intersects candidates with its block list and runs
     * the same place resolve with the operation as the trigger, so pull and push share one
     * dedup. An operation with no embedded candidates emits nothing and costs no network.
     */
    fun listenEmbeddedPlaceEvents(): Flow<EmbeddedPlaceEvent>

    /**
     * The single place where the page's requested ids are cut: the answer to `filterShowableInapps`.
     * Keeps the ids whose in-apps exist in the version- and A/B-filtered list, pass the
     * frequency rule (an exhausted non-unlimited in-app drops out of the answer, `unlimited` always
     * passes — decision 17.08), match targeting (no network fetches — the page waits
     * 3 seconds) and are not embedded. `directCall` and the show limits are deliberately not
     * checked: a drawn element must open.
     *
     * The answer mirrors the request, duplicates included. `Inapp.Targeting` goes out at the
     * moment the SDK computes the answer (delivery does not matter), over the **full** list —
     * a requested id in the cut A/B branch keeps its funnel denominator — and once per session per
     * `host in-app + requested id` pair: a repeated request reports only the new ones. A tap
     * reports nothing here — the in-app is not shown yet.
     */
    suspend fun filterShowableInAppIds(hostInAppId: String, inAppIds: List<String>): List<String>

    suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>>

    fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    )

    /**
     * The embedded block drew its content. Compared against the place's "last shown" slot:
     * a changed in-app ships the `Inapp.Show` half of the pair and — for a frequency that
     * counts shows at all — writes the history and moves the shared cooldown, exactly like an
     * overlay show; the same in-app repeated (a rotation, a recreated page) stays silent, in
     * counters too. Everything comes from the snapshot the content carries — the config may
     * have moved on since the resolve. [tags] arrive already gated by the caller.
     */
    fun recordBlockShow(
        placeSystemName: String,
        inAppId: String,
        frequency: Frequency,
        timeToDisplay: Milliseconds,
        tags: Map<String, String>?,
    )

    /** The winner's `delayTime` elapsed on this place: a later resolve this session hands it out with no delay. */
    fun markEmbeddedDelayWaitedOut(placeSystemName: String, inAppId: String)

    fun reservePlaceShow(placeSystemName: String, content: InAppType.Embedded): Boolean

    fun releasePlaceShow(placeSystemName: String)

    fun reserveOverlayShow(inApp: InApp): ShowReservationOutcome

    fun releaseOverlayShow(inAppId: String)

    fun sendInAppClicked(inAppId: String, tags: Map<String, String>?)

    suspend fun fetchMobileConfig()

    fun resetInAppConfigAndEvents()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveInAppDismissTime(inApp: InApp)
}

internal data class InAppToShow(
    val inApp: InApp,
    val variant: InAppType,
)

/**
 * A resolved place: the content to render and the winner's show delay — the campaign's choice,
 * applied by the registry before the delivery.
 */
internal data class EmbeddedResolveResult(
    val variant: InAppType.Embedded,
    val delayTime: Milliseconds?,
)
