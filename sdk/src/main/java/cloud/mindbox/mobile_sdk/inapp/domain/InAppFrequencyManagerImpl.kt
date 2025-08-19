package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class InAppFrequencyManagerImpl(private val inAppRepository: InAppRepository) :
    InAppFrequencyManager {
    override fun filterInAppsFrequency(inApps: List<InApp>): List<InApp> {
        val shownInApps = inAppRepository.getShownInApps()
        return inApps.filter { inApp ->
            val lastShownTimeStamp =
                shownInApps[inApp.id]?.maxOrNull() ?: run {
                    mindboxLogI("InApp with id = ${inApp.id} was never shown before. Frequency filter won't be applied")
                    return@filter true
                }
            when (inApp.frequency.delay) {
                is Frequency.Delay.LifetimeDelay -> {
                    mindboxLogI("InApp with id = ${inApp.id} has lifetime delay and lastShownTimestamp is $lastShownTimeStamp. Skip this inApp")
                    false
                }

                is Frequency.Delay.TimeDelay -> {
                    val delay = lastShownTimeStamp + inApp.frequency.delay.unit.toMillis(inApp.frequency.delay.time)
                    val currentTime = System.currentTimeMillis()
                    mindboxLogI("InApp with id = ${inApp.id} has periodic delay. " +
                        "Last shown at $lastShownTimeStamp. " +
                        "Compare current time with delay. " +
                        "Current time is $currentTime and delay is $delay. " +
                        "Delay minus current time is ${delay - currentTime}")
                    if ((delay - currentTime) > 0) {
                        mindboxLogI("Difference is positive for inApp with id = ${inApp.id}. Skipping inApp")
                    } else {
                        mindboxLogI("Difference is non positive for inApp with id = ${inApp.id}. Keeping inApp")
                    }
                    delay < currentTime
                }

                Frequency.Delay.OneTimePerSession -> !inAppRepository.isInAppShown(inApp.id).also { result ->
                    mindboxLogI(
                        "InApp with id = ${inApp.id} has settings one time per session. " +
                            "Result of checking whether we can show inapp ${inApp.id} in the current session = ${!result} "
                    )
                }
            }
        }
    }
}
