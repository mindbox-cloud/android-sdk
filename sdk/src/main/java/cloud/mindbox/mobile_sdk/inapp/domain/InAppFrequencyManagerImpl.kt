package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class InAppFrequencyManagerImpl(private val inAppRepository: InAppRepository) :
    InAppFrequencyManager {
    override fun filterInAppsFrequency(inApps: List<InApp>): List<InApp> {

        return inApps.filter { inApp ->
            val lastShownTimeStamp =
                inAppRepository.getShownInApps()[inApp.id] ?: run {
                    mindboxLogI("InApp was never shown before. Frequency filter won't be applied")
                    return@filter true
                }
            when (inApp.frequency.delay) {
                is Frequency.Delay.LifetimeDelay -> {
                    mindboxLogI("Lifetime delay and lastShownTimestamp is ${lastShownTimeStamp}. Skip this inApp")
                    false
                }

                is Frequency.Delay.TimeDelay -> {
                    val delay = lastShownTimeStamp + inApp.frequency.delay.unit.toMillis(inApp.frequency.delay.time)
                    val currentTime = System.currentTimeMillis()
                    mindboxLogI("Periodic delay. " +
                            "Last shown at $lastShownTimeStamp. " +
                            "Compare current time with delay. " +
                            "Current time is $currentTime ms and delay is $delay ms. " +
                            "Delay minus current time is ${delay - currentTime} ms")
                    if ((delay - currentTime) > 0) {
                        mindboxLogI("Difference is positive. Skipping inApp")
                    } else {
                        mindboxLogI("Difference is non positive. Keeping inApp")
                    }
                    delay < currentTime
                }
            }
        }
    }
}