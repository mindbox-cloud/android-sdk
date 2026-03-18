package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticConstants
import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticPatternEvent
import cloud.mindbox.mobile_sdk.inapp.presentation.view.HapticRequest
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

internal class HapticRequestValidator : Validator<HapticRequest> {

    override fun isValid(item: HapticRequest): Boolean = when (item) {
        is HapticRequest.Selection -> true
        is HapticRequest.Impact -> true
        is HapticRequest.Pattern -> isValidPattern(item.events)
    }

    private fun isValidPattern(events: List<HapticPatternEvent>): Boolean {
        if (events.isEmpty()) return logAndFail("pattern is empty")
        if (events.size > MAX_EVENTS) return logAndFail("too many events: ${events.size}")
        if (!events.all { isValidEvent(it) }) return false
        return isValidPatternOrder(events.sortedBy { it.time })
    }

    private fun isValidEvent(event: HapticPatternEvent): Boolean {
        if (event.time !in 0L..MAX_TOTAL_DURATION_MS) {
            return logAndFail("event time out of range: ${event.time}")
        }
        if (event.duration !in 0L..MAX_SINGLE_EVENT_DURATION_MS) {
            return logAndFail("event duration out of range: ${event.duration}")
        }
        if (event.intensity !in 0f..1f) {
            return logAndFail("event intensity out of range: ${event.intensity}")
        }
        if (event.sharpness !in 0f..1f) {
            return logAndFail("event sharpness out of range: ${event.sharpness}")
        }
        val effectiveDuration: Long = effectiveDurationOf(event)
        if (event.time + effectiveDuration > MAX_TOTAL_DURATION_MS) {
            return logAndFail("event time + effectiveDuration exceeds max: ${event.time + effectiveDuration}")
        }
        return true
    }

    private fun isValidPatternOrder(sortedEvents: List<HapticPatternEvent>): Boolean {
        for (i in 1 until sortedEvents.size) {
            val previous: HapticPatternEvent = sortedEvents[i - 1]
            val next: HapticPatternEvent = sortedEvents[i]
            val previousEnd: Long = previous.time + effectiveDurationOf(previous)
            if (next.time < previousEnd) {
                return logAndFail("event at time=${next.time} overlaps previous event ending at $previousEnd")
            }
        }
        return true
    }

    private fun effectiveDurationOf(event: HapticPatternEvent): Long =
        if (event.duration > 0) event.duration else HapticConstants.TRANSIENT_DURATION_MS

    private fun logAndFail(reason: String): Boolean {
        mindboxLogW("[Haptic] invalid pattern: $reason")
        return false
    }

    private companion object {
        const val MAX_EVENTS = 128
        const val MAX_TOTAL_DURATION_MS = 30_000L
        const val MAX_SINGLE_EVENT_DURATION_MS = 5_000L
    }
}
