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
                val lastShowInappTime = inAppRepository.getLastInappShowTime().ms
                val currentTime = timeProvider.currentTimeMillis()
                val isAllowed = minIntervalBetweenShowDuration.interval + lastShowInappTime < currentTime
                mindboxLogI("Min interval between inapp show: $minIntervalBetweenShowDuration, last inapp show time: $lastShowInappTime, current time $currentTime. Show allowed: $isAllowed")
                isAllowed
            }
        }
    }
}
