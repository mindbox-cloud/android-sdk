package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider

internal class MinIntervalBetweenShowsLimitChecker(
    private val sessionStorageManager: SessionStorageManager,
    private val inAppRepository: InAppRepository,
    private val timeProvider: TimeProvider
) : Checker {

    override fun check(reservations: Collection<ShowReservation>): Boolean {
        mindboxLogI("Checking min interval between shows limit")
        return when (val minIntervalBetweenShowDuration = sessionStorageManager.inAppShowLimitsSettings.minIntervalBetweenShows) {
            null -> {
                mindboxLogI("Parameter min interval between inapp show not specify. Work without limit")
                true
            }

            else -> {
                val lastDismissInappTime: Timestamp = inAppRepository.getLastInappDismissTime()
                val lastReservationTime: Timestamp = reservations.maxOfOrNull { reservation -> reservation.reservedAt.ms }
                    ?.let(::Timestamp) ?: Timestamp(0L)
                val since: Timestamp = if (lastReservationTime.ms > lastDismissInappTime.ms) lastReservationTime else lastDismissInappTime
                val currentTime: Timestamp = timeProvider.currentTimestamp()
                val timeDiff = Milliseconds(currentTime.ms - since.ms)
                val isAllowed = minIntervalBetweenShowDuration.interval + since.ms < currentTime.ms
                mindboxLogI("Min interval between inapp show: $minIntervalBetweenShowDuration, last inapp dismiss time: $lastDismissInappTime, last reservation time: $lastReservationTime, current time: $currentTime, time since: ${timeDiff.interval}ms. Show allowed: $isAllowed")
                isAllowed
            }
        }
    }
}
