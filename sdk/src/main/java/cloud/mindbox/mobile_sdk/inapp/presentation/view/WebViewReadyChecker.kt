package cloud.mindbox.mobile_sdk.inapp.presentation.view

/**
 * Polls the page for the JS bridge instead of deciding on a single onPageFinished-time probe.
 *
 * Module scripts can finish evaluating a beat after the page's load event on slow devices or
 * with a cold cache, so one early `false` must not close a healthy in-app. The check re-runs
 * on a short cadence and gives up only after the full budget; a new navigation (or teardown)
 * cancels the previous checker outright. Mirrors the iOS SDK's WebViewReadyChecker.
 *
 * Pure logic: evaluation and scheduling are injected, so the class is JVM-testable.
 */
internal class WebViewReadyChecker(
    private val evaluate: (script: String, resultCallback: (String?) -> Unit) -> Unit,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> Unit,
) {

    companion object {
        const val MAX_ATTEMPTS: Int = 8
        const val RETRY_DELAY_MS: Long = 150L
    }

    @Volatile
    private var isCancelled = false

    fun run(
        script: String,
        expectedResult: String,
        onReady: () -> Unit,
        onGiveUp: (lastFailure: String) -> Unit
    ) {
        attempt(1, script, expectedResult, onReady, onGiveUp)
    }

    /**
     * Abandons the poll without calling either completion — the caller's new navigation
     * (or teardown) owns readiness from here.
     */
    fun cancel() {
        isCancelled = true
    }

    private fun attempt(
        number: Int,
        script: String,
        expectedResult: String,
        onReady: () -> Unit,
        onGiveUp: (String) -> Unit
    ) {
        if (isCancelled) return
        evaluate(script) { result ->
            if (isCancelled) return@evaluate
            if (result == expectedResult) {
                onReady()
                return@evaluate
            }
            if (number >= MAX_ATTEMPTS) {
                onGiveUp("evaluateJavaScript returned unexpected response: $result")
                return@evaluate
            }
            schedule(RETRY_DELAY_MS) {
                attempt(number + 1, script, expectedResult, onReady, onGiveUp)
            }
        }
    }
}
