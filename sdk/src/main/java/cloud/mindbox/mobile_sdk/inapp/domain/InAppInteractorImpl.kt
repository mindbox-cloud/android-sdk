package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.flow.*

internal class InAppInteractorImpl(
    private val mobileConfigRepository: MobileConfigRepository,
    private val inAppRepository: InAppRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppFilteringManager: InAppFilteringManager,
    private val inAppEventManager: InAppEventManager,
    private val inAppChoosingManager: InAppChoosingManager,
) : InAppInteractor {

    override suspend fun processEventAndConfig(): Flow<InAppType> {
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection().let { inApps ->
            inAppFilteringManager.filterNotShownInApps(
                inAppRepository.getShownInApps(),
                inApps
            )
        }.also { unShownInApps ->
            MindboxLoggerImpl.d(
                this, "Filtered config has ${unShownInApps.size} inapps"
            )
            inAppSegmentationRepository.unShownInApps = unShownInApps
            for (inApp in unShownInApps) {
                for (operation in inApp.targeting.getOperationsSet()) {
                    inAppRepository.saveOperationalInApp(operation, inApp)
                }
            }
        }

        return inAppRepository.listenInAppEvents()
            .filter { event -> inAppEventManager.isValidInAppEvent(event) }
            .onEach {
                mindboxLogD("Event triggered: ${it.name}")
            }.filter {
                !isInAppShown().also { mindboxLogD("InApp shown: $it") }
            }.map { event ->
                val filteredInApps = inAppFilteringManager.filterInAppsByEvent(inApps, event)
                mindboxLogD("Event: ${event.name} combined with $filteredInApps")

                inAppChoosingManager.chooseInAppToShow(
                    filteredInApps
                ).also { inAppType ->
                    inAppType ?: mindboxLogD("No innaps to show found")
                    if (event == InAppEventType.AppStartup) {
                        InitializeLock.complete(InitializeLock.State.APP_STARTED)
                    }
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