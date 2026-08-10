package cloud.mindbox.mobile_sdk.embedded.mock

/**
 * Debug switch for the temporary mock feed page. Lets the test app drive the mock through the
 * scenarios a real page can end up in. Goes away together with the mock page once the config
 * hands out a real feed URL.
 *
 * The scenario is baked into the page HTML at build time, and every block builds its own page —
 * blocks created after the switch use the new scenario, blocks already on screen keep theirs
 * until their content reloads (re-creation or a new session).
 *
 * Not annotated with `InternalMindboxApi`: the marker lives in mindbox-common, which host apps
 * do not see, and this object exists precisely for the test app.
 */
public object TempMindboxStoriesFeedMock {

    public enum class Scenario {

        /** The feed renders and reports its height — the happy path. */
        SUCCESS,

        /** Targeting matched nothing: the page reports zero height — the empty state (hidden by default). */
        EMPTY,

        /** The page never answers: the container times out into the error state. */
        ERROR,

        /** The page answers, but later than the container's timeout — same as ERROR for the host. */
        SLOW,
    }

    public var scenario: Scenario = Scenario.SUCCESS
}
