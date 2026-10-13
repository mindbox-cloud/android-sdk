package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class MaxInappsPerSessionLimitChecker(
    private val sessionStorageManager: SessionStorageManager
) : Checker {

    override fun check(reservations: Collection<ShowReservation>): Boolean {
        return when (val maxInappsPerSessionCount = sessionStorageManager.state.inAppShowLimitsSettings.maxInappsPerSession) {
            null -> {
                mindboxLogI("[ShowBudget] [Session] No limit specified")
                true
            }

            else -> {
                val shownInSession = sessionStorageManager.state.inAppMessageShownInSession.size
                val isAllowed = maxInappsPerSessionCount > shownInSession + reservations.size
                mindboxLogI(
                    "[ShowBudget] [Session] counted: $shownInSession, reserved: ${reservations.size}, limit: $maxInappsPerSessionCount, allowed: $isAllowed"
                )
                isAllowed
            }
        }
    }
}
