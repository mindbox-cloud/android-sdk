package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class RequestPermissionManagerImpl : RequestPermissionManager {
    override fun increaseRequestCounter() {
        MindboxPreferences.requestPermissionCount += 1
    }

    override fun decreaseRequestCounter() {
        val currentRequestCount = MindboxPreferences.requestPermissionCount
        if (currentRequestCount > 0) {
            MindboxPreferences.requestPermissionCount -= 1
        }
    }

    override fun getRequestCount(): Int = MindboxPreferences.requestPermissionCount
}
