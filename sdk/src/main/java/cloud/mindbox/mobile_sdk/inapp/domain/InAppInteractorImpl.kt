package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveResult
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppToShow
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetOwner
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.isAllowedByFrequency
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingDataWrapper
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.millisToTimeSpan
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.countsShows
import cloud.mindbox.mobile_sdk.firstOverlayVariant
import cloud.mindbox.mobile_sdk.sortByPriority
import cloud.mindbox.mobile_sdk.utils.TimeProvider
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
    private val showBudgetManager: ShowBudgetManager,
    private val timeProvider: TimeProvider,
    private val sessionStorageManager: SessionStorageManager,
    private val inAppFailureTracker: InAppFailureTracker,
) : InAppInteractor, MindboxLog {

    private val inAppTargetingChannel = Channel<InAppEventType>(Channel.UNLIMITED)

    private val placeRequestTargetingData =
        TargetingDataWrapper(InAppEventType.EmbeddedPlaceRequested.EVENT_NAME)

    override suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>> {
        val inApps: List<InApp> = mobileConfigRepository.getInAppsSection()
            .let { inApps ->
                inAppRepository.saveCurrentSessionInApps(inApps)
                for (inApp in inApps) {
                    val operations = inApp.targeting.getOperationsSet()
                    if (inApp.displayConditions == DisplayConditions.DIRECT_CALL && operations.isNotEmpty()) {
                        logW(
                            "In-app ${inApp.id} is direct-call only but its targeting listens to " +
                                "operations $operations: a dead combination — it never shows by " +
                                "the operation and never sends its targeting"
                        )
                    }
                    for (operation in operations) {
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
                    .let { inAppFilteringManager.filterOutNonOverlayInApps(it) }
                val inApp: InApp? = chooseAmongCandidates(
                    logLabel = "Event '${event.name}'",
                    candidates = candidates,
                    triggerEvent = event,
                    selectVariant = { candidate -> candidate.firstOverlayVariant() }
                )?.also { winner ->
                    inAppProcessingManager.sendTargetedInApp(winner, event)
                    inAppRepository.saveTargetedInAppWithEvent(
                        inAppId = winner.id,
                        event.hashCode()
                    )
                }
                inAppTargetingChannel.send(event)
                if (event == InAppEventType.AppStartup) {
                    InitializeLock.complete(InitializeLock.State.APP_STARTED)
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
    ): EmbeddedResolveResult? {
        val requestedPlace = placeSystemName.trim()
        val inApps = mobileConfigRepository.getInAppsSection()
        inAppRepository.saveCurrentSessionInApps(inApps)
        val candidates = inAppFilteringManager.filterEmbeddedInAppsByPlace(inApps, requestedPlace)
            .let { inAppFilteringManager.filterOutDirectCallInApps(it) }
        val matched = candidates.filter { candidate ->
            inAppProcessingManager.matchesTargeting(candidate, triggerEvent)
        }
        logI("Place '$requestedPlace': ${matched.size} of ${candidates.size} candidate(s) matched targeting")
        val inAppsPool = inAppABTestLogic.getInAppsPool(inApps.map { inApp -> inApp.id })
        val winner = inAppFilteringManager.filterABTestsInApps(matched, inAppsPool)
            .let { inAppFrequencyManager.filterInAppsFrequency(it) }
            .sortByPriority()
            .firstOrNull { candidate -> candidate.embeddedVariantFor(requestedPlace) != null }

        sendPlaceTargetings(requestedPlace, matched, winner)
        if (winner == null) {
            logI("Place '$requestedPlace': nothing to show")
            inAppFailureTracker.sendCollectedFailures()
            return null
        }
        inAppFailureTracker.clearFailures()
        if (!isPlaceWinnerWithinBudgets(requestedPlace, winner)) {
            logI("Place '$requestedPlace': in-app ${winner.id} is blocked by the show limits")
            return null
        }
        val variant = winner.embeddedVariantFor(requestedPlace) ?: return null
        val delayTime = winner.delayTime?.takeIf { delay ->
            delay.interval > 0 && waitedOutDelayKey(requestedPlace, winner.id) !in sessionStorageManager.embeddedDelaysWaitedOut
        }
        if (winner.delayTime != null && delayTime == null) {
            logI("Place '$requestedPlace': in-app ${winner.id} waits no delay (already waited out this session or zero)")
        }
        return EmbeddedResolveResult(
            variant = variant,
            delayTime = delayTime,
        )
    }

    override fun markEmbeddedDelayWaitedOut(placeSystemName: String, inAppId: String) {
        sessionStorageManager.embeddedDelaysWaitedOut.add(waitedOutDelayKey(placeSystemName.trim(), inAppId))
    }

    private fun waitedOutDelayKey(place: String, inAppId: String): String = "$place|$inAppId"

    private fun sendPlaceTargetings(place: String, matched: List<InApp>, winner: InApp?) {
        for (inApp in matched) {
            if (inApp.id == winner?.id) {
                if (sessionStorageManager.embeddedLastTargetedByPlace.put(place, inApp.id) == inApp.id) {
                    logI("Place '$place': winner ${inApp.id} is the last targeted here, no second targeting")
                } else {
                    sessionStorageManager.placeTargetingReportedInSession.add(inApp.id)
                    inAppProcessingManager.sendTargetedInApp(inApp)
                }
            } else {
                if (sessionStorageManager.placeTargetingReportedInSession.add(inApp.id)) {
                    inAppProcessingManager.sendTargetedInApp(inApp)
                } else {
                    logI("Place '$place': in-app ${inApp.id} already sent its targeting this session")
                }
            }
        }
    }

    private fun InApp.embeddedVariantFor(place: String): InAppType.Embedded? =
        form.variants
            .filterIsInstance<InAppType.Embedded>()
            .firstOrNull { variant -> variant.placeSystemName == place }

    private suspend fun abTestFilteredInApps(inApps: List<InApp>): List<InApp> =
        inAppFilteringManager.filterABTestsInApps(inApps, inAppABTestLogic.getInAppsPool(inApps.map { it.id }))

    private suspend fun chooseAmongCandidates(
        logLabel: String,
        candidates: List<InApp>,
        triggerEvent: InAppEventType,
        selectVariant: (InApp) -> InAppType?,
    ): InApp? {
        val showable = inAppFilteringManager.filterOutDirectCallInApps(candidates)
            .let { inAppFrequencyManager.filterInAppsFrequency(it) }
        logI("$logLabel: ${showable.size} candidate(s) after filtering: ${showable.map { it.id }}")
        return inAppProcessingManager.chooseInAppToShow(showable.sortByPriority(), triggerEvent, selectVariant)
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

    override suspend fun getInAppToShowById(inAppId: String): InAppToShow? {
        val inApp = findInAppById(inAppId)
            ?: run {
                logI("No in-app with id $inAppId to resolve content for")
                return null
            }
        val variant = inApp.firstOverlayVariant() ?: run {
            logE("In-app $inAppId is drawn inside the host layout and is never shown as an overlay")
            return null
        }
        return InAppToShow(inApp, variant)
    }

    override suspend fun filterShowableInAppIds(hostInAppId: String, inAppIds: List<String>): List<String> {
        if (inAppIds.isEmpty()) return emptyList()
        val inApps = mobileConfigRepository.getInAppsSection()
        val inAppsPool = inAppABTestLogic.getInAppsPool(inApps.map { it.id })
        val showableIds = inAppFilteringManager.filterABTestsInApps(inApps, inAppsPool)
            .map { inApp -> inApp.id }
            .toSet()
        val fullById = inApps.distinctBy { inApp -> inApp.id }.associateBy { inApp -> inApp.id }
        val matchedById = mutableMapOf<String, Boolean>()
        for (id in inAppIds.distinct()) {
            val inApp = fullById[id]
            if (inApp == null) {
                logI("Requested id $id is not in the config (or filtered by sdkVersion), cutting it")
                continue
            }
            if (inApp.firstOverlayVariant() == null) {
                logI("Requested id $id has no overlay variant to draw, cutting it")
                continue
            }
            matchedById[id] = matchesRequestedTargeting(inApp)
        }
        sendRequestedTargetings(hostInAppId, fullById, matchedById)
        return inAppIds.filter { id ->
            if (matchedById[id] != true) return@filter false
            if (id !in showableIds) {
                logI("Requested id $id is filtered by ab-tests, cutting it")
                return@filter false
            }
            if (!inAppFrequencyManager.isAllowedByFrequency(fullById.getValue(id))) {
                logI("Requested id $id is blocked by its frequency, cutting it")
                return@filter false
            }
            true
        }
    }

    private fun sendRequestedTargetings(hostInAppId: String, fullById: Map<String, InApp>, matchedById: Map<String, Boolean>) {
        for ((id, matches) in matchedById) {
            if (!matches) continue
            if (sessionStorageManager.requestedInAppTargetingReportedInSession.add("$hostInAppId|$id")) {
                inAppProcessingManager.sendTargetedInApp(fullById.getValue(id))
            }
        }
    }

    private suspend fun matchesRequestedTargeting(inApp: InApp): Boolean =
        runCatching {
            inApp.targeting.fetchTargetingInfo(placeRequestTargetingData)
            inApp.targeting.checkTargeting(placeRequestTargetingData)
        }
            .getOrElse { error ->
                logI("Requested id ${inApp.id} targeting could not be checked ($error), cutting it")
                false
            }
            .also { matches -> if (!matches) logI("Requested id ${inApp.id} targeting did not match, cutting it") }

    private fun isPlaceWinnerWithinBudgets(place: String, winner: InApp): Boolean =
        sessionStorageManager.embeddedLastShownByPlace[place] == winner.id ||
            showBudgetManager.isWithinBudgets(winner.frequency, winner.isPriority, ShowBudgetOwner.place(place))

    override fun reservePlaceShow(placeSystemName: String, content: InAppType.Embedded): Boolean {
        val place = placeSystemName.trim()
        if (sessionStorageManager.embeddedLastShownByPlace[place] == content.inAppId) {
            logI("Place '$place' already shows in-app ${content.inAppId}, no new show to reserve")
            return true
        }
        val inApp = inAppRepository.getCurrentSessionInApps().firstOrNull { it.id == content.inAppId }
        if (inApp != null && !inAppFrequencyManager.isAllowedByFrequency(inApp)) {
            logI("Place '$place': in-app ${content.inAppId} is blocked by its frequency since it was picked, the place stays empty")
            return false
        }
        return showBudgetManager.reserve(ShowBudgetOwner.place(place), content.inAppId, content.frequency, content.isPriority) !=
            ShowReservationOutcome.REFUSED
    }

    override fun releasePlaceShow(placeSystemName: String) {
        showBudgetManager.release(ShowBudgetOwner.place(placeSystemName.trim()))
    }

    override fun reserveOverlayShow(inApp: InApp): ShowReservationOutcome {
        if (!inAppFrequencyManager.isAllowedByFrequency(inApp)) return ShowReservationOutcome.REFUSED
        return showBudgetManager.reserve(ShowBudgetOwner.overlay(inApp.id), inApp.id, inApp.frequency, inApp.isPriority)
    }

    override fun releaseOverlayShow(inAppId: String) {
        showBudgetManager.release(ShowBudgetOwner.overlay(inAppId))
    }

    private suspend fun findInAppById(inAppId: String): InApp? =
        mobileConfigRepository.getInAppsSection().firstOrNull { inApp -> inApp.id == inAppId }

    override fun recordBlockShow(
        placeSystemName: String,
        inAppId: String,
        frequency: Frequency,
        timeToDisplay: Milliseconds,
        tags: Map<String, String>?,
    ) {
        val place = placeSystemName.trim()
        val lastShown = sessionStorageManager.embeddedLastShownByPlace.put(place, inAppId)
        if (lastShown == inAppId) {
            logI("Place '$place': the block re-drew in-app $inAppId it already showed, nothing to report")
            return
        }
        showBudgetManager.commit(ShowBudgetOwner.place(place), inAppId, frequency, timeProvider.currentTimestamp())
        logI("In-app $inAppId sends its show, timeToDisplay=${timeToDisplay.interval} ms")
        inAppRepository.sendInAppShown(inAppId, timeToDisplay.interval.millisToTimeSpan(), tags)
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
                showBudgetManager.release(ShowBudgetOwner.overlay(id))
                return
            }
        showBudgetManager.commit(ShowBudgetOwner.overlay(id), id, inApp.frequency, timeStamp.toTimestamp())
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
                .let { inAppFilteringManager.filterOutNonOverlayInApps(it) }
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

    override fun saveInAppDismissTime(inApp: InApp) {
        val timeStamp = timeProvider.currentTimestamp()
        mindboxLogI("Last in-app display duration ${(timeStamp - inAppRepository.getLastInappDismissTime()).ms} ms")
        if (!inApp.countsShows()) {
            logI("In-app ${inApp.id} is unlimited, the dismiss does not move the cooldown")
            return
        }
        showBudgetManager.recordCooldown(inApp.frequency, timeStamp)
    }
}
