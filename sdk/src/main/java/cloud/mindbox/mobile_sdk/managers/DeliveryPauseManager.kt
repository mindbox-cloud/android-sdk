package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds

internal class DeliveryPauseManager(
    private val monotonicNow: () -> Milliseconds,
) {

    private companion object {
        val FIRST_PAUSE = Milliseconds(180_000L)
        val MAX_PAUSE = Milliseconds(3_600_000L)
    }

    private class OperationState(
        val pausedUntilTick: Milliseconds,
        val nextPause: Milliseconds,
    )

    private val states = mutableMapOf<String, OperationState>()

    @Synchronized
    fun remainingPause(eventType: EventType): Milliseconds? = remainingPause(eventType.deliveryKey)

    @Synchronized
    fun wasThrottled(eventType: EventType): Boolean = states.containsKey(eventType.deliveryKey)

    @Synchronized
    fun register(eventType: EventType, result: AsyncEventSendResult): Milliseconds? {
        val key = eventType.deliveryKey
        if (remainingPause(key) != null) return null
        return when (result) {
            AsyncEventSendResult.DELIVERED, AsyncEventSendResult.DISCARDED -> {
                states.remove(key)
                null
            }

            AsyncEventSendResult.THROTTLED -> {
                val pause = states[key]?.nextPause ?: FIRST_PAUSE
                states[key] = OperationState(
                    pausedUntilTick = Milliseconds(monotonicNow().interval + pause.interval),
                    nextPause = Milliseconds(minOf(pause.interval * 2, MAX_PAUSE.interval)),
                )
                pause
            }

            AsyncEventSendResult.RETRY_LATER -> null
        }
    }

    @Synchronized
    fun reset() {
        states.clear()
    }

    private fun remainingPause(key: String): Milliseconds? {
        val pausedUntilTick = states[key]?.pausedUntilTick ?: return null
        val remaining = Milliseconds(pausedUntilTick.interval - monotonicNow().interval)
        return remaining.takeIf { it.interval > 0 }
    }
}

internal val EventType.deliveryKey: String
    get() = "$endpoint $operation"
