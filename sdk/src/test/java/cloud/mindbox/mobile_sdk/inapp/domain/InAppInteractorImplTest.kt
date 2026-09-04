package cloud.mindbox.mobile_sdk.inapp.domain

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppTargetingErrorRepository
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
    private lateinit var showBudgetManager: ShowBudgetManager

    @RelaxedMockK
    private lateinit var timeProvider: TimeProvider

    @RelaxedMockK
    private lateinit var inAppGeoRepository: InAppGeoRepository

    @RelaxedMockK
    private lateinit var inAppSegmentationRepository: InAppSegmentationRepository

    @RelaxedMockK
    private lateinit var inAppTargetingErrorRepository: InAppTargetingErrorRepository

    @RelaxedMockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @MockK
    private lateinit var inAppContentFetcher: InAppContentFetcher

    private lateinit var interactor: InAppInteractor

    @RelaxedMockK
    private lateinit var inAppFailureTracker: InAppFailureTracker

    @RelaxedMockK
    private lateinit var featureToggleManager: FeatureToggleManager

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
            showBudgetManager,
            timeProvider,
            sessionStorageManager,
            inAppFailureTracker
        )

        coEvery { mobileConfigRepository.getInAppsSection() } returns emptyList()
        every { inAppRepository.listenInAppEvents() } returns flowOf(InAppEventType.AppStartup)
        every { inAppEventManager.isValidInAppEvent(any()) } returns true
    }

    @Test
    fun `processEventAndConfig returns correct inapp for several events`() = runTest {
        val eventFlow = MutableSharedFlow<InAppEventType>()

        val nonPriorityInApp = InAppStub.getInApp().copy(
            id = "nonPriorityInapp1",
            isPriority = false,
            targeting = InAppStub.getTargetingTrueNode().copy("true"),
            form = InAppStub.getInApp().form.copy(
                variants = listOf(
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
                variants = listOf(
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
                variants = listOf(
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
                variants = listOf(
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
            inAppTargetingErrorRepository,
            inAppContentFetcher,
            inAppRepository,
            inAppFailureTracker,
            featureToggleManager
        )

        interactor = InAppInteractorImpl(
            mobileConfigRepository,
            inAppRepository,
            inAppFilteringManager,
            inAppEventManager,
            realProcessingManager,
            inAppABTestLogic,
            inAppFrequencyManager,
            showBudgetManager,
            timeProvider,
            sessionStorageManager,
            inAppFailureTracker
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
            assertEquals(priorityInApp, firstItem.first)

            eventFlow.emit(InAppEventType.AppStartup)
            val secondItem = awaitItem()
            assertEquals(priorityInAppTwo, secondItem.first)

            eventFlow.emit(InAppEventType.AppStartup)
            val thirdItem = awaitItem()
            assertEquals(nonPriorityInApp, thirdItem.first)

            eventFlow.emit(InAppEventType.AppStartup)
            val fourthItem = awaitItem()
            assertEquals(nonPriorityInAppTwo, fourthItem.first)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event winner sends its targeting and records the event`() = runTest {
        val winner = InAppStub.getInApp().copy(
            id = "winner",
            targeting = InAppStub.getTargetingTrueNode(),
            form = InAppStub.getInApp().form.copy(
                variants = listOf(InAppStub.getModalWindow().copy(inAppId = "winner"))
            )
        )
        coEvery { mobileConfigRepository.getInAppsSection() } returns listOf(winner)
        every { inAppFilteringManager.filterUnShownInAppsByEvent(any(), any()) } returns listOf(winner)
        every { inAppFilteringManager.filterOutNonOverlayInApps(any()) } answers { firstArg() }
        every { inAppFilteringManager.filterOutDirectCallInApps(any()) } answers { firstArg() }
        coEvery { inAppFrequencyManager.filterInAppsFrequency(any()) } answers { firstArg() }
        coEvery { inAppProcessingManager.chooseInAppToShow(any(), any(), any()) } returns winner
        coEvery { inAppProcessingManager.sendTargetedInApp(any(), any()) } just runs

        interactor.processEventAndConfig().test {
            awaitItem()
            coVerify(exactly = 1) {
                inAppProcessingManager.sendTargetedInApp(winner, InAppEventType.AppStartup)
            }
            verify(exactly = 1) {
                inAppRepository.saveTargetedInAppWithEvent(winner.id, InAppEventType.AppStartup.hashCode())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
