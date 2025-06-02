package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class MinIntervalBetweenShowsLimitChecker : Checker {
    override fun check(): Boolean {
        mindboxLogI("Checking min interval between shows limit")
        return true // TODO: Implement logic
    }
}
