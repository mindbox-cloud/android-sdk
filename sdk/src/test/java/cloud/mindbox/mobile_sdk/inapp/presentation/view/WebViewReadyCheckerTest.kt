package cloud.mindbox.mobile_sdk.inapp.presentation.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WebViewReadyCheckerTest {

    /**
     * Drives the checker synchronously: `evaluate` answers from a scripted sequence
     * (empty -> "false") and scheduled retries run immediately unless held for the
     * cancellation test.
     */
    private class Harness(
        private val answers: MutableList<String?>,
        private val runScheduledImmediately: Boolean = true
    ) {
        var evaluateCount = 0
            private set
        val scheduledDelays = mutableListOf<Long>()
        val pendingWork = mutableListOf<() -> Unit>()

        fun makeChecker(): WebViewReadyChecker = WebViewReadyChecker(
            evaluate = { _, resultCallback ->
                evaluateCount++
                resultCallback(if (answers.isEmpty()) "false" else answers.removeAt(0))
            },
            schedule = { delayMillis, action ->
                scheduledDelays.add(delayMillis)
                if (runScheduledImmediately) action() else pendingWork.add(action)
            }
        )
    }

    @Test
    fun `an immediately ready page passes on the first attempt`() {
        val harness = Harness(mutableListOf("true"))
        var readyCalls = 0

        harness.makeChecker().run(
            script = "check",
            expectedResult = "true",
            onReady = { readyCalls++ },
            onGiveUp = { throw AssertionError("must not give up") }
        )

        assertEquals(1, readyCalls)
        assertEquals(1, harness.evaluateCount)
        assertTrue(harness.scheduledDelays.isEmpty())
    }

    @Test
    fun `a module evaluating after onPageFinished passes on a retry instead of closing the show`() {
        val harness = Harness(mutableListOf("false", null, "true"))
        var readyCalls = 0

        harness.makeChecker().run(
            script = "check",
            expectedResult = "true",
            onReady = { readyCalls++ },
            onGiveUp = { throw AssertionError("must not give up") }
        )

        assertEquals(1, readyCalls)
        assertEquals(3, harness.evaluateCount)
        assertEquals(
            listOf(WebViewReadyChecker.RETRY_DELAY_MS, WebViewReadyChecker.RETRY_DELAY_MS),
            harness.scheduledDelays
        )
    }

    @Test
    fun `a page that never boots gives up only after the full retry budget`() {
        val harness = Harness(mutableListOf())
        val giveUpReasons = mutableListOf<String>()

        harness.makeChecker().run(
            script = "check",
            expectedResult = "true",
            onReady = { throw AssertionError("must not become ready") },
            onGiveUp = { reason -> giveUpReasons.add(reason) }
        )

        assertEquals(1, giveUpReasons.size)
        assertEquals(WebViewReadyChecker.MAX_ATTEMPTS, harness.evaluateCount)
    }

    @Test
    fun `cancel abandons the poll without ever resolving`() {
        val harness = Harness(mutableListOf(), runScheduledImmediately = false)
        val checker = harness.makeChecker()
        checker.run(
            script = "check",
            expectedResult = "true",
            onReady = { throw AssertionError("must not become ready") },
            onGiveUp = { throw AssertionError("must not give up") }
        )
        assertEquals(1, harness.evaluateCount)

        checker.cancel()
        harness.pendingWork.forEach { work -> work() }

        // The cancelled checker neither evaluates again nor resolves.
        assertEquals(1, harness.evaluateCount)
    }
}
