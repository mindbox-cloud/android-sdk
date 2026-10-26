package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation

internal interface Checker {
    fun check(reservations: Collection<ShowReservation>): Boolean
}
