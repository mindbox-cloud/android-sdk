package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class UserVisitManagerImpl : UserVisitManager {
    override fun saveUserVisit() {
        var userVisitCount = MindboxPreferences.userVisitCount
        when {
            userVisitCount > 0 -> {
                MindboxPreferences.userVisitCount = userVisitCount + 1
            }
            !MindboxPreferences.isFirstInitialize -> {
                MindboxPreferences.userVisitCount = ++userVisitCount + 1
            }
            else -> {
                MindboxPreferences.userVisitCount = userVisitCount + 1
            }
        }
        mindboxLogI("Previous user visit count is $userVisitCount. New user visit count after increment is ${MindboxPreferences.userVisitCount}")
    }
}
