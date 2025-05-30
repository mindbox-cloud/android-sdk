package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.InAppShowLimitChecker
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class AllAllowInAppShowLimitChecker(
    private val checkers: List<InAppShowLimitChecker>
) : InAppShowLimitChecker {
    override fun check(): Boolean = loggingRunCatching(defaultValue = true) {
        mindboxLogI("Checking all in-app show limits")
        checkers.all { it.check() }
    }
}
