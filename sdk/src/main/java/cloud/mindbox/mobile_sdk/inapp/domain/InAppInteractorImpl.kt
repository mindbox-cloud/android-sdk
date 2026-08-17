package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.isAllowedByFrequency
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingDataWrapper
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.sortByPriority
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.allAllow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

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
    private val timeProvider: TimeProvider,
    private val sessionStorageManager: SessionStorageManager
) : InAppInteractor, MindboxLog {

    private val inAppTargetingChannel = Channel<InAppEventType>(Channel.UNLIMITED)

    // A page request is not an operation: with the synthetic event name and no body,
    // operation-node targetings never match — "no operation is happening right now".
    private val placeRequestTargetingData =
        TargetingDataWrapper(InAppEventType.EmbeddedPlaceRequested.EVENT_NAME)

    override suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>> {
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection()
            .let { inApps ->
                inAppRepository.saveCurrentSessionInApps(inApps)
                for (inApp in inApps) {
                    for (operation in inApp.targeting.getOperationsSet()) {
                        inAppRepository.saveOperationalInApp(operation.lowercase(), inApp)
                    }
                }
                abTestFilteredInApps(inApps).also { filteredInApps ->
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
            .onEach { event ->
                mindboxLogD("Event triggered: ${event.name}")
            }.map { event ->
                val triggerTimeMillis = timeProvider.currentTimestamp()
                val candidates = inAppFilteringManager.filterUnShownInAppsByEvent(inApps, event)
                    .let { inAppFilteringManager.filterOutEmbeddedInApps(it) }
                val inApp: InApp? = chooseAmongCandidates(
                    logLabel = "Event '${event.name}'",
                    candidates = candidates,
                    triggerEvent = event
                ).also {
                    inAppTargetingChannel.send(event)
                    if (event == InAppEventType.AppStartup) {
                        InitializeLock.complete(InitializeLock.State.APP_STARTED)
                    }
                }
                inApp?.let {
                    sessionStorageManager.inAppTriggerEvent = event
                }
                inApp?.let { inapp -> inapp to timeProvider.elapsedSince(triggerTimeMillis) }
            }
            .onEach { pair ->
                pair?.let { (inApp, preparedTime) -> mindboxLogI("InApp ${inApp.id} isPriority=${inApp.isPriority}, delayTime=${inApp.delayTime}, skipLimitChecks=${inApp.isPriority}, preparedTime = ${preparedTime.interval} ms") }
                    ?: mindboxLogI("No inapps to show found")
            }
            .filterNotNull()
    }

    override suspend fun selectInAppForPlace(
        placeSystemName: String,
        triggerEvent: InAppEventType,
    ): InAppType.Embedded? {
        val requestedPlace = placeSystemName.trim()
        val inApps = mobileConfigRepository.getInAppsSection()
        inAppRepository.saveCurrentSessionInApps(inApps)
        // The same chain as the event path: the display style does not change "when to show".
        val candidates = abTestFilteredInApps(inApps)
            .let { inAppFilteringManager.filterEmbeddedInAppsByPlace(it, requestedPlace) }
        val winner = chooseAmongCandidates(
            logLabel = "Place '$requestedPlace'",
            candidates = candidates,
            triggerEvent = triggerEvent
        )
            ?: run {
                logI("Place '$requestedPlace': nothing to show")
                return null
            }

        if (!areShowLimitsAllowed(winner)) {
            logI("Place '$requestedPlace': in-app ${winner.id} is blocked by the show limits")
            return null
        }
        return winner.form.variants
            .filterIsInstance<InAppType.Embedded>()
            .firstOrNull { variant -> variant.placeSystemName == requestedPlace }
    }

    private suspend fun abTestFilteredInApps(inApps: List<InApp>): List<InApp> =
        inAppFilteringManager.filterABTestsInApps(inApps, inAppABTestLogic.getInAppsPool(inApps.map { it.id }))

    private suspend fun chooseAmongCandidates(
        logLabel: String,
        candidates: List<InApp>,
        triggerEvent: InAppEventType,
    ): InApp? {
        val showable = inAppFilteringManager.filterOutDirectCallInApps(candidates)
            .let { inAppFrequencyManager.filterInAppsFrequency(it) }
        logI("$logLabel: ${showable.size} candidate(s) after filtering: ${showable.map { it.id }}")
        return inAppProcessingManager.chooseInAppToShow(showable.sortByPriority(), triggerEvent)
    }

    override fun listenConfigUpdates(): Flow<Unit> = mobileConfigRepository.listenConfigUpdates()

    override fun listenEmbeddedPlaceEvents(): Flow<EmbeddedPlaceEvent> = flow {
        inAppRepository.listenLiveInAppEvents()
            .filter { event ->
                event is InAppEventType.OrdinalEvent && inAppEventManager.isValidInAppEvent(event)
            }
            .collect { event ->
                inAppRepository.getOperationalInAppsByOperation(event.name)
                    .flatMap { inApp -> inApp.form.variants.filterIsInstance<InAppType.Embedded>() }
                    .map { variant -> variant.placeSystemName }
                    .toSet()
                    .forEach { place ->
                        logI("Operation '${event.name}' matched embedded place '$place'")
                        emit(EmbeddedPlaceEvent(placeSystemName = place, triggerEvent = event))
                    }
            }
    }

    override suspend fun getInAppById(inAppId: String): InAppType? {
        val inApp = findInAppById(inAppId)
            ?: run {
                logI("No in-app with id $inAppId to resolve content for")
                return null
            }
        val variant = inApp.form.variants.firstOrNull()
        if (variant is InAppType.Embedded) {
            logI("In-app $inAppId is embedded and is never shown as an overlay")
            return null
        }
        return variant
    }

    override suspend fun filterShowableInAppIds(inAppIds: List<String>): List<String> {
        if (inAppIds.isEmpty()) return emptyList()
        val inApps = mobileConfigRepository.getInAppsSection()
        val inAppsPool = inAppABTestLogic.getInAppsPool(inApps.map { it.id })
        val showableById = inAppFilteringManager.filterABTestsInApps(inApps, inAppsPool)
            .distinctBy { inApp -> inApp.id }
            .associateBy { inApp -> inApp.id }
        return inAppIds.filter { id ->
            val inApp = showableById[id] ?: run {
                logI("Requested id $id is not in the config (or filtered by sdkVersion/ab-tests), cutting it")
                return@filter false
            }
            if (inApp.form.variants.any { variant -> variant is InAppType.Embedded }) {
                logI("Requested id $id points to an embedded in-app, cutting it (no feed inside a feed)")
                return@filter false
            }
            if (!inAppFrequencyManager.isAllowedByFrequency(inApp)) {
                logI("Requested id $id is blocked by its frequency, cutting it")
                return@filter false
            }
            runCatching { inApp.targeting.checkTargeting(placeRequestTargetingData) }
                .getOrElse { error ->
                    logI("Requested id $id targeting could not be checked without network ($error), cutting it")
                    false
                }
                .also { matches -> if (!matches) logI("Requested id $id targeting did not match, cutting it") }
        }.onEach { id ->
            inAppProcessingManager.sendTargetedInApp(showableById.getValue(id))
        }
    }

    override fun areShowAndFrequencyLimitsAllowed(inApp: InApp): Boolean =
        inAppFrequencyManager.isAllowedByFrequency(inApp) && areShowLimitsAllowed(inApp)

    override fun areShowLimitsAllowed(inApp: InApp): Boolean =
        inApp.isPriority ||
            inApp.frequency.delay is Frequency.Delay.Unlimited ||
            allAllow(
                maxInappsPerSessionLimitChecker,
                maxInappsPerDayLimitChecker,
                minIntervalBetweenShowsLimitChecker
            )

    private suspend fun findInAppById(inAppId: String): InApp? =
        mobileConfigRepository.getInAppsSection().firstOrNull { inApp -> inApp.id == inAppId }

    override suspend fun recordShowLocally(inAppId: String) {
        val inApp = findInAppById(inAppId)
            ?: run {
                logI("No in-app with id $inAppId to count a show for")
                return
            }
        recordShowCounters(inApp, timeProvider.currentTimestamp())
    }

    override fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    ) {
        inAppRepository.sendInAppShown(id, timeToDisplay, tags)
        val inApp = inAppRepository.getCurrentSessionInApps().firstOrNull { it.id == id }
            ?: run {
                logI("No in-app with id $id in the current session to count a show for")
                return
            }
        recordShowCounters(inApp, timeStamp.toTimestamp())
    }

    private fun recordShowCounters(inApp: InApp, shownAt: Timestamp) {
        if (inApp.frequency.delay is Frequency.Delay.Unlimited) {
            logI("In-app ${inApp.id} has unlimited frequency, nothing to count")
            return
        }
        logI("Counting a show of in-app ${inApp.id} (frequency ${inApp.frequency.delay})")
        inAppRepository.setInAppShown(inApp.id)
        inAppRepository.saveShownInApp(inApp.id, shownAt.ms)
        inAppRepository.saveInAppStateChangeTime(shownAt)
    }

    override fun sendInAppClicked(inAppId: String, tags: Map<String, String>?) {
        inAppRepository.sendInAppClicked(inAppId, tags)
    }

    override suspend fun listenToTargetingEvents() {
        val inApps = mobileConfigRepository.getInAppsSection()
        val inAppsMap = inAppRepository.getTargetedInApps()
        logI("Whole InApp list = $inApps")
        logI("InApps that has already sent targeting ${inAppsMap.entries}")
        inAppTargetingChannel.receiveAsFlow().collect { event ->
            val filteredInApps = inAppFilteringManager.filterInAppsByEvent(inApps, event)
                .let { inAppFilteringManager.filterOutDirectCallInApps(it) }
            logI("inapps for event $event are = $filteredInApps")
            for (inApp in filteredInApps) {
                if (inAppsMap[inApp.id]?.contains(event.hashCode()) != true) {
                    inAppProcessingManager.sendTargetedInApp(inApp, event)
                }
            }
        }
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
