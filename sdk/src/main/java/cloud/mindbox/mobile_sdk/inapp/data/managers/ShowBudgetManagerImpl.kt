package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.countsShows
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.allAllow

internal class ShowBudgetManagerImpl(
    private val sessionStorageManager: SessionStorageManager,
    private val inAppRepository: InAppRepository,
    private val timeProvider: TimeProvider,
    private val maxInappsPerSessionLimitChecker: Checker,
    private val maxInappsPerDayLimitChecker: Checker,
    private val minIntervalBetweenShowsLimitChecker: Checker,
) : ShowBudgetManager {

    private val lock: Any get() = sessionStorageManager.showBudgetLock

    override fun isWithinBudgets(frequency: Frequency, isPriority: Boolean, owner: String?): Boolean {
        if (bypassesBudgets(frequency, isPriority)) return true
        return synchronized(lock) {
            budgetsAllow(sessionStorageManager.showReservations.values.filter { held -> held.owner != owner })
        }
    }

    override fun reserve(
        owner: String,
        inAppId: String,
        frequency: Frequency,
        isPriority: Boolean
    ): ShowReservationOutcome {
        if (bypassesBudgets(frequency, isPriority)) {
            mindboxLogI("Show budgets do not apply to in-app $inAppId, no reservation needed")
            return ShowReservationOutcome.NOT_NEEDED
        }
        synchronized(lock) {
            val reservations = sessionStorageManager.showReservations
            val held = reservations[owner]
            if (held != null && held.inAppId == inAppId) {
                mindboxLogI("$owner already holds a show reservation for in-app $inAppId")
                return ShowReservationOutcome.ALREADY_HELD
            }
            if (held != null) {
                mindboxLogI("$owner drops its reservation for in-app ${held.inAppId}: a newer candidate replaces it")
                reservations.remove(owner)
            }
            if (!budgetsAllow(reservations.values)) {
                mindboxLogI("Show budgets are spent, in-app $inAppId gets no reservation for $owner")
                return ShowReservationOutcome.REFUSED
            }
            reservations[owner] = ShowReservation(owner, inAppId, timeProvider.currentTimestamp())
            mindboxLogI("$owner reserved a show for in-app $inAppId (${reservations.size} reservation(s) held)")
            return ShowReservationOutcome.GRANTED
        }
    }

    override fun commit(
        owner: String,
        inAppId: String,
        frequency: Frequency,
        shownAt: Timestamp
    ) {
        synchronized(lock) {
            sessionStorageManager.showReservations.remove(owner)
            if (!frequency.countsShows()) {
                mindboxLogI("In-app $inAppId has unlimited frequency, nothing to count")
                return
            }
            mindboxLogI("Counting a show of in-app $inAppId (frequency ${frequency.delay})")
            inAppRepository.setInAppShown(inAppId)
            inAppRepository.saveShownInApp(inAppId, shownAt.ms)
            inAppRepository.saveInAppStateChangeTime(shownAt)
        }
    }

    override fun release(owner: String) {
        synchronized(lock) {
            val released = sessionStorageManager.showReservations.remove(owner) ?: return
            mindboxLogI("$owner released its show reservation for in-app ${released.inAppId}")
        }
    }

    override fun recordCooldown(frequency: Frequency, at: Timestamp) {
        if (!frequency.countsShows()) return
        synchronized(lock) { inAppRepository.saveInAppStateChangeTime(at) }
    }

    private fun bypassesBudgets(frequency: Frequency, isPriority: Boolean): Boolean =
        isPriority || !frequency.countsShows()

    private fun budgetsAllow(reservations: Collection<ShowReservation>): Boolean =
        allAllow(
            reservations,
            maxInappsPerSessionLimitChecker,
            maxInappsPerDayLimitChecker,
            minIntervalBetweenShowsLimitChecker
        )
}
