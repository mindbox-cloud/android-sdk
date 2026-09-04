package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class MaxInappsPerSessionLimitChecker(
    private val sessionStorageManager: SessionStorageManager
) : Checker {

    override fun check(reservations: Collection<ShowReservation>): Boolean {
        mindboxLogI("Checking max inapps show per session limit")
        return when (val maxInappsPerSessionCount = sessionStorageManager.inAppShowLimitsSettings.maxInappsPerSession) {
            null -> {
                mindboxLogI("Parameter limit inapp for show per session not specify. Work without limits for show per session")
                true
            }

            else -> {
                val shownInSession = sessionStorageManager.inAppMessageShownInSession.size
                val isAllowed = maxInappsPerSessionCount > shownInSession + reservations.size
                mindboxLogI("Inapp shown in session count: $shownInSession, reserved: ${reservations.size}, limit: $maxInappsPerSessionCount, Show allowed: $isAllowed")
                isAllowed
            }
        }
    }
}
