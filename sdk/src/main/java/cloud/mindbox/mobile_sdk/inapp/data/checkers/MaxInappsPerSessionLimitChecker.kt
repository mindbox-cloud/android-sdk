package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.InAppShowLimitChecker
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class MaxInappsPerSessionLimitChecker(
    private val sessionStorageManager: SessionStorageManager
) : InAppShowLimitChecker {
    override fun check(): Boolean {
        mindboxLogI("Checking max inapps show per session limit")
        return sessionStorageManager.inAppShowLimitsSettings.maxInappsPerSession?.let { maxInappsPerSessionCount ->
            val isAllowed = maxInappsPerSessionCount > sessionStorageManager.inAppMessageShownInSession.size
            mindboxLogI("Inapp shown in session count: ${sessionStorageManager.inAppMessageShownInSession.size}, limit: $maxInappsPerSessionCount, Show allowed: $isAllowed")
            isAllowed
        } ?: true.also {
            mindboxLogI("Parameter limit inapp for show per session not specify. Work without limits for show per session")
        }
    }
}
