package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class InAppInteractorImpl(
    private val mobileConfigRepository: MobileConfigRepository,
    private val inAppRepository: InAppRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppFilteringManager: InAppFilteringManager,
    private val inAppEventManager: InAppEventManager,
    private val inAppProcessingManager: InAppProcessingManager,
    private val inAppABTestLogic: InAppABTestLogic,
    private val sessionStorageManager: SessionStorageManager
) : InAppInteractor, MindboxLog {

    override suspend fun processEventAndConfig(): Flow<InAppType> {
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection()
            .let { inApps ->
                sessionStorageManager.inApps = inApps
                val inAppIds = inAppABTestLogic.getInAppsPool(inApps.map { it.id })
                inAppFilteringManager.filterABTestsInApps(inApps, inAppIds).also { filteredInApps ->
                    logI("InApps after abtest logic ${filteredInApps.map { it.id }}")
                }
            }.let { inApps ->
                inAppFilteringManager.filterNotShownInApps(
                    inAppRepository.getShownInApps(),
                    inApps
                )
            }.also { unShownInApps ->
                logI("Filtered config has ${unShownInApps.size} inapps")
                for (inApp in unShownInApps) {
                    for (operation in inApp.targeting.getOperationsSet()) {
                        inAppRepository.saveOperationalInApp(operation.lowercase(), inApp)
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

                inAppProcessingManager.chooseInAppToShow(
                    filteredInApps,
                    event
                ).also { inAppType ->
                    inAppType ?: mindboxLogD("No innaps to show found")
                    withContext(Dispatchers.IO) {
                        launch {
                            sendTargetedInApps()
                        }
                    }
                    if (event == InAppEventType.AppStartup) {
                        InitializeLock.complete(InitializeLock.State.APP_STARTED)
                    }
                    inAppSegmentationRepository.setProductSegmentationFetchStatus(
                        ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
                    )
                }
            }.filterNotNull()
    }

    override fun saveShownInApp(id: String) {
        val shownInApps = inAppRepository.getShownInApps()
        val isAlreadySaved = shownInApps.contains(id)

        if (!isAlreadySaved) {
            inAppRepository.setInAppShown()
            inAppRepository.sendInAppShown(id)
            inAppRepository.saveShownInApp(id)
        }
    }

    override fun sendInAppClicked(inAppId: String) {
        inAppRepository.sendInAppClicked(inAppId)
    }

    private suspend fun sendTargetedInApps() {
        val inApps = mobileConfigRepository.getInAppsSection().filterNot { inApp ->
            inApp.id == sessionStorageManager.currentShownInAppId
        }
        inAppRepository.listenInAppEvents()
            .filter { inAppEventManager.isValidInAppEvent(it) }
            .collect {event ->
                inAppProcessingManager.sendTargetedInApps(inApps, event)
            }

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
