package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class MaxInappsPerDayLimitChecker : Checker {
    override fun check(): Boolean {
        mindboxLogI("Checking max inapps per day limit")
        return true // TODO: Implement logic
    }
}
