package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Payload
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

internal class InAppInteractorImpl(
    private val mobileConfigRepository: MobileConfigRepository,
    private val inAppRepository: InAppRepository,
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppFilteringManager: InAppFilteringManager,
    private val inAppEventManager: InAppEventManager,
) : InAppInteractor {


    override fun processEventAndConfig(): Flow<InAppType> {
        return mobileConfigRepository.listenInAppsSection().filterNotNull()
            .combine(inAppRepository.listenInAppEvents().filter { event ->
                MindboxLoggerImpl.d(this, "Event triggered: $event")
                inAppEventManager.isValidInAppEvent(event)
            }) { inApps, event ->
                val inApp = chooseInAppToShow(inApps, event)
                when (val type = inApp?.form?.variants?.firstOrNull()) {
                    is Payload.SimpleImage -> InAppType.SimpleImage(
                        inAppId = inApp.id,
                        imageUrl = type.imageUrl,
                        redirectUrl = type.redirectUrl,
                        intentData = type.intentPayload
                    )
                    else -> {
                        MindboxLoggerImpl.d(
                            this,
                            "No innaps to show found"
                        )
                        null
                    }
                }
            }.filterNotNull()
    }

    private suspend fun chooseInAppToShow(inApps: List<InApp>, event: InAppEventType): InApp? {
        val unShownInApps =
            inAppFilteringManager.filterNotShownInApps(inAppRepository.getShownInApps(),
                inApps)
        inAppSegmentationRepository.unShownInApps = unShownInApps
        MindboxLoggerImpl.d(
            this,
            "Filtered config has ${unShownInApps.size} inapps"
        )
        if (event == InAppEventType.AppStartup) {
            for (inApp in unShownInApps) {
                for (operation in inApp.targeting.getOperationsSet()) {
                    inAppRepository.saveOperationalInApp(operation, inApp)
                }
            }
            for (inApp in unShownInApps) {
                inApp.targeting.fetchTargetingInfo()
                if (inApp.targeting.checkTargeting())
                    return inApp
            }
            return null
        } else {
            // добавить обработку скипа инаппов если не хватает данных для вычисления таргетинга
            val filteredInApps = inAppRepository.getOperationalInAppsByOperation(event.name)
            for (inApp in filteredInApps) {
                inApp.targeting.fetchTargetingInfo()
                if (inApp.targeting.checkTargeting()) {
                    return inApp
                }
            }
            return null
        }
    }


    override fun saveShownInApp(id: String) {
        inAppRepository.saveShownInApp(id)
    }

    override fun sendInAppShown(inAppId: String) {
        inAppRepository.sendInAppShown(inAppId)
    }

    override fun sendInAppClicked(inAppId: String) {
        inAppRepository.sendInAppClicked(inAppId)
    }

    override suspend fun fetchMobileConfig() {
        mobileConfigRepository.fetchMobileConfig()
    }
}