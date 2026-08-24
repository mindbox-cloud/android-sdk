package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
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
     * Resolves content for an embedded place through the common selection (A/B pool, place,
     * `directCall`, frequency, priority, targeting) plus the show limits — the display style
     * does not change "when to show". Only the `isInAppActive` lock and the delayed queue stay
     * out: those are overlay machinery. The pull side passes
     * [InAppEventType.EmbeddedPlaceRequested] as [triggerEvent]; the push side passes the
     * matched operation. Suspends until the config arrives.
     */
    suspend fun selectInAppForPlace(
        placeSystemName: String,
        triggerEvent: InAppEventType,
    ): InAppType.Embedded?

    /**
     * The in-app with [inAppId] and its overlay variant for a direct call: no restriction —
     * frequency, limits, `displayConditions`, targeting, the A/B pool — is checked. A drawn
     * circle must open. Returns `null` only for an unknown id, an id filtered out by
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
     * The single place where stories are cut: the answer to the page's `filterShowableInapps`.
     * Keeps the ids whose in-apps exist in the version- and A/B-filtered list, pass the
     * frequency rule (an exhausted non-unlimited story loses its circle, `unlimited` always
     * passes — decision 17.08), match targeting (no network fetches — the page waits
     * 3 seconds) and are not embedded. `directCall` and the show limits are deliberately not
     * checked: a drawn circle must open.
     *
     * Every id kept sends `Inapp.Targeting` with every answer, deliberately without a dedup —
     * the story funnel counts the proposed circles, not the opened ones (decision 16.08). A tap
     * reports nothing here — the story is not drawn yet.
     */
    suspend fun filterShowableInAppIds(inAppIds: List<String>): List<String>

    suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>>

    fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    )

    /**
     * The embedded block drew its content: sends `Inapp.Show` unconditionally and records the
     * show counters — the session list and the show history — only when the frequency counts
     * shows at all (`unlimited` has no counter to keep). The shared cooldown between overlay
     * shows is left alone: the block interrupts nothing. The operation ships **once per session
     * per in-app**, so a view the host recreated (a rotation, a return to the screen) reports no
     * second show. [timeToDisplay] is everything the user waited through: the resolve, the page
     * load and its own pipeline.
     */
    suspend fun recordBlockShow(
        inAppId: String,
        timeToDisplay: Milliseconds,
        tags: Map<String, String>?,
    )

    fun sendInAppClicked(inAppId: String, tags: Map<String, String>?)

    suspend fun fetchMobileConfig()

    fun resetInAppConfigAndEvents()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveInAppDismissTime(inApp: InApp)

    fun areShowAndFrequencyLimitsAllowed(inApp: InApp): Boolean

    fun areShowLimitsAllowed(inApp: InApp): Boolean
}

internal data class InAppToShow(
    val inApp: InApp,
    val variant: InAppType,
)
