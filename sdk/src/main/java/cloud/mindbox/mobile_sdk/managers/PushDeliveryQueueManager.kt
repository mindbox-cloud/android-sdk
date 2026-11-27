package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.PushDelivery

internal class PushDeliveryQueueManager {

    private var isRunEnding = false

    @Synchronized
    fun startRun() {
        isRunEnding = false
    }

    @Synchronized
    fun hasQueuedAtRunEnd(): Boolean =
        DbManager.getQueuedPushDeliveries().isNotEmpty().also { isRunEnding = !it }

    @Synchronized
    fun handOver(delivery: PushDelivery, enqueueQueue: (isRunEnding: Boolean) -> Unit): Boolean {
        if (DbManager.updatePushDelivery(delivery.copy(isInQueue = true)) != 1) return false
        enqueueQueue(isRunEnding)
        isRunEnding = false
        return true
    }
}
