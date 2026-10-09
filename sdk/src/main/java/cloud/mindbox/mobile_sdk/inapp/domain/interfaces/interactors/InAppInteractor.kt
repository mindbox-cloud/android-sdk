package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface InAppInteractor {

    val sessionEpoch: Long

    /**
     * Whether [sessionEpoch] is the current session and that session has not been found expired:
     * between the expiry check and the wipe of its data the current session no longer counts as
     * live. The one predicate every answer, delay, reservation and show of an embedded block is
     * judged by.
     */
    fun isLiveSession(sessionEpoch: Long): Boolean

    /**
     * The current session epoch, moved on each time the session data is wiped for a new session.
     * A subscriber gets the current value first, so a renewal that happened while nothing listened
     * is still seen.
     */
    fun listenSessionEpoch(): StateFlow<Long>

    /**
     * `true` from the app leaving the foreground until the session check of its return has run;
     * `false` while the app is in the foreground with its session checked.
     */
    val returnCheckPending: StateFlow<Boolean>

    /**
     * Returns at once while the app is in the foreground with its session checked; after the app
     * left the foreground, suspends until the session check of its return has run.
     */
    suspend fun awaitReturnChecked()

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
     * matched operation. The place remembers the last operation that reached it for the
     * session and a pull resolves as if that operation were still in effect, so content shown
     * by an operation survives the block's next appearance and a config update; nothing is
     * re-emitted, the memory only steers this selection. Suspends until the config arrives.
     */
    suspend fun selectInAppForPlace(
        placeSystemName: PlaceKey,
        triggerEvent: InAppEventType,
    ): EmbeddedResolveOutcome

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
     * passes), match targeting (geo and segmentation are fetched from the network) and
     * are not embedded. `directCall` and the show limits are deliberately not
     * checked: a drawn element must open.
     *
     * The answer mirrors the request, duplicates included. `Inapp.Targeting` goes out at the
     * moment the SDK computes the answer (delivery does not matter), over the **full** list —
     * a requested id in the cut A/B branch keeps its funnel denominator — and once per session per
     * `host in-app + requested id` pair: a repeated request reports only the new ones. A tap
     * reports nothing here — the in-app is not shown yet.
     *
     * Between a session reset and the new session's config the answer comes from the config held
     * before the reset; an embedded block's own selection waits for the new one.
     *
     * `null` when the SDK has no config to answer from: none arrived within the embedded block's
     * config wait (30 s), or the config fetch failed with nothing cached. The page must not read
     * that as an empty answer, so the bridge refuses the request instead.
     */
    suspend fun filterShowableInAppIds(hostInAppId: String, inAppIds: List<String>): List<String>?

    suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>>

    fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    )

    /**
     * The embedded block drew its content confirmed in [sessionEpoch]. Unless that session is live
     * ([isLiveSession]) nothing is written, committed or sent. In a live session it is compared
     * against the place's "last shown" slot, in the same lock hold as the check: a changed in-app
     * ships the `Inapp.Show` half of the pair and — for a frequency that counts shows at all —
     * writes the history and moves the shared cooldown, exactly like an overlay show; the same
     * in-app repeated within the session (a rotation, a recreated page) stays silent, in counters
     * too. The slot is session state, so the first show of a new session is a new show.
     * Everything comes from the snapshot the content carries — the config may have moved on since
     * the resolve. [tags] arrive already gated by the caller.
     *
     * @return whether `Inapp.Show` was sent.
     */
    fun recordBlockShow(
        placeSystemName: PlaceKey,
        inAppId: String,
        frequency: Frequency,
        timeToDisplay: Milliseconds,
        tags: Map<String, String>?,
        sessionEpoch: Long,
    ): Boolean

    /**
     * The winner's `delayTime` elapsed on this place: a later resolve this session hands it out with no delay.
     * Marks nothing and returns `false` unless [sessionEpoch] is live ([isLiveSession]).
     */
    fun markEmbeddedDelayWaitedOut(placeSystemName: PlaceKey, inAppId: String, sessionEpoch: Long): Boolean

    fun reservePlaceShow(placeSystemName: PlaceKey, content: InAppType.Embedded, sessionEpoch: Long): PlaceShowReservation

    fun releasePlaceShow(placeSystemName: PlaceKey)

    fun releasePlaceShow(placeSystemName: PlaceKey, sessionEpoch: Long): Boolean

    fun reserveOverlayShow(inApp: InApp): ShowReservationOutcome

    fun releaseOverlayShow(inAppId: String)

    fun sendInAppClicked(inAppId: String, tags: Map<String, String>?)

    suspend fun fetchMobileConfig()

    fun beginNewSession()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveInAppDismissTime(inApp: InApp)
}

internal enum class PlaceShowReservation { RESERVED, REFUSED, STALE }

internal data class InAppToShow(
    val inApp: InApp,
    val variant: InAppType,
)

/** What the pass of a place ended with — the registry's input for the delivery to the blocks. */
internal sealed class EmbeddedResolveOutcome {

    /**
     * A resolved place: the content to render and the winner's show delay — the campaign's choice,
     * applied by the registry before the delivery.
     */
    data class Content(val variant: InAppType.Embedded, val delayTime: Milliseconds?) : EmbeddedResolveOutcome()

    /** Nothing to show: no campaign for the place, or every candidate was left out. */
    data object Empty : EmbeddedResolveOutcome()

    /**
     * The SDK could not decide: the config fetch failed and nothing is cached, so the place has no
     * answer rather than nothing to show.
     */
    data object ConfigUnavailable : EmbeddedResolveOutcome()
}
