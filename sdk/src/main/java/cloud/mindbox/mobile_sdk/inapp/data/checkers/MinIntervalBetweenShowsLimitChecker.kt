package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.TimeProvider

internal class MinIntervalBetweenShowsLimitChecker(
    private val sessionStorageManager: SessionStorageManager,
    private val inAppRepository: InAppRepository,
    private val timeProvider: TimeProvider
) : Checker {
    override fun check(): Boolean {
        mindboxLogI("Checking min interval between shows limit")
        return when (val minIntervalBetweenShowDuration = sessionStorageManager.inAppShowLimitsSettings.minIntervalBetweenShows) {
            null -> {
                mindboxLogI("Parameter min interval between inapp show not specify. Work without limit")
                true
            }

            else -> {
                val lastDismissInappTime = inAppRepository.getLastInappDismissTime().ms
                val currentTime = timeProvider.currentTimeMillis()
                val timeDiff = currentTime - lastDismissInappTime
                val isAllowed = minIntervalBetweenShowDuration.interval + lastDismissInappTime < currentTime
                mindboxLogI("Min interval between inapp show: $minIntervalBetweenShowDuration, last inapp dismiss time: $lastDismissInappTime, current time: $currentTime, time since last dismiss: ${timeDiff}ms. Show allowed: $isAllowed")
                isAllowed
            }
        }
    }
}
