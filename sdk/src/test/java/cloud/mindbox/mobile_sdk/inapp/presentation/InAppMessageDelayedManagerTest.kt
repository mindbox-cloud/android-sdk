package cloud.mindbox.mobile_sdk.inapp.presentation

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppMessageDelayedManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val timeProvider: TimeProvider = mockk()
    private val inAppMessageDelayedManager = InAppMessageDelayedManager(timeProvider, testDispatcher)

    @Test
    fun `process should add in-app to queue and schedule processing`() = runTest(testDispatcher.scheduler) {
        val inApp = InAppStub.getInApp().copy(delayTime = Milliseconds(10000))
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }

        inAppMessageDelayedManager.process(inApp, Milliseconds(0L))
        inAppMessageDelayedManager.inAppToShowFlow.test {
            advanceTimeBy(10_001)
            assertEquals(inApp, awaitItem().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processQueue should emit in-app when its time has come`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inApp = InAppStub.getInApp().copy(delayTime = Milliseconds(10000))

        inAppMessageDelayedManager.process(inApp, Milliseconds(0L))

        inAppMessageDelayedManager.inAppToShowFlow.test {
            advanceTimeBy(9_999)
            expectNoEvents()
            advanceTimeBy(1)
            assertEquals(inApp, awaitItem().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearSession should cancel pending jobs and clear queue`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inApp = InAppStub.getInApp().copy(delayTime = Milliseconds(10000))
        inAppMessageDelayedManager.process(inApp, Milliseconds(0L))
        inAppMessageDelayedManager.clearSession()
        inAppMessageDelayedManager.inAppToShowFlow.test {
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `showCandidate selection - earlier showTime wins`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inAppOne = InAppStub.getInApp().copy(id = "inApp1", delayTime = Milliseconds(10000))
        val inAppTwo = InAppStub.getInApp().copy(id = "inApp2", delayTime = Milliseconds(5000), isPriority = true)

        inAppMessageDelayedManager.process(inAppOne, Milliseconds(0L))
        inAppMessageDelayedManager.process(inAppTwo, Milliseconds(0L))

        inAppMessageDelayedManager.inAppToShowFlow.test {
            assertEquals(inAppTwo, awaitItem().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showCandidate selection - same showTime, priority wins`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inAppNonPriority = InAppStub.getInApp().copy(id = "inApp1", delayTime = Milliseconds(5000))
        val inAppPriority = InAppStub.getInApp().copy(id = "inApp2", delayTime = Milliseconds(5000), isPriority = true)

        inAppMessageDelayedManager.process(inAppNonPriority, Milliseconds(0L))
        inAppMessageDelayedManager.process(inAppPriority, Milliseconds(0L))

        inAppMessageDelayedManager.inAppToShowFlow.test {
            assertEquals(inAppPriority, awaitItem().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `winner selection - same showTime and priority, earlier sequenceNumber wins`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inAppFirst = InAppStub.getInApp().copy(id = "inApp1", delayTime = Milliseconds(5000), isPriority = true)
        val inAppSecond = InAppStub.getInApp().copy(id = "inApp2", delayTime = Milliseconds(5000), isPriority = true)

        inAppMessageDelayedManager.process(inAppFirst, Milliseconds(0L))
        inAppMessageDelayedManager.process(inAppSecond, Milliseconds(0L))

        inAppMessageDelayedManager.inAppToShowFlow.test {
            assertEquals(inAppFirst, awaitItem().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processQueue should discard other ready in-apps after a showCandidate is chosen`() = runTest(testDispatcher.scheduler) {
        every { timeProvider.currentTimeMillis() } answers { testDispatcher.scheduler.currentTime }
        val inAppWinner = InAppStub.getInApp().copy(id = "winner", delayTime = Milliseconds(5000), isPriority = true)
        val inAppLoser1 = InAppStub.getInApp().copy(id = "loser1", delayTime = Milliseconds(5000), isPriority = false)
        val inAppLoser2 = InAppStub.getInApp().copy(id = "loser2", delayTime = Milliseconds(3000), isPriority = false)

        inAppMessageDelayedManager.process(inAppWinner, Milliseconds(0L))
        inAppMessageDelayedManager.process(inAppLoser1, Milliseconds(0L))
        inAppMessageDelayedManager.process(inAppLoser2, Milliseconds(0L))
        advanceTimeBy(5000)
        inAppMessageDelayedManager.inAppToShowFlow.test {
            assertEquals(inAppWinner, awaitItem().first)
            expectNoEvents()
        }
    }
}
