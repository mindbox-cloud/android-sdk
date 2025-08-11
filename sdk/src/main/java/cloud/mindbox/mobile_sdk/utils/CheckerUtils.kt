package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker

internal fun allAllow(vararg checkers: Checker): Boolean = loggingRunCatching(defaultValue = true) {
    checkers.all { it.check() }
}
