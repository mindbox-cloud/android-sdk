package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Timestamp

internal interface ShowBudgetManager {

    fun isWithinBudgets(frequency: Frequency, isPriority: Boolean, owner: String? = null): Boolean

    fun reserve(
        owner: String,
        inAppId: String,
        frequency: Frequency,
        isPriority: Boolean
    ): ShowReservationOutcome

    fun commit(
        owner: String,
        inAppId: String,
        frequency: Frequency,
        shownAt: Timestamp
    )

    fun release(owner: String)

    fun recordCooldown(frequency: Frequency, at: Timestamp)
}

internal enum class ShowReservationOutcome {
    GRANTED,

    ALREADY_HELD,

    NOT_NEEDED,

    REFUSED,
}

internal object ShowBudgetOwner {
    fun place(placeSystemName: String): String = "place|$placeSystemName"

    fun overlay(inAppId: String): String = "overlay|$inAppId"
}
