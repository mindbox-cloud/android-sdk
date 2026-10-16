package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.convertToIso8601String
import cloud.mindbox.mobile_sdk.utils.TimeProvider

internal class MinIntervalBetweenShowsLimitChecker(
    private val sessionStorageManager: SessionStorageManager,
    private val inAppRepository: InAppRepository,
    private val timeProvider: TimeProvider
) : Checker {

    override fun check(reservations: Collection<ShowReservation>): Boolean {
        return when (val minIntervalBetweenShowDuration = sessionStorageManager.state.inAppShowLimitsSettings.minIntervalBetweenShows) {
            null -> {
                mindboxLogI("[ShowBudget] [minInterval] minIntervalBetweenShows not set, invalid or not positive, skipping the interval")
                true
            }

            else -> {
                // Moved by counted shows and closes only: an in-app with unlimited frequency (a story) never moves it.
                val lastDismissInappTime: Timestamp = inAppRepository.getLastInappDismissTime()
                val lastReservationTime: Timestamp = reservations.maxOfOrNull { reservation -> reservation.reservedAt.ms }
                    ?.let(::Timestamp) ?: Timestamp(0L)
                val since: Timestamp = if (lastReservationTime.ms > lastDismissInappTime.ms) lastReservationTime else lastDismissInappTime
                val currentTime: Timestamp = timeProvider.currentTimestamp()
                val isAllowed = minIntervalBetweenShowDuration.interval + since.ms < currentTime.ms
                if (since == Timestamp.ZERO) {
                    mindboxLogI("[ShowBudget] [minInterval] no counted shows or reservations yet, allowing")
                } else {
                    val nextAllowed = since + minIntervalBetweenShowDuration.interval
                    mindboxLogI(
                        "[ShowBudget] [minInterval] last: ${since.convertToIso8601String()}, " +
                            "next allowed: ${nextAllowed.convertToIso8601String()}, allowed: $isAllowed"
                    )
                }
                isAllowed
            }
        }
    }
}
