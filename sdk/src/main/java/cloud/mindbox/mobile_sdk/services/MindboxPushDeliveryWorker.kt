package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.managers.PushDeliveryWorkerDelegate
import kotlinx.coroutines.CancellationException

internal class MindboxPushDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val KEY_DELIVERY_ID = "deliveryId"
        private const val KEY_THROTTLES = "throttles"
        private const val QUEUE = -1L

        fun inputData(deliveryId: Long): Data = workDataOf(KEY_DELIVERY_ID to deliveryId)

        fun queueInputData(throttles: Int): Data = workDataOf(KEY_THROTTLES to throttles)
    }

    override suspend fun doWork(): Result {
        val deliveryId = inputData.getLong(KEY_DELIVERY_ID, QUEUE)
        return try {
            if (deliveryId == QUEUE) {
                PushDeliveryWorkerDelegate().sendQueue(applicationContext, inputData.getInt(KEY_THROTTLES, 0))
            } else {
                PushDeliveryWorkerDelegate().send(applicationContext, deliveryId)
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mindboxLogE("Push delivery work failed", e)
            Result.failure()
        }
    }
}
