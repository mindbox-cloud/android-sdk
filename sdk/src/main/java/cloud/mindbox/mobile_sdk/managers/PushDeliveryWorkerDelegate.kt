package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.PushDeliverySchedule
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.services.PushDeliveryWorkManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal class PushDeliveryWorkerDelegate {

    private companion object {
        const val MAX_PER_RUN = 20
        val MAX_WAIT = Milliseconds(30_000L)
        val QUEUE_DELAY = Milliseconds(10_000L)
    }

    private data class Attempt(
        val delivery: PushDelivery,
        val startedAt: Timestamp,
        val result: AsyncEventSendResult,
    )

    private val timeProvider by lazy { MindboxDI.appModule.timeProvider }
    private val pushDeliveredManager by lazy { MindboxDI.appModule.pushDeliveredManager }
    private val pauseManager by lazy { MindboxDI.appModule.pushDeliveryPauseManager }

    suspend fun send(context: Context, deliveryId: Long) {
        val configuration = prepare(context)
        val delivery = DbManager.getPushDelivery(deliveryId)
        when {
            delivery == null -> mindboxLogI("Push delivery #$deliveryId is not in the queue")
            delivery.isInQueue -> {
                mindboxLogI("Push delivery #$deliveryId is already handed to the queue")
                PushDeliveryWorkManager.enqueueQueue(context)
            }
            delivery.isTooOld(now()) -> dropTooOld(delivery)
            configuration == null ->
                mindboxLogW("Push delivery #$deliveryId will be sent later, because configuration was not initialized")
            else -> sendAttempts(configuration, delivery)?.let { last -> handToQueue(context, last) }
        }
    }

    suspend fun sendQueue(context: Context, throttles: Int): ListenableWorker.Result {
        val configuration = prepare(context) ?: run {
            mindboxLogW("Push deliveries will be sent later, because configuration was not initialized")
            return ListenableWorker.Result.success()
        }
        val results = DbManager.getQueuedPushDeliveries()
            .take(MAX_PER_RUN)
            .map { delivery ->
                sendQueued(configuration, delivery).takeIf { it != AsyncEventSendResult.THROTTLED }
                    ?: return pauseQueue(context, throttles + 1)
            }
        return when {
            results.any { !it.isRemovedFromQueue } -> {
                mindboxLogI("[GD] push deliveries decision: not all were accepted, the queue retries with the backoff")
                ListenableWorker.Result.retry()
            }
            DbManager.getQueuedPushDeliveries().isNotEmpty() -> {
                PushDeliveryWorkManager.enqueueNextQueue(context)
                ListenableWorker.Result.success()
            }
            else -> ListenableWorker.Result.success()
        }
    }

    private suspend fun sendQueued(configuration: Configuration, delivery: PushDelivery): AsyncEventSendResult {
        if (delivery.isTooOld(now())) {
            return if (dropTooOld(delivery)) AsyncEventSendResult.DISCARDED else AsyncEventSendResult.RETRY_LATER
        }
        val result = pushDeliveredManager.send(configuration, delivery)
        val isKept = result.isRemovedFromQueue && !DbManager.removePushDelivery(delivery.id)
        return if (isKept) AsyncEventSendResult.RETRY_LATER else result
    }

    private suspend fun prepare(context: Context): Configuration? {
        Mindbox.initComponents(context)
        InitializeLock.await(InitializeLock.State.MIGRATION)
        return DbManager.getConfigurations()
    }

    private fun now(): Timestamp = timeProvider.currentTimestamp()

    private fun dropTooOld(delivery: PushDelivery): Boolean {
        mindboxLogI("Push delivery #${delivery.id} is too old, dropping it")
        return DbManager.removePushDelivery(delivery.id)
    }

    private fun handToQueue(context: Context, last: Attempt) {
        val id = last.delivery.id
        if (DbManager.updatePushDelivery(last.delivery.copy(isInQueue = true)) != 1) {
            mindboxLogI("Push delivery #$id is no longer stored, it is not handed to the queue")
            return
        }
        if (last.result == AsyncEventSendResult.THROTTLED) {
            val pause = pauseManager.nextPause(throttles = 1)
            mindboxLogI("[GD] push delivery #$id decision: handed to the queue after HTTP 429, the queue pauses for ${pause.interval} ms")
            PushDeliveryWorkManager.enqueueQueue(context, pause, throttles = 1)
        } else {
            mindboxLogI("[GD] push delivery #$id decision: handed to the queue")
            PushDeliveryWorkManager.enqueueQueue(context, QUEUE_DELAY)
        }
    }

    private fun pauseQueue(context: Context, throttles: Int): ListenableWorker.Result {
        val pause = pauseManager.nextPause(throttles)
        mindboxLogI("[GD] push deliveries decision: HTTP 429, the queue pauses for ${pause.interval} ms")
        PushDeliveryWorkManager.enqueueNextQueue(context, pause, throttles)
        return ListenableWorker.Result.success()
    }

    private suspend fun sendAttempts(configuration: Configuration, delivery: PushDelivery): Attempt? {
        val sdkInt = Build.VERSION.SDK_INT
        val schedule = PushDeliverySchedule.of(delivery.provider, sdkInt)
        mindboxLogI(
            "[GD] push delivery #${delivery.id} schedule: $schedule ${schedule.offsets.map { it.interval }}, " +
                "provider: ${delivery.provider}, Android: $sdkInt"
        )
        val startedAt = now()
        val received = minOf(startedAt.ms, delivery.receivedAt).toTimestamp()

        return attemptOffsets(schedule, delivery, startedAt).fold(null as Attempt?) { previous, offset ->
            val earliestStart = previous?.startedAt?.plus(schedule.interval.interval)
            waitUntil(maxOf((received + offset.interval).ms, earliestStart?.ms ?: 0L).toTimestamp())

            val current = previous?.delivery ?: delivery
            val attemptStart = now()
            val result = pushDeliveredManager.send(configuration, current)
            if (result.isRemovedFromQueue) {
                DbManager.removePushDelivery(delivery.id)
                return null
            }
            val attempted = current.copy(attempts = current.attempts + 1)
            if (DbManager.updatePushDelivery(attempted) != 1) {
                mindboxLogI("Push delivery #${delivery.id} is no longer in the queue, its attempt is not stored")
                return null
            }
            Attempt(delivery = attempted, startedAt = attemptStart, result = result)
        }
    }

    private suspend fun waitUntil(start: Timestamp) {
        delay((start.ms - now().ms).coerceAtMost(MAX_WAIT.interval).milliseconds)
    }

    private fun attemptOffsets(schedule: PushDeliverySchedule, delivery: PushDelivery, now: Timestamp): List<Milliseconds> {
        val elapsed = delivery.elapsedAt(now)
        val windowEnd = Milliseconds(schedule.offsets.last().interval + schedule.interval.interval)
        val remaining = schedule.offsets.drop(delivery.attempts.coerceAtLeast(0))
        return if (remaining.isEmpty() || elapsed.interval > windowEnd.interval) listOf(elapsed) else remaining
    }
}
