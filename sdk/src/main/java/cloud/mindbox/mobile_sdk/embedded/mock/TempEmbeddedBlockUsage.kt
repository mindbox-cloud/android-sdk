package cloud.mindbox.mobile_sdk.embedded.mock

import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import java.util.concurrent.ConcurrentHashMap

// MUST NOT REACH `develop`: every temporary and mock piece of the embedded block announces itself
// the first time it is used. One run of the app therefore lists what is still wired in, and a run
// with no such line left is the proof that the sweep after the real contract is complete.
//
// Grep anchor for that sweep: "Used mock! Need delete".
internal object TempEmbeddedBlockUsage {

    // Once per site per process. These sit on paths that run per config fetch, per attach and per
    // page message — a line on every call would bury the very log it is meant to draw attention to.
    private val reported = ConcurrentHashMap<String, Unit>()

    // Some sites report from a class initializer, where a throwing logger would turn into an
    // ExceptionInInitializerError and take the feature down. A marker must never be able to do
    // that: it is bookkeeping, not behavior.
    fun report(site: String) {
        if (reported.putIfAbsent(site, Unit) == null) {
            runCatching { mindboxLogE("Used mock! Need delete — $site") }
        }
    }
}
