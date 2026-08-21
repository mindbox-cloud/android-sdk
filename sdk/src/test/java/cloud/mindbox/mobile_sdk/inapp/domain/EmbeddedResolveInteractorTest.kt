package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
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
 * future direct call, and the feed answer to `filterShowableInapps`. Presentation limits and
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
        )
        every { timeProvider.currentTimestamp() } returns now
        every { inAppRepository.getShownInApps() } returns emptyMap()
        every { inAppProcessingManager.sendTargetedInApp(any()) } just runs
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs
        every { sessionStorageManager.placeTargetingReportedInSession } returns ConcurrentHashMap.newKeySet()
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

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

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

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals("priority", content?.inAppId)
    }

    @Test
    fun `selectInAppForPlace does not delay content when winner has delayTime`() = runTest {
        givenConfig(embeddedInApp().copy(delayTime = Milliseconds(7_200_000L)))

        // The content comes back right away — there is nothing to wait with on the pull path.
        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.inAppId)
    }

    @Test
    fun `selectInAppForPlace skips candidate with directCall`() = runTest {
        givenConfig(embeddedInApp().copy(displayConditions = DisplayConditions.DIRECT_CALL))

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
    }

    @Test
    fun `selectInAppForPlace skips candidate outside ab pool`() = runTest {
        givenConfig(embeddedInApp())
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns emptySet()

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
    }

    @Test
    fun `selectInAppForPlace returns candidate inside ab pool`() = runTest {
        givenConfig(embeddedInApp())
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("embedded-id")

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.inAppId)
    }

    @Test
    fun `selectInAppForPlace filters by frequency like every other path`() = runTest {
        givenConfig(embeddedInApp())

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.inAppId)
        verify(exactly = 1) { frequencyManager.filterInAppsFrequency(any()) }
    }

    @Test
    fun `selectInAppForPlace skips a candidate its frequency already blocks`() = runTest {
        // A lifetime-frequency block that has been shown before is done — exactly like a modal.
        givenConfig(embeddedInApp())
        every { inAppRepository.getShownInApps() } returns mapOf("embedded-id" to listOf(1L))

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
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

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.inAppId)
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

        assertEquals("embedded-id", interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))?.inAppId)
        verify(exactly = 0) { maxInappsPerSessionLimitChecker.check() }
    }

    @Test
    fun `targeting catch-up never covers directCall in-apps`() = runTest {
        // iOS decision 14.08, mirrored: a story answers no event, so the catch-up must not
        // send Inapp.Targeting for it — that would inflate the funnel on every start. Its
        // targeting ships once, with the explicit show.
        val story = modalInApp(id = "story").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        val ordinary = modalInApp(id = "ordinary")
        givenConfig(story, ordinary)
        coEvery { inAppRepository.getTargetedInApps() } returns emptyMap()
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs

        interactor.processEventAndConfig().test { cancelAndIgnoreRemainingEvents() }
        val job = launch { interactor.listenToTargetingEvents() }
        advanceUntilIdle()
        job.cancel()

        coVerify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(story, any()) }
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

        assertEquals("embedded-id", first?.inAppId)
        assertEquals("embedded-id", second?.inAppId)
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace sends no targeting for a winner the show limits block`() = runTest {
        givenConfig(embeddedInApp())
        every { maxInappsPerSessionLimitChecker.check() } returns false

        assertNull(interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)))
        verify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(any()) }

        every { maxInappsPerSessionLimitChecker.check() } returns true
        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `place resolve with an operation trigger dedups with the pull`() = runTest {
        givenConfig(embeddedInApp())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))

        interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))
        interactor.selectInAppForPlace(place, triggerEvent = operation)

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `selectInAppForPlace passes the push trigger to the selection`() = runTest {
        givenConfig(embeddedInApp())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))

        interactor.selectInAppForPlace(place, triggerEvent = operation)

        coVerify { inAppProcessingManager.chooseInAppToShow(any(), operation, any()) }
    }

    @Test
    fun `getInAppToShowById ignores every restriction`() = runTest {
        // directCall, already shown, whatever frequency — a drawn circle must open.
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
        // Two in-apps share an id across sdkVersion ranges: the circle decision and the tap
        // must talk about the same in-app — the first one.
        givenConfig(modalInApp(id = "dup"), modalInApp(id = "dup"))

        assertEquals(listOf("dup"), interactor.filterShowableInAppIds(listOf("dup")))
    }

    @Test
    fun `filterShowableInAppIds keeps all valid ids`() = runTest {
        givenConfig(modalInApp(id = "story-1"), modalInApp(id = "story-2"))

        assertEquals(
            listOf("story-1", "story-2"),
            interactor.filterShowableInAppIds(listOf("story-1", "story-2"))
        )
    }

    @Test
    fun `filterShowableInAppIds cuts unknown id`() = runTest {
        givenConfig(modalInApp(id = "story-1"))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds(listOf("ghost")))
    }

    @Test
    fun `filterShowableInAppIds cuts id with unmatched targeting`() = runTest {
        // An operation node never matches the feed answer — no operation is happening.
        givenConfig(
            modalInApp(id = "story-1").copy(targeting = InAppStub.getTargetingOperationNode())
        )

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds(listOf("story-1")))
    }

    @Test
    fun `filterShowableInAppIds fetches the targeting dependencies before checking`() = runTest {
        // A segment-targeted story is answerable only from fetched data. The feed question is
        // the only path that ever evaluates a directCall story's targeting, so it has to fetch
        // for itself — the session status and the repository mutexes keep it one network trip.
        val targeting = mockk<TreeTargeting>()
        coEvery { targeting.fetchTargetingInfo(any()) } just runs
        every { targeting.checkTargeting(any()) } returns true
        givenConfig(modalInApp(id = "story-1").copy(targeting = targeting))

        assertEquals(listOf("story-1"), interactor.filterShowableInAppIds(listOf("story-1")))
        coVerify(exactly = 1) { targeting.fetchTargetingInfo(any()) }
    }

    @Test
    fun `filterShowableInAppIds cuts the id whose dependencies could not be fetched`() = runTest {
        // Fail closed: a fetch that failed leaves the targeting unverifiable, and unverified
        // is never "allowed".
        val targeting = mockk<TreeTargeting>()
        coEvery { targeting.fetchTargetingInfo(any()) } throws RuntimeException("offline")
        givenConfig(modalInApp(id = "story-1").copy(targeting = targeting))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds(listOf("story-1")))
        verify(exactly = 0) { targeting.checkTargeting(any()) }
    }

    @Test
    fun `filterShowableInAppIds cuts an id its frequency already blocks`() = runTest {
        // The frequency rule is the same on every selection path: the stub frequency is
        // once/lifetime, and a recorded show exhausts it — the circle is not proposed.
        givenConfig(modalInApp(id = "story-1"))
        every { inAppRepository.getShownInApps() } returns mapOf("story-1" to listOf(1L))

        assertEquals(emptyList<String>(), interactor.filterShowableInAppIds(listOf("story-1")))
        verify(exactly = 0) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds keeps an unlimited id regardless of show history`() = runTest {
        givenConfig(
            modalInApp(id = "story-1").copy(frequency = Frequency(Frequency.Delay.Unlimited))
        )
        every { inAppRepository.getShownInApps() } returns mapOf("story-1" to listOf(1L))

        assertEquals(listOf("story-1"), interactor.filterShowableInAppIds(listOf("story-1")))
    }

    @Test
    fun `filterShowableInAppIds does not check the show limits`() = runTest {
        // The limits belong to the overlay show; the feed shows nothing itself.
        givenConfig(modalInApp(id = "story-1"))
        every { maxInappsPerSessionLimitChecker.check() } returns false

        assertEquals(listOf("story-1"), interactor.filterShowableInAppIds(listOf("story-1")))
    }

    @Test
    fun `filterShowableInAppIds cuts id of embedded in-app`() = runTest {
        givenConfig(embeddedInApp(id = "feed-itself"), modalInApp(id = "story-1"))

        assertEquals(
            listOf("story-1"),
            interactor.filterShowableInAppIds(listOf("feed-itself", "story-1"))
        )
    }

    @Test
    fun `filterShowableInAppIds cuts id outside ab pool`() = runTest {
        givenConfig(modalInApp(id = "in-pool"), modalInApp(id = "out-of-pool"))
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("in-pool")

        assertEquals(
            listOf("in-pool"),
            interactor.filterShowableInAppIds(listOf("in-pool", "out-of-pool"))
        )
    }

    @Test
    fun `filterShowableInAppIds does not check directCall`() = runTest {
        // directCall is the standard marker of a story — checking it would empty the feed.
        givenConfig(
            modalInApp(id = "story-1").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        )

        assertEquals(listOf("story-1"), interactor.filterShowableInAppIds(listOf("story-1")))
    }

    @Test
    fun `filterShowableInAppIds sends targeting for every allowed id`() = runTest {
        givenConfig(modalInApp(id = "story-1"), modalInApp(id = "story-2"), modalInApp(id = "cut"))
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns setOf("story-1", "story-2")

        interactor.filterShowableInAppIds(listOf("story-1", "story-2", "cut"))

        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "story-1" }) }
        verify(exactly = 1) { inAppProcessingManager.sendTargetedInApp(match<InApp> { it.id == "story-2" }) }
        verify(exactly = 2) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends targeting again on every answer`() = runTest {
        // The funnel counts the proposed circles: no dedup, every answer sends again.
        givenConfig(modalInApp(id = "story-1"))

        interactor.filterShowableInAppIds(listOf("story-1"))
        interactor.filterShowableInAppIds(listOf("story-1"))

        verify(exactly = 2) { inAppProcessingManager.sendTargetedInApp(any()) }
    }

    @Test
    fun `filterShowableInAppIds sends no targeting for a cut id`() = runTest {
        givenConfig(
            modalInApp(id = "story-1").copy(targeting = InAppStub.getTargetingOperationNode())
        )

        interactor.filterShowableInAppIds(listOf("story-1", "ghost"))

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
    fun `recordBlockShow sends the Inapp Show and counts the show`() = runTest {
        givenConfig(embeddedInApp())
        every { sessionStorageManager.blockShowsReportedInSession } returns mutableSetOf()

        interactor.recordBlockShow("embedded-id", Milliseconds(1_500L), mapOf("a" to "b"))

        verify { inAppRepository.sendInAppShown("embedded-id", "00:00:01.5000000", mapOf("a" to "b")) }
        verify { inAppRepository.setInAppShown("embedded-id") }
    }

    @Test
    fun `recordBlockShow sends the Inapp Show once per session`() = runTest {
        // A rotation or a return to the screen draws the same content again — one show per session.
        givenConfig(embeddedInApp())
        every { sessionStorageManager.blockShowsReportedInSession } returns mutableSetOf()

        interactor.recordBlockShow("embedded-id", Milliseconds(1_000L), null)
        interactor.recordBlockShow("embedded-id", Milliseconds(1_000L), null)

        verify(exactly = 1) { inAppRepository.sendInAppShown(any(), any(), any()) }
    }

    @Test
    fun `recordBlockShow sends the Inapp Show for an unlimited block that writes no counters`() = runTest {
        givenConfig(embeddedInApp().copy(frequency = Frequency(Frequency.Delay.Unlimited)))
        every { sessionStorageManager.blockShowsReportedInSession } returns mutableSetOf()

        interactor.recordBlockShow("embedded-id", Milliseconds(0L), null)

        verify { inAppRepository.sendInAppShown(any(), any(), any()) }
        verify(exactly = 0) { inAppRepository.setInAppShown(any()) }
        verify(exactly = 0) { inAppRepository.saveShownInApp(any(), any()) }
    }

    @Test
    fun `recordBlockShow ignores an unknown id`() = runTest {
        givenConfig(embeddedInApp())

        interactor.recordBlockShow("ghost", Milliseconds(0L), null)

        verify(exactly = 0) { inAppRepository.sendInAppShown(any(), any(), any()) }
    }

    @Test
    fun `live operation matched to an embedded place emits a place event`() = runTest {
        val embedded = embeddedInApp()
        givenConfig(embedded)
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
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

        val content = interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place))

        assertEquals("mixed-id", content?.inAppId)
        assertEquals(place, content?.placeSystemName)
    }

    @Test
    fun `filterShowableInAppIds keeps an id whose form also has an overlay variant`() = runTest {
        givenConfig(mixedInApp())

        val result = interactor.filterShowableInAppIds(listOf("mixed-id"))

        assertEquals(listOf("mixed-id"), result)
    }
}
