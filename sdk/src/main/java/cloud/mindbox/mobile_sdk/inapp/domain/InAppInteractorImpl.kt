package cloud.mindbox.mobile_sdk.inapp.domain

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
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection()?.let { inApps ->
            inAppFilteringManager.filterNotShownInApps(
                inAppRepository.getShownInApps(),
                inApps
            )
        }?.also { unShownInApps ->
            MindboxLoggerImpl.d(
                this, "Filtered config has ${unShownInApps.size} inapps"
            )
            inAppSegmentationRepository.unShownInApps = unShownInApps
            for (inApp in unShownInApps) {
                for (operation in inApp.targeting.getOperationsSet()) {
                    inAppRepository.saveOperationalInApp(operation, inApp)
                }
            }
        } ?: listOf()

        return inAppRepository.listenInAppEvents()
            .filter { event -> inAppEventManager.isValidInAppEvent(event) }
            .onEach {
                MindboxLoggerImpl.d(this, "Event triggered: ${it.name}")
            }.filter {
                inApps.isNotEmpty()
            }.map { event ->
                val filteredInApps = inAppFilteringManager.filterInAppsByEvent(
                    inApps,
                    event
                )
                MindboxLoggerImpl.d(this, "Event: ${event.name} combined with $filteredInApps")
                inAppChoosingManager.chooseInAppToShow(
                    filteredInApps
                ).also { inAppType ->
                    inAppType ?: MindboxLoggerImpl.d(
                        this, "No innaps to show found"
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