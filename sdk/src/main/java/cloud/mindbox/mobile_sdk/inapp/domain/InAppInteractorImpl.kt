package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import kotlinx.coroutines.flow.*

internal class InAppInteractorImpl(
    private val mobileConfigRepository: MobileConfigRepository,
    private val inAppRepository: InAppRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppFilteringManager: InAppFilteringManager,
    private val inAppEventManager: InAppEventManager,
    private val inAppChoosingManager: InAppChoosingManager,
) : InAppInteractor {

    override fun processEventAndConfig(): Flow<InAppType> {
        return mobileConfigRepository.listenInAppsSection().filterNotNull().onEach { inApps ->
            inAppFilteringManager.filterNotShownInApps(
                inAppRepository.getShownInApps(),
                inApps
            )
        }.onEach { unShownInApps ->
            MindboxLoggerImpl.d(
                this,
                "Filtered config has ${unShownInApps.size} inapps"
            )
            inAppSegmentationRepository.unShownInApps = unShownInApps
        }.onEach { unShownInApps ->
            for (inApp in unShownInApps) {
                for (operation in inApp.targeting.getOperationsSet()) {
                    inAppRepository.saveOperationalInApp(operation, inApp)
                }
            }
        }.combine(inAppRepository.listenInAppEvents().filter { event ->
            MindboxLoggerImpl.d(this, "Event triggered: $event")
            inAppEventManager.isValidInAppEvent(event)
        }) { inApps, event ->
            inAppChoosingManager.chooseInAppToShow(
                inAppFilteringManager.filterInAppsByEvent(
                    inApps,
                    event
                )
            ).also { inAppType ->
                inAppType ?: MindboxLoggerImpl.d(
                    this,
                    "No innaps to show found"
                )
            }
        }.filterNotNull()
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

    override fun isInAppShown(): Boolean {
        return inAppRepository.isInAppShown()
    }

    override fun setInAppShown() {
        inAppRepository.setInAppShown()
    }

    override suspend fun fetchMobileConfig() {
        mobileConfigRepository.fetchMobileConfig()
    }
}