package cloud.mindbox.mobile_sdk.inapp.data.validators

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
        return events.all { isValidEvent(it) }
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
        if (event.time + event.duration > MAX_TOTAL_DURATION_MS) {
            return logAndFail("event time + duration exceeds max: ${event.time + event.duration}")
        }
        return true
    }

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
