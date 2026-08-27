package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.util.concurrent.ConcurrentHashMap
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The MOBILE-333 resolve trio: content for a place (pull and push), content by id for the
 * future direct call, and the dictionary answer to `filterShowableInapps`. Presentation limits and
 * the lock are structurally absent from these paths — the paired negative tests pin that.
 */
@ExperimentalCoroutinesApi
class EmbeddedResolveInteractorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mobileConfigRepository: MobileConfigRepository

    @RelaxedMockK
    private lateinit var inAppRepository: InAppRepository

    @MockK
    private lateinit var inAppProcessingManager: InAppProcessingManager

    @RelaxedMockK
    private lateinit var inAppABTestLogic: InAppABTestLogic

    @MockK
    private lateinit var maxInappsPerSessionLimitChecker: Checker

    @MockK
    private lateinit var maxInappsPerDayLimitChecker: Checker

    @MockK
    private lateinit var minIntervalBetweenShowsLimitChecker: Checker

    @RelaxedMockK
    private lateinit var timeProvider: TimeProvider

    @RelaxedMockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @RelaxedMockK
    private lateinit var inAppFailureTracker: InAppFailureTracker

    private lateinit var frequencyManager: InAppFrequencyManagerImpl

    private lateinit var interactor: InAppInteractorImpl

    private val now = Timestamp(1_000_000L)

    private val place = "main-screen-top"

    @Before
    fun setUp() {
        frequencyManager = spyk(InAppFrequencyManagerImpl(inAppRepository))
        interactor = InAppInteractorImpl(
            mobileConfigRepository = mobileConfigRepository,
            inAppRepository = inAppRepository,
            inAppFilteringManager = InAppFilteringManagerImpl(inAppRepository),
            inAppEventManager = InAppEventManagerImpl(),
            inAppProcessingManager = inAppProcessingManager,
            inAppABTestLogic = inAppABTestLogic,
            inAppFrequencyManager = frequencyManager,
            maxInappsPerSessionLimitChecker = maxInappsPerSessionLimitChecker,
            maxInappsPerDayLimitChecker = maxInappsPerDayLimitChecker,
            minIntervalBetweenShowsLimitChecker = minIntervalBetweenShowsLimitChecker,
            timeProvider = timeProvider,
            sessionStorageManager = sessionStorageManager,
            inAppFailureTracker = inAppFailureTracker,
        )
        every { timeProvider.currentTimestamp() } returns now
        every { inAppRepository.getShownInApps() } returns emptyMap()
        every { inAppProcessingManager.sendTargetedInApp(any()) } just runs
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs
        every { sessionStorageManager.placeTargetingReportedInSession } returns ConcurrentHashMap.newKeySet()
        every { sessionStorageManager.requestedInAppTargetingReportedInSession } returns ConcurrentHashMap.newKeySet()
        every { sessionStorageManager.embeddedLastShownByPlace } returns ConcurrentHashMap()
        every { sessionStorageManager.embeddedLastTargetedByPlace } returns ConcurrentHashMap()
        every { sessionStorageManager.embeddedDelaysWaitedOut } returns ConcurrentHashMap.newKeySet()
        coEvery { inAppProcessingManager.matchesTargeting(any(), any()) } returns true
        every { maxInappsPerSessionLimitChecker.check() } returns true
        every { maxInappsPerDayLimitChecker.check() } returns true
        every { minIntervalBetweenShowsLimitChecker.check() } returns true
        coEvery { inAppABTestLogic.getInAppsPool(any()) } answers { firstArg<List<String>>().toSet() }
        coEvery { inAppProcessingManager.chooseInAppToShow(any(), any(), any()) } answers {
            firstArg<List<InApp>>().firstOrNull()
        }
    }

    private fun embeddedInApp(
        id: String = "embedded-id",
        placeName: String = place,
        isPriority: Boolean = false,
    ): InApp = InAppStub.getInApp().copy(
        id = id,
        isPriority = isPriority,
        targeting = InAppStub.getTargetingTrueNode(),
        form = Form(variants = listOf(InAppStub.getEmbedded().copy(inAppId = id, placeSystemName = placeName)))
    )

    private fun modalInApp(id: String = "modal-id"): InApp =
        InAppStub.getInApp().copy(id = id, targeting = InAppStub.getTargetingTrueNode())

    private fun mixedInApp(id: String = "mixed-id", placeName: String = place): InApp =
        InAppStub.getInApp().copy(
            id = id,
            targeting = InAppStub.getTargetingTrueNode(),
            form = Form(
                variants = listOf(
                    InAppStub.getEmbedded().copy(inAppId = id, placeSystemName = placeName),
                    InAppStub.getModalWindow().copy(inAppId = id)
                )
            )
        )

    private fun givenConfig(vararg inApps: InApp) {
        coEvery { mobileConfigRepository.getInAppsSection() } returns inApps.toList()
    }

    @Test
    fun `selectInAppForPlace returns embedded content for known place`() = runTest {
        givenConfig(embeddedInApp(), modalInApp())

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant

        assertEquals("embedded-id", content?.inAppId)
        assertEquals(place, content?.placeSystemName)
    }

    @Test
    fun `selectInAppForPlace returns null for unknown place`() = runTest {
        givenConfig(embeddedInApp(), modalInApp())

        assertNull(interactor.selectInAppForPlace("no-such-place", InAppEventType.EmbeddedPlaceRequested("no-such-place")))
    }

    @Test
    fun `selectInAppForPlace picks first by priority on place collision`() = runTest {
        givenConfig(
            embeddedInApp(id = "ordinary"),
            embeddedInApp(id = "priority", isPriority = true),
        )

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant

        assertEquals("priority", content?.inAppId)
    }

    @Test
    fun `selectInAppForPlace hands the winner delayTime to the caller instead of waiting`() = runTest {
        givenConfig(embeddedInApp().copy(delayTime = Milliseconds(7_200_000L)))

        // The resolve itself never waits: the registry owns the delay.
        val result = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals("embedded-id", result?.variant?.inAppId)
        assertEquals(7_200_000L, result?.delayTime?.interval)
    }

    @Test
    fun `selectInAppForPlace hands out no delay once the winner waited it out this session`() = runTest {
        givenConfig(embeddedInApp().copy(delayTime = Milliseconds(7_200_000L)))

        interactor.markEmbeddedDelayWaitedOut(place, "embedded-id")
        val result = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals("embedded-id", result?.variant?.inAppId)
        assertNull(result?.delayTime)
    }

    @Test
    fun `a waited-out delay is per place and per in-app`() = runTest {
        givenConfig(embeddedInApp().copy(delayTime = Milliseconds(7_200_000L)))

        interactor.markEmbeddedDelayWaitedOut("other-place", "embedded-id")
        interactor.markEmbeddedDelayWaitedOut(place, "other-in-app")
        val result = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals(7_200_000L, result?.delayTime?.interval)
    }

    @Test
    fun `selectInAppForPlace skips candidate with directCall`() = runTest {
        givenConfig(embeddedInApp().copy(displayConditions = DisplayConditions.DIRECT_CALL))

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
    }

    @Test
    fun `selectInAppForPlace shows nothing outside the ab pool but still sends its targeting`() = runTest {
        // The cut A/B branch keeps its funnel denominator: no show, yet the offer goes out.
        givenConfig(embeddedInApp())
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns emptySet()

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "embedded-id" }) }
    }

    @Test
    fun `selectInAppForPlace returns candidate inside ab pool`() = runTest {
        givenConfig(embeddedInApp())
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("embedded-id")

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant?.inAppId)
    }

    @Test
    fun `selectInAppForPlace filters by frequency like every other path`() = runTest {
        givenConfig(embeddedInApp())

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant?.inAppId)
        verify(exactly = 1) { frequencyManager.filterInAppsFrequency(any()) }
    }

    @Test
    fun `selectInAppForPlace skips a candidate its frequency already blocks`() = runTest {
        // A lifetime-frequency block that has been shown before is done — exactly like a modal.
        // Its offer still ships: the frequency holds the show back, not the funnel.
        givenConfig(embeddedInApp())
        every { inAppRepository.getShownInApps() } returns mapOf("embedded-id" to listOf(1L))

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace checks the show limits`() = runTest {
        givenConfig(embeddedInApp())

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify { maxInappsPerSessionLimitChecker.check() }
        verify { maxInappsPerDayLimitChecker.check() }
        verify { minIntervalBetweenShowsLimitChecker.check() }
    }

    @Test
    fun `selectInAppForPlace returns nothing when a show limit blocks the winner`() = runTest {
        givenConfig(embeddedInApp())
        every { maxInappsPerSessionLimitChecker.check() } returns false

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
    }

    @Test
    fun `selectInAppForPlace ignores the show limits for a priority in-app`() = runTest {
        givenConfig(embeddedInApp(isPriority = true))
        every { maxInappsPerDayLimitChecker.check() } returns false

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant?.inAppId)
    }

    @Test
    fun `selectInAppForPlace ignores the show limits for an unlimited in-app`() = runTest {
        // The unlimited rule, second half (iOS decision 14.08, mirrored): the stock block
        // config is unlimited, and spent budgets of other in-apps must not empty it.
        givenConfig(
            embeddedInApp().copy(frequency = Frequency(Frequency.Delay.Unlimited))
        )
        every { maxInappsPerSessionLimitChecker.check() } returns false
        every { minIntervalBetweenShowsLimitChecker.check() } returns false

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant?.inAppId)
        verify(exactly = 0) { maxInappsPerSessionLimitChecker.check() }
    }

    @Test
    fun `targeting catch-up never covers directCall in-apps`() = runTest {
        // iOS decision 14.08, mirrored: a direct-call in-app answers no event, so the catch-up must not
        // send Inapp.Targeting for it — that would inflate the funnel on every start. Its
        // targeting ships once, with the explicit show.
        val directCall = modalInApp(id = "direct-call").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        val ordinary = modalInApp(id = "ordinary")
        givenConfig(directCall, ordinary)
        coEvery { inAppRepository.getTargetedInApps() } returns emptyMap()
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs

        interactor.processEventAndConfig().test { cancelAndIgnoreRemainingEvents() }
        val job = launch { interactor.listenToTargetingEvents() }
        advanceUntilIdle()
        job.cancel()

        coVerify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(directCall, any()) }
        coVerify(atLeast = 1) { inAppProcessingManager.sendTargetedInApp(ordinary, any()) }
    }

    @Test
    fun `targeting catch-up never covers in-apps without an overlay variant`() = runTest {
        // A pure-embedded in-app gets its targeting from the place resolve; the catch-up
        // covering it too would double the funnel on every matching operation. A mixed form
        // stays covered — the catch-up speaks for its overlay half.
        val pureEmbedded = embeddedInApp(id = "pure-embedded")
        val mixed = mixedInApp(id = "mixed")
        givenConfig(pureEmbedded, mixed)
        coEvery { inAppRepository.getTargetedInApps() } returns emptyMap()
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs

        interactor.processEventAndConfig().test { cancelAndIgnoreRemainingEvents() }
        val job = launch { interactor.listenToTargetingEvents() }
        advanceUntilIdle()
        job.cancel()

        coVerify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(pureEmbedded, any()) }
        coVerify(atLeast = 1) { inAppProcessingManager.sendTargetedInApp(mixed, any()) }
    }

    @Test
    fun `selectInAppForPlace sends targeting for the winner it hands out`() = runTest {
        givenConfig(embeddedInApp())

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify(exactly = 1) {
            inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "embedded-id" })
        }
    }

    @Test
    fun `selectInAppForPlace sends the winner targeting once per session`() = runTest {
        givenConfig(embeddedInApp())

        val first = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        val second = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals("embedded-id", first?.variant?.inAppId)
        assertEquals("embedded-id", second?.variant?.inAppId)
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace sends the winner targeting even when the show limits block it`() = runTest {
        // Parity with the overlay: the offer is reported before the budgets decide whether it
        // may actually appear — and the blocked pass consumes the winner's offer slot.
        givenConfig(embeddedInApp())
        every { maxInappsPerSessionLimitChecker.check() } returns false

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }

        every { maxInappsPerSessionLimitChecker.check() } returns true
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace sends targeting for the losers once per session and pairs the winner with its slot`() = runTest {
        // Two candidates matched: one wins the place, the loser still keeps its denominator.
        givenConfig(
            embeddedInApp(id = "winner", isPriority = true),
            embeddedInApp(id = "loser"),
        )

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        // The loser goes through the once-per-session set, the winner through the "last
        // targeted" slot: one event each however many times the place re-resolves.
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "winner" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "loser" }) }
    }

    @Test
    fun `a winner that loses a later pass to a stronger candidate is not targeted again`() = runTest {
        givenConfig(
            embeddedInApp(id = "usual"),
            embeddedInApp(id = "stronger", isPriority = true),
        )
        coEvery { inAppProcessingManager.matchesTargeting(match { it.id == "stronger" }, any()) } returns false

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        coEvery { inAppProcessingManager.matchesTargeting(match { it.id == "stronger" }, any()) } returns true
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "usual" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "stronger" }) }
    }

    @Test
    fun `selectInAppForPlace targets the winner again on every change of the winner, 1 to 2 to 1`() = runTest {
        val first = embeddedInApp(id = "first")
        val second = embeddedInApp(id = "second")

        givenConfig(first)
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        givenConfig(second)
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        givenConfig(first)
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        // The slot compares with the last targeted, not with a session set: the return counts.
        verify(exactly = 2) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "first" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "second" }) }
    }

    @Test
    fun `selectInAppForPlace sends no targeting for a candidate whose targeting did not match`() = runTest {
        givenConfig(embeddedInApp(id = "matched"), embeddedInApp(id = "unmatched"))
        coEvery { inAppProcessingManager.matchesTargeting(match { it.id == "unmatched" }, any()) } returns false

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "matched" }) }
        verify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "unmatched" }) }
    }

    @Test
    fun `selectInAppForPlace ships the collected failures only when the place stays empty`() = runTest {
        givenConfig(embeddedInApp())
        coEvery { inAppProcessingManager.matchesTargeting(any(), any()) } returns false

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify(exactly = 1) { inAppFailureTracker.sendCollectedFailures() }
        verify(exactly = 0) { inAppFailureTracker.clearFailures() }
    }

    @Test
    fun `selectInAppForPlace drops the pass failures once somebody won the place`() = runTest {
        // Parity with the overlay pass: a winner — even one the limits then hold back — answers
        // "why nothing was shown", so the buffer of the pass is discarded, not sent.
        givenConfig(embeddedInApp())
        every { maxInappsPerSessionLimitChecker.check() } returns false

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        verify(exactly = 1) { inAppFailureTracker.clearFailures() }
        verify(exactly = 0) { inAppFailureTracker.sendCollectedFailures() }
    }

    @Test
    fun `selectInAppForPlace never evaluates a directCall candidate`() = runTest {
        givenConfig(embeddedInApp(id = "direct").copy(displayConditions = DisplayConditions.DIRECT_CALL))

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        coVerify(exactly = 0) { inAppProcessingManager.matchesTargeting(any(), any()) }
        verify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends targeting for the same id again for a different host`() = runTest {
        // The pair is host + id: the same in-app proposed by two different hosts is two offers.
        givenConfig(modalInApp(id = "inapp-1"))

        interactor.filterShowableInAppIds("host-form", listOf("inapp-1"))
        interactor.filterShowableInAppIds("other-host", listOf("inapp-1"))

        verify(exactly = 2) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `place resolve with an operation trigger dedups with the pull`() = runTest {
        givenConfig(embeddedInApp())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        interactor.selectInAppForPlace(place, triggerEvent = operation)

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace passes the push trigger to the selection`() = runTest {
        givenConfig(embeddedInApp())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))

        interactor.selectInAppForPlace(place, triggerEvent = operation)

        coVerify { inAppProcessingManager.matchesTargeting(any(), operation) }
    }

    @Test
    fun `getInAppToShowById ignores every restriction`() = runTest {
        // directCall, already shown, whatever frequency — a drawn element must open.
        givenConfig(
            modalInApp(id = "restricted").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        )
        every { inAppRepository.getShownInApps() } returns mapOf("restricted" to listOf(1L))

        val content = interactor.getInAppToShowById("restricted")?.variant

        assertEquals(InAppStub.getInApp().form.variants.first(), content)
    }

    @Test
    fun `getInAppToShowById does not check ab pool`() = runTest {
        givenConfig(modalInApp(id = "outside-pool"))
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns emptySet()

        assertEquals(
            InAppStub.getInApp().form.variants.first(),
            interactor.getInAppToShowById("outside-pool")?.variant
        )
        coVerify(exactly = 0) { inAppABTestLogic.getInAppsPool(any()) }
    }

    @Test
    fun `getInAppToShowById does not check show limits or lock`() = runTest {
        givenConfig(modalInApp(id = "known"))

        interactor.getInAppToShowById("known")

        verify { maxInappsPerSessionLimitChecker wasNot Called }
        verify { maxInappsPerDayLimitChecker wasNot Called }
        verify { minIntervalBetweenShowsLimitChecker wasNot Called }
    }

    @Test
    fun `getInAppToShowById returns null for unknown id`() = runTest {
        givenConfig(modalInApp(id = "known"))

        assertNull(interactor.getInAppToShowById("unknown"))
    }

    @Test
    fun `getInAppToShowById returns the owning in-app with its variant`() = runTest {
        val inApp = modalInApp(id = "known")
        givenConfig(inApp)

        val toShow = interactor.getInAppToShowById("known")!!

        assertEquals(inApp, toShow.inApp)
        assertEquals(inApp.form.variants.first(), toShow.variant)
    }

    @Test
    fun `getInAppToShowById returns null for an embedded in-app`() = runTest {
        givenConfig(embeddedInApp(id = "embedded-id"))

        assertNull(interactor.getInAppToShowById("embedded-id"))
    }

    @Test
    fun `getInAppToShowById picks the overlay variant of a mixed form`() = runTest {
        val modal = modalInApp(id = "mixed")
        val embeddedVariant = embeddedInApp(id = "mixed").form.variants.first()
        givenConfig(
            modal.copy(form = Form(variants = listOf(embeddedVariant, modal.form.variants.first())))
        )

        assertEquals(
            modal.form.variants.first(),
            interactor.getInAppToShowById("mixed")?.variant
        )
    }

    @Test
    fun `getInAppToShowById picks first when config has duplicate ids`() = runTest {
        // Ids are not unique across sdkVersion ranges; the section is already version-filtered,
        // so the first match is the right one.
        val first = modalInApp(id = "dup")
        val second = InAppStub.getInApp().copy(
            id = "dup",
            form = Form(variants = listOf(InAppStub.getWebView()))
        )
        givenConfig(first, second)

        assertEquals(first.form.variants.first(), interactor.getInAppToShowById("dup")?.variant)
    }

    @Test
    fun `filterShowableInAppIds picks the first duplicate id like the direct call does`() = runTest {
        // Two in-apps share an id across sdkVersion ranges: the dictionary decision and the tap
        // must talk about the same in-app — the first one.
        givenConfig(modalInApp(id = "dup"), modalInApp(id = "dup"))

        assertEquals(listOf("dup"), interactor.filterShowableInAppIds("host-form", listOf("dup")))
    }

    @Test
    fun `filterShowableInAppIds keeps all valid ids`() = runTest {
        givenConfig(modalInApp(id = "inapp-1"), modalInApp(id = "inapp-2"))

        assertEquals(
            listOf("inapp-1", "inapp-2"),
            interactor.filterShowableInAppIds("host-form", listOf("inapp-1", "inapp-2"))
        )
    }

    @Test
    fun `filterShowableInAppIds cuts unknown id`() = runTest {
        givenConfig(modalInApp(id = "inapp-1"))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds("host-form", listOf("ghost")))
    }

    @Test
    fun `filterShowableInAppIds cuts id with unmatched targeting`() = runTest {
        // An operation node never matches the dictionary answer — no operation is happening.
        givenConfig(
            modalInApp(id = "inapp-1").copy(targeting = InAppStub.getTargetingOperationNode())
        )

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
    }

    @Test
    fun `filterShowableInAppIds fetches the targeting dependencies before checking`() = runTest {
        // A segment-targeted in-app is answerable only from fetched data. The dictionary question is
        // the only path that ever evaluates a directCall in-app's targeting, so it has to fetch
        // for itself — the session status and the repository mutexes keep it one network trip.
        val targeting = mockk<TreeTargeting>()
        coEvery { targeting.fetchTargetingInfo(any()) } just runs
        every { targeting.checkTargeting(any()) } returns true
        givenConfig(modalInApp(id = "inapp-1").copy(targeting = targeting))

        assertEquals(listOf("inapp-1"), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
        coVerify(exactly = 1) { targeting.fetchTargetingInfo(any()) }
    }

    @Test
    fun `filterShowableInAppIds cuts the id whose dependencies could not be fetched`() = runTest {
        // Fail closed: a fetch that failed leaves the targeting unverifiable, and unverified
        // is never "allowed".
        val targeting = mockk<TreeTargeting>()
        coEvery { targeting.fetchTargetingInfo(any()) } throws RuntimeException("offline")
        givenConfig(modalInApp(id = "inapp-1").copy(targeting = targeting))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
        verify(exactly = 0) { targeting.checkTargeting(any()) }
    }

    @Test
    fun `filterShowableInAppIds cuts an id its frequency already blocks`() = runTest {
        // The frequency rule is the same on every selection path: the stub frequency is
        // once/lifetime, and a recorded show exhausts it — the id is not proposed.
        givenConfig(modalInApp(id = "inapp-1"))
        every { inAppRepository.getShownInApps() } returns mapOf("inapp-1" to listOf(1L))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
        // The exhausted frequency holds the id out of the answer, not out of the funnel.
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds keeps an unlimited id regardless of show history`() = runTest {
        givenConfig(
            modalInApp(id = "inapp-1").copy(frequency = Frequency(Frequency.Delay.Unlimited))
        )
        every { inAppRepository.getShownInApps() } returns mapOf("inapp-1" to listOf(1L))

        assertEquals(listOf("inapp-1"), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
    }

    @Test
    fun `filterShowableInAppIds does not check the show limits`() = runTest {
        // The limits belong to the overlay show; the dictionary shows nothing itself.
        givenConfig(modalInApp(id = "inapp-1"))
        every { maxInappsPerSessionLimitChecker.check() } returns false

        assertEquals(listOf("inapp-1"), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
    }

    @Test
    fun `filterShowableInAppIds cuts id of embedded in-app`() = runTest {
        givenConfig(embeddedInApp(id = "embedded-itself"), modalInApp(id = "inapp-1"))

        assertEquals(
            listOf("inapp-1"),
            interactor.filterShowableInAppIds("host-form", listOf("embedded-itself", "inapp-1"))
        )
    }

    @Test
    fun `filterShowableInAppIds cuts id outside ab pool`() = runTest {
        givenConfig(modalInApp(id = "in-pool"), modalInApp(id = "out-of-pool"))
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("in-pool")

        assertEquals(
            listOf("in-pool"),
            interactor.filterShowableInAppIds("host-form", listOf("in-pool", "out-of-pool"))
        )
    }

    @Test
    fun `filterShowableInAppIds does not check directCall`() = runTest {
        // directCall is the standard marker of a dictionary-drawn in-app — checking it would empty the answer.
        givenConfig(
            modalInApp(id = "inapp-1").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        )

        assertEquals(listOf("inapp-1"), interactor.filterShowableInAppIds("host-form", listOf("inapp-1")))
    }

    @Test
    fun `filterShowableInAppIds sends targeting for every asked id that matches, the ab-cut included`() = runTest {
        // The answer is cut by the pool; the offer is not — the cut branch keeps its denominator.
        givenConfig(modalInApp(id = "inapp-1"), modalInApp(id = "inapp-2"), modalInApp(id = "cut"))
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("inapp-1", "inapp-2")

        val answer = interactor.filterShowableInAppIds("host-form", listOf("inapp-1", "inapp-2", "cut"))

        assertEquals(listOf("inapp-1", "inapp-2"), answer)
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "inapp-1" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "inapp-2" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "cut" }) }
        verify(exactly = 3) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends targeting once per host and id pair a session`() = runTest {
        // A repeated answer offers nothing new: one Inapp.Targeting per host|id pair.
        givenConfig(modalInApp(id = "inapp-1"))

        interactor.filterShowableInAppIds("host-form", listOf("inapp-1"))
        interactor.filterShowableInAppIds("host-form", listOf("inapp-1"))

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends one targeting for a duplicated id and mirrors the duplicate`() = runTest {
        // The answer mirrors the request — the page owns its list's shape; the event is one.
        givenConfig(modalInApp(id = "inapp-1"))

        val answer = interactor.filterShowableInAppIds("host-form", listOf("inapp-1", "inapp-1"))

        assertEquals(listOf("inapp-1", "inapp-1"), answer)
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends no targeting for a cut id`() = runTest {
        givenConfig(
            modalInApp(id = "inapp-1").copy(targeting = InAppStub.getTargetingOperationNode())
        )

        interactor.filterShowableInAppIds("host-form", listOf("inapp-1", "ghost"))

        verify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `saveShownInApp sends the Inapp Show and writes the counters`() {
        every { inAppRepository.getCurrentSessionInApps() } returns listOf(modalInApp(id = "modal-1"))

        interactor.saveShownInApp("modal-1", now.ms, "0:0:1", null)

        verify { inAppRepository.sendInAppShown("modal-1", "0:0:1", null) }
        verify { inAppRepository.setInAppShown("modal-1") }
        verify { inAppRepository.saveShownInApp("modal-1", now.ms) }
        verify { inAppRepository.saveInAppStateChangeTime(now) }
    }

    @Test
    fun `saveShownInApp sends the Inapp Show but writes no counters for unlimited`() {
        every { inAppRepository.getCurrentSessionInApps() } returns listOf(
            modalInApp(id = "modal-1").copy(frequency = Frequency(Frequency.Delay.Unlimited))
        )

        interactor.saveShownInApp("modal-1", now.ms, "0:0:1", null)

        verify { inAppRepository.sendInAppShown("modal-1", "0:0:1", null) }
        verify(exactly = 0) { inAppRepository.setInAppShown(any()) }
        verify(exactly = 0) { inAppRepository.saveShownInApp(any(), any()) }
        // Unlimited is outside the show accounting in both directions: the cooldown stays put too.
        verify(exactly = 0) { inAppRepository.saveInAppStateChangeTime(any()) }
    }

    @Test
    fun `saveInAppDismissTime moves the cooldown for a counted in-app`() {
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(0L)

        interactor.saveInAppDismissTime(modalInApp(id = "modal-1"))

        verify { inAppRepository.saveInAppStateChangeTime(now) }
    }

    @Test
    fun `saveInAppDismissTime leaves the cooldown alone for unlimited`() {
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(0L)

        interactor.saveInAppDismissTime(
            modalInApp(id = "modal-1").copy(frequency = Frequency(Frequency.Delay.Unlimited))
        )

        verify(exactly = 0) { inAppRepository.saveInAppStateChangeTime(any()) }
    }

    @Test
    fun `recordBlockShow sends the Inapp Show, counts the show and moves the cooldown`() {
        interactor.recordBlockShow(place, "embedded-id", InAppStub.getInApp().frequency, Milliseconds(1_500L), mapOf("a" to "b"))

        verify { inAppRepository.sendInAppShown("embedded-id", "00:00:01.5000000", mapOf("a" to "b")) }
        verify { inAppRepository.setInAppShown("embedded-id") }
        verify { inAppRepository.saveShownInApp("embedded-id", now.ms) }
        // Parity in both directions: a counted block show also moves the shared cooldown.
        verify { inAppRepository.saveInAppStateChangeTime(now) }
    }

    @Test
    fun `recordBlockShow stays silent while the place slot holds the same content`() {
        // A rotation or a recreated page draws the same content again — no second pair, no
        // second count. A changed in-app writes the slot over and speaks again.
        interactor.recordBlockShow(place, "embedded-id", InAppStub.getInApp().frequency, Milliseconds(1_000L), null)
        interactor.recordBlockShow(place, "embedded-id", InAppStub.getInApp().frequency, Milliseconds(1_000L), null)

        verify(exactly = 1) { inAppRepository.sendInAppShown(any(), any(), any()) }
        verify(exactly = 1) { inAppRepository.setInAppShown(any()) }

        interactor.recordBlockShow(place, "embedded-2", InAppStub.getInApp().frequency, Milliseconds(1_000L), null)
        interactor.recordBlockShow(place, "embedded-id", InAppStub.getInApp().frequency, Milliseconds(1_000L), null)

        // 1 -> 2 -> 1 is three shows: the slot compares with the last shown, not a session set.
        verify(exactly = 3) { inAppRepository.sendInAppShown(any(), any(), any()) }
    }

    @Test
    fun `recordBlockShow sends the Inapp Show for an unlimited block that writes no counters`() {
        interactor.recordBlockShow(place, "embedded-id", Frequency(Frequency.Delay.Unlimited), Milliseconds(0L), null)

        verify { inAppRepository.sendInAppShown(any(), any(), any()) }
        verify(exactly = 0) { inAppRepository.setInAppShown(any()) }
        verify(exactly = 0) { inAppRepository.saveShownInApp(any(), any()) }
        verify(exactly = 0) { inAppRepository.saveInAppStateChangeTime(any()) }
    }

    @Test
    fun `recordBlockShow needs no config - the snapshot carries everything`() {
        // The config may have moved on since the resolve — the user still saw this content.
        coEvery { mobileConfigRepository.getInAppsSection() } returns emptyList()

        interactor.recordBlockShow(place, "gone-from-config", InAppStub.getInApp().frequency, Milliseconds(0L), null)

        verify(exactly = 1) { inAppRepository.sendInAppShown("gone-from-config", any(), any()) }
    }

    @Test
    fun `live operation matched to an embedded place emits a place event`() = runTest {
        val embedded = embeddedInApp()
        givenConfig(embedded)
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        every { inAppRepository.listenLiveInAppEvents() } returns flowOf(operation)
        every { inAppRepository.getOperationalInAppsByOperation(operation.name) } returns listOf(embedded)

        interactor.listenEmbeddedPlaceEvents().test {
            val placeEvent = awaitItem()
            assertEquals(place, placeEvent.placeSystemName)
            assertEquals(operation, placeEvent.triggerEvent)
            awaitComplete()
        }
        // No resolve happens on the domain side — the controller runs it through its dedup.
        coVerify(exactly = 0) { inAppProcessingManager.chooseInAppToShow(any(), any(), any()) }
    }

    @Test
    fun `operation with no embedded candidates emits nothing`() = runTest {
        givenConfig(embeddedInApp())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("plain-operation"))
        every { inAppRepository.listenLiveInAppEvents() } returns flowOf(operation)
        every { inAppRepository.getOperationalInAppsByOperation(operation.name) } returns emptyList()

        interactor.listenEmbeddedPlaceEvents().test {
            awaitComplete()
        }
        coVerify(exactly = 0) { inAppProcessingManager.chooseInAppToShow(any(), any(), any()) }
    }

    @Test
    fun `overlay path cuts embedded-only and directCall but keeps a mixed form`() = runTest {
        // The step of the plan that touches the common show path: the event chain gets the two
        // new filters, the ordinary in-app still goes through, and a mixed form survives thanks
        // to its overlay variant (in sync with iOS).
        val embedded = embeddedInApp()
        val direct = modalInApp(id = "direct").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        val ordinary = modalInApp(id = "ordinary")
        val mixed = mixedInApp()
        givenConfig(embedded, direct, ordinary, mixed)
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)

        interactor.processEventAndConfig().test {
            awaitItem()
            awaitComplete()
        }

        coVerify {
            inAppProcessingManager.chooseInAppToShow(listOf(ordinary, mixed), InAppEventType.AppStartup, any())
        }
    }

    @Test
    fun `selectInAppForPlace hands out the embedded variant of a mixed form`() = runTest {
        givenConfig(mixedInApp())

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.variant

        assertEquals("mixed-id", content?.inAppId)
        assertEquals(place, content?.placeSystemName)
    }

    @Test
    fun `filterShowableInAppIds keeps an id whose form also has an overlay variant`() = runTest {
        givenConfig(mixedInApp())

        val result = interactor.filterShowableInAppIds("host-form", listOf("mixed-id"))

        assertEquals(listOf("mixed-id"), result)
    }
}
