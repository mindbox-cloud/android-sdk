package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.getDayStartTimestamp
import java.util.concurrent.TimeUnit

internal class MaxInappsPerDayLimitChecker(
    private val inAppRepository: InAppRepository,
    private val sessionStorageManager: SessionStorageManager,
    private val timeProvider: TimeProvider
) : Checker {
    override fun check(): Boolean {
        mindboxLogI("Checking max inapps per day limit")
        return when (val maxInappsPerSessionCount = sessionStorageManager.inAppShowLimitsSettings.maxInappsPerDay) {
            null -> {
                mindboxLogI("Parameter limit inapp for show per day not specify. Work without limits for show per day")
                true
            }

            else -> {
                val startOfDay = getDayStartTimestamp(timeProvider.currentTimestamp())
                val shownInAppsToday = inAppRepository.getShownInApps()
                    .values
                    .flatten()
                    .count { timestamp ->
                        timestamp in startOfDay.ms until startOfDay.ms + TimeUnit.DAYS.toMillis(1)
                    }
                val isAllowed = maxInappsPerSessionCount > shownInAppsToday
                mindboxLogI("Shows today: $shownInAppsToday, limit per day: $maxInappsPerSessionCount isAllowed = $isAllowed")
                isAllowed
            }
        }
    }
}
