package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl

internal class InAppFilteringManagerImpl : InAppFilteringManager {

    override fun filterNotShownInApps(shownInApps: Set<String>, inApps: List<InApp>): List<InApp> {
        MindboxLoggerImpl.d(
            this,
            "Already shown innaps: $shownInApps"
        )
        return inApps.filterNot { inApp -> shownInApps.contains(inApp.id) }
    }

    override fun filterUnOperationalInApps(inApps: List<InApp>): List<InApp> {
        return inApps.filter { inApp -> inApp.targeting.getOperationsSet().isEmpty() }
    }
}