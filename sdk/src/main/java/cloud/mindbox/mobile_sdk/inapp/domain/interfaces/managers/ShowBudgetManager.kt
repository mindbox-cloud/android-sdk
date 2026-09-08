package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Timestamp

internal interface ShowBudgetManager {

    fun reserve(
        owner: ShowBudgetOwner,
        inAppId: String,
        frequency: Frequency,
        isPriority: Boolean
    ): ShowReservationOutcome

    fun commit(
        owner: ShowBudgetOwner,
        inAppId: String,
        frequency: Frequency,
        shownAt: Timestamp
    )

    fun release(owner: ShowBudgetOwner)

    fun recordCooldown(frequency: Frequency, at: Timestamp)
}

internal enum class ShowReservationOutcome {
    GRANTED,

    ALREADY_HELD,

    NOT_NEEDED,

    REFUSED,
}

internal sealed interface ShowBudgetOwner {

    data class Place(val placeSystemName: String) : ShowBudgetOwner

    data class Overlay(val inAppId: String) : ShowBudgetOwner
}
