package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation

internal fun allAllow(reservations: Collection<ShowReservation>, vararg checkers: Checker): Boolean =
    loggingRunCatching(defaultValue = true) {
        checkers.all { it.check(reservations) }
    }
