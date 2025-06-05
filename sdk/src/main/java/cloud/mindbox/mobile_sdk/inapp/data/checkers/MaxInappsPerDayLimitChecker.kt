package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.getDayBounds

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
                val (startOfDay, endOfDay) = getDayBounds(Timestamp(timeProvider.currentTimeMillis()))
                val shownInAppsToday = inAppRepository.getShownInApps()
                    .values
                    .flatten()
                    .count { timestamp ->
                        timestamp in startOfDay.value until endOfDay.value
                    }
                val isAllowed = maxInappsPerSessionCount > shownInAppsToday
                mindboxLogI("Shows today: $shownInAppsToday, limit per day: $maxInappsPerSessionCount isAllowed = $isAllowed")
                isAllowed
            }
        }
    }
}
