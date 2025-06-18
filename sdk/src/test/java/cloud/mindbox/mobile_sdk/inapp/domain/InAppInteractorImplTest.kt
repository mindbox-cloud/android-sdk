package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class InAppInteractorImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mobileConfigRepository: MobileConfigRepository

    @RelaxedMockK
    private lateinit var inAppRepository: InAppRepository

    @RelaxedMockK
    private lateinit var inAppFilteringManager: InAppFilteringManager

    @MockK
    private lateinit var inAppEventManager: InAppEventManager

    @MockK
    private lateinit var inAppProcessingManager: InAppProcessingManager

    @RelaxedMockK
    private lateinit var inAppABTestLogic: InAppABTestLogic

    @RelaxedMockK
    private lateinit var inAppFrequencyManager: InAppFrequencyManager

    @MockK
    private lateinit var maxInappsPerSessionLimitChecker: Checker

    @MockK
    private lateinit var maxInappsPerDayLimitChecker: Checker

    @MockK
    private lateinit var minIntervalBetweenShowsLimitChecker: Checker

    @MockK
    private lateinit var timeProvider: TimeProvider

    @RelaxedMockK
    private lateinit var inAppGeoRepository: InAppGeoRepository

    @RelaxedMockK
    private lateinit var inAppSegmentationRepository: InAppSegmentationRepository

    @MockK
    private lateinit var inAppContentFetcher: InAppContentFetcher

    private lateinit var interactor: InAppInteractor

    @Before
    fun setup() {
        interactor = InAppInteractorImpl(
            mobileConfigRepository,
            inAppRepository,
            inAppFilteringManager,
            inAppEventManager,
            inAppProcessingManager,
            inAppABTestLogic,
            inAppFrequencyManager,
            maxInappsPerSessionLimitChecker,
            maxInappsPerDayLimitChecker,
            minIntervalBetweenShowsLimitChecker,
            timeProvider
        )

        coEvery { mobileConfigRepository.getInAppsSection() } returns emptyList()
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        every { inAppEventManager.isValidInAppEvent(any()) } returns true
    }

    @Test
    fun `priority in-app should pass filter when limits are exceeded`() = runTest {
        val priorityInApp = InAppStub
            .getInApp()
            .copy(
                id = "priorityId",
                isPriority = true,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(
                        InAppStub.getModalWindow().copy(
                            inAppId = "priorityId"
                        )
                    )
                )
            )
        coEvery { inAppProcessingManager.chooseInAppToShow(any(), any()) } returns priorityInApp
        every { maxInappsPerSessionLimitChecker.check() } returns false

        interactor.processEventAndConfig().test {
            val item = awaitItem()
            assertEquals(priorityInApp.form.variants.first(), item)
            awaitComplete()
        }

        verify(exactly = 0) { maxInappsPerSessionLimitChecker.check() }
        verify(exactly = 0) { maxInappsPerDayLimitChecker.check() }
        verify(exactly = 0) { minIntervalBetweenShowsLimitChecker.check() }
    }

    @Test
    fun `non-priority in-app should pass filter when limits are not exceeded`() = runTest {
        val nonPriorityInApp = InAppStub
            .getInApp()
            .copy(
                id = "nonPriorityId",
                isPriority = false,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(
                        InAppStub.getModalWindow().copy(
                            inAppId = "nonPriorityId"
                        )
                    )
                )
            )
        coEvery { inAppProcessingManager.chooseInAppToShow(any(), any()) } returns nonPriorityInApp
        every { maxInappsPerSessionLimitChecker.check() } returns true
        every { maxInappsPerDayLimitChecker.check() } returns true
        every { minIntervalBetweenShowsLimitChecker.check() } returns true

        interactor.processEventAndConfig().test {
            val item = awaitItem()
            assertEquals(nonPriorityInApp.form.variants.first(), item)
            awaitComplete()
        }

        verify(exactly = 1) { maxInappsPerSessionLimitChecker.check() }
        verify(exactly = 1) { maxInappsPerDayLimitChecker.check() }
        verify(exactly = 1) { minIntervalBetweenShowsLimitChecker.check() }
    }

    @Test
    fun `non-priority in-app should be filtered out when limits are exceeded`() = runTest {
        val nonPriorityInApp = InAppStub.getInApp().copy(isPriority = false)
        coEvery { inAppProcessingManager.chooseInAppToShow(any(), any()) } returns nonPriorityInApp
        every { maxInappsPerSessionLimitChecker.check() } returns true
        every { maxInappsPerDayLimitChecker.check() } returns false
        every { minIntervalBetweenShowsLimitChecker.check() } returns true

        interactor.processEventAndConfig().test {
            expectNoEvents()
        }

        verify(exactly = 1) { maxInappsPerSessionLimitChecker.check() }
        verify(exactly = 1) { maxInappsPerDayLimitChecker.check() }
        verify(exactly = 0) { minIntervalBetweenShowsLimitChecker.check() }
    }

    @Test
    fun `processEventAndConfig returns correct inapp for several events`() = runTest {
        val eventFlow = MutableSharedFlow<InAppEventType>()

        val nonPriorityInApp = InAppStub.getInApp().copy(
            id = "nonPriorityInapp1",
            isPriority = false,
            targeting = InAppStub.getTargetingTrueNode().copy("true"),
            form = InAppStub.getInApp().form.copy(
                listOf(
                    InAppStub.getModalWindow().copy(
                        inAppId = "nonPriorityInapp1"
                    )
                )
            )
        )
        val priorityInApp = InAppStub.getInApp().copy(
            id = "priorityInapp",
            isPriority = true,
            targeting = InAppStub.getTargetingTrueNode().copy("true"),
            form = InAppStub.getInApp().form.copy(
                listOf(
                    InAppStub.getModalWindow().copy(
                        inAppId = "priorityInapp"
                    )
                )
            )
        )

        val priorityInAppTwo = InAppStub.getInApp().copy(
            id = "priorityInapp2",
            isPriority = true,
            targeting = InAppStub.getTargetingTrueNode().copy("true"),
            form = InAppStub.getInApp().form.copy(
                listOf(
                    InAppStub.getModalWindow().copy(
                        inAppId = "priorityInapp2"
                    )
                )
            )
        )
        val nonPriorityInAppTwo = InAppStub.getInApp().copy(
            id = "nonPriorityInApp2",
            isPriority = false,
            targeting = InAppStub.getTargetingTrueNode().copy("true"),
            form = InAppStub.getInApp().form.copy(
                listOf(
                    InAppStub.getModalWindow().copy(
                        inAppId = "nonPriorityInApp2"
                    )
                )
            )
        )

        val inAppsFromConfig = listOf(nonPriorityInApp, priorityInApp, priorityInAppTwo, nonPriorityInAppTwo)
        val listAfterFirstEvent = listOf(nonPriorityInApp, priorityInAppTwo, nonPriorityInAppTwo)
        val listAfterSecondEvent = listOf(nonPriorityInApp, nonPriorityInAppTwo)
        val listAfterThirdEvent = listOf(nonPriorityInAppTwo)

        val realProcessingManager = InAppProcessingManagerImpl(
            inAppGeoRepository,
            inAppSegmentationRepository,
            inAppContentFetcher,
            inAppRepository
        )

        interactor = InAppInteractorImpl(
            mobileConfigRepository,
            inAppRepository,
            inAppFilteringManager,
            inAppEventManager,
            realProcessingManager,
            inAppABTestLogic,
            inAppFrequencyManager,
            maxInappsPerSessionLimitChecker,
            maxInappsPerDayLimitChecker,
            minIntervalBetweenShowsLimitChecker,
            timeProvider
        )

        coEvery { mobileConfigRepository.getInAppsSection() } returns inAppsFromConfig
        coEvery { inAppABTestLogic.getInAppsPool(any()) } returns inAppsFromConfig.map { it.id }.toSet()
        coEvery { inAppFilteringManager.filterABTestsInApps(any(), any()) } returns inAppsFromConfig
        coEvery { inAppFilteringManager.filterUnShownInAppsByEvent(any(), any()) } returns inAppsFromConfig
        coEvery { inAppFrequencyManager.filterInAppsFrequency(any()) } returns inAppsFromConfig andThenAnswer {
            listAfterFirstEvent
        } andThenAnswer {
            listAfterSecondEvent
        } andThenAnswer {
            listAfterThirdEvent
        }
        coEvery { inAppContentFetcher.fetchContent(any(), any()) } returns true
        every { inAppRepository.listenInAppEvents() } returns eventFlow

        interactor.processEventAndConfig().test {
            eventFlow.emit(InAppEventType.AppStartup)
            val firstItem = awaitItem()
            assertEquals(priorityInApp.form.variants.first(), firstItem)

            eventFlow.emit(InAppEventType.AppStartup)
            val secondItem = awaitItem()
            assertEquals(priorityInAppTwo.form.variants.first(), secondItem)

            eventFlow.emit(InAppEventType.AppStartup)
            val thirdItem = awaitItem()
            assertEquals(nonPriorityInApp.form.variants.first(), thirdItem)

            eventFlow.emit(InAppEventType.AppStartup)
            val fourthItem = awaitItem()
            assertEquals(nonPriorityInAppTwo.form.variants.first(), fourthItem)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
