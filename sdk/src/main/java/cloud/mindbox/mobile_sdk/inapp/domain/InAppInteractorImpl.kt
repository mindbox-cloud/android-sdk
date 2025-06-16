package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import cloud.mindbox.mobile_sdk.utils.allAllow

internal class InAppInteractorImpl(
    private val mobileConfigRepository: MobileConfigRepository,
    private val inAppRepository: InAppRepository,
    private val inAppFilteringManager: InAppFilteringManager,
    private val inAppEventManager: InAppEventManager,
    private val inAppProcessingManager: InAppProcessingManager,
    private val inAppABTestLogic: InAppABTestLogic,
    private val inAppFrequencyManager: InAppFrequencyManager,
    private val maxInappsPerSessionLimitChecker: Checker,
    private val maxInappsPerDayLimitChecker: Checker,
    private val minIntervalBetweenShowsLimitChecker: Checker,
    private val timeProvider: TimeProvider
) : InAppInteractor, MindboxLog {

    private val inAppTargetingChannel = Channel<InAppEventType>(Channel.UNLIMITED)

    override suspend fun processEventAndConfig(): Flow<InAppType> {
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection()
            .let { inApps ->
                inAppRepository.saveCurrentSessionInApps(inApps)
                for (inApp in inApps) {
                    for (operation in inApp.targeting.getOperationsSet()) {
                        inAppRepository.saveOperationalInApp(operation.lowercase(), inApp)
                    }
                }
                val inAppIds = inAppABTestLogic.getInAppsPool(inApps.map { it.id })
                inAppFilteringManager.filterABTestsInApps(inApps, inAppIds).also { filteredInApps ->
                    logI("InApps after abtest logic ${filteredInApps.map { it.id }}")
                }
            }.also { unShownInApps ->
                logI("Filtered config has ${unShownInApps.size} inapps")
                for (inApp in unShownInApps) {
                    for (operation in inApp.targeting.getOperationsSet()) {
                        inAppRepository.saveUnShownOperationalInApp(operation.lowercase(), inApp)
                    }
                }
            }
        return inAppRepository.listenInAppEvents()
            .filter { event -> inAppEventManager.isValidInAppEvent(event) }
            .onEach {
                mindboxLogD("Event triggered: ${it.name}")
            }.map { event ->
                val filteredInApps = inAppFilteringManager.filterUnShownInAppsByEvent(inApps, event).let {
                    inAppFrequencyManager.filterInAppsFrequency(it)
                }
                mindboxLogI("Event: ${event.name} combined with $filteredInApps")
                inAppProcessingManager.chooseInAppToShow(
                    filteredInApps,
                    event
                ).also {
                    inAppTargetingChannel.send(event)
                    if (event == InAppEventType.AppStartup) {
                        InitializeLock.complete(InitializeLock.State.APP_STARTED)
                    }
                }
            }
            .onEach { inApp ->
                if (inApp == null) mindboxLogI("No inapps to show found")
            }
            .filterNotNull()
            .filter { inApp ->
                logI("Inapp ${inApp.id} is Priority: ${inApp.isPriority}")
                allAllow(
                    maxInappsPerSessionLimitChecker,
                    maxInappsPerDayLimitChecker,
                    minIntervalBetweenShowsLimitChecker
                )
            }
            .mapNotNull { inApp ->
                inApp.form.variants.firstOrNull()
            }
    }

    override fun saveShownInApp(id: String, timeStamp: Long) {
        inAppRepository.setInAppShown(id)
        inAppRepository.sendInAppShown(id)
        inAppRepository.saveShownInApp(id, timeStamp)
        inAppRepository.saveInAppStateChangeTime(timeStamp.toTimestamp())
    }

    override fun sendInAppClicked(inAppId: String) {
        inAppRepository.sendInAppClicked(inAppId)
    }

    override suspend fun listenToTargetingEvents() {
        val inApps = mobileConfigRepository.getInAppsSection()
        val inAppsMap = inAppRepository.getTargetedInApps()
        logI("Whole InApp list = $inApps")
        logI("InApps that has already sent targeting ${inAppsMap.entries}")
        inAppTargetingChannel.receiveAsFlow().collect { event ->
            val filteredInApps = inAppFilteringManager.filterInAppsByEvent(inApps, event)
            logI("inapps for event $event are = $filteredInApps")
            for (inApp in filteredInApps) {
                if (inAppsMap[inApp.id]?.contains(event.hashCode()) != true) {
                    inAppProcessingManager.sendTargetedInApp(inApp, event)
                }
            }
        }
    }

    override fun isInAppShown(inAppId: String): Boolean {
        return inAppRepository.isInAppShown(inAppId)
    }

    override fun setInAppShown(inAppId: String) {
        inAppRepository.setInAppShown(inAppId)
    }

    override suspend fun fetchMobileConfig() {
        mobileConfigRepository.fetchMobileConfig()
    }

    override fun resetInAppConfigAndEvents() {
        mobileConfigRepository.resetCurrentConfig()
        inAppRepository.clearInAppEvents()
    }

    override fun isTimeDelayInapp(inAppId: String): Boolean {
        return inAppRepository.isTimeDelayInapp(inAppId)
    }

    override fun saveInAppDismissTime() {
        val timeStamp = timeProvider.currentTimestamp()
        mindboxLogI("Last in-app display duration ${(timeStamp - inAppRepository.getLastInappDismissTime()).ms} ms")
        inAppRepository.saveInAppStateChangeTime(timeStamp = timeStamp)
    }
}
