package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.checkers.MaxInappsPerDayLimitChecker
import cloud.mindbox.mobile_sdk.inapp.data.checkers.MaxInappsPerSessionLimitChecker
import cloud.mindbox.mobile_sdk.inapp.data.checkers.MinIntervalBetweenShowsLimitChecker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowBudgetOwner
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class ShowBudgetManagerImplTest {

    private val timeProvider: TimeProvider = mockk()
    private val inAppRepository: InAppRepository = mockk(relaxed = true)
    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var manager: ShowBudgetManagerImpl

    private val now = Timestamp(1_700_000_000_000L)
    private val counting = InAppStub.getInApp().frequency
    private val unlimited = Frequency(Frequency.Delay.Unlimited)
    private val place = ShowBudgetOwner.place("main-screen-top")
    private val overlay = ShowBudgetOwner.overlay("modal-1")

    @Before
    fun setUp() {
        every { timeProvider.currentTimestamp() } returns now
        every { timeProvider.currentTimeMillis() } returns now.ms
        every { inAppRepository.getShownInApps() } returns emptyMap()
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(0L)
        sessionStorageManager = SessionStorageManager(timeProvider)
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(maxInappsPerSession = 1)
        manager = managerWith(inAppRepository)
    }

    private fun managerWith(repository: InAppRepository) = ShowBudgetManagerImpl(
        sessionStorageManager,
        repository,
        timeProvider,
        MaxInappsPerSessionLimitChecker(sessionStorageManager),
        MaxInappsPerDayLimitChecker(repository, sessionStorageManager, timeProvider),
        MinIntervalBetweenShowsLimitChecker(sessionStorageManager, repository, timeProvider)
    )

    @Test
    fun `the first to reserve takes the place in the budget and the second sees it taken`() {
        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(place, "block", counting, isPriority = false))

        assertEquals(ShowReservationOutcome.REFUSED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
        assertEquals(setOf(place), sessionStorageManager.showReservations.keys)
    }

    @Test
    fun `a released hold gives the budget back to the next candidate`() {
        manager.reserve(place, "block", counting, isPriority = false)

        manager.release(place)

        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
    }

    @Test
    fun `commit turns the hold into counted show in one step and frees no room`() {
        manager.reserve(place, "block", counting, isPriority = false)

        manager.commit(place, "block", counting, now)

        assertTrue(sessionStorageManager.showReservations.isEmpty())
        verifyOrder {
            inAppRepository.setInAppShown("block")
            inAppRepository.saveShownInApp("block", now.ms)
            inAppRepository.saveInAppStateChangeTime(now)
        }
        // The counter now stands where the hold stood (the repository writes it): still one, still spent.
        sessionStorageManager.inAppMessageShownInSession.add("block")
        assertEquals(ShowReservationOutcome.REFUSED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
    }

    @Test
    fun `unlimited and priority pass without a hold and are never counted`() {
        manager.reserve(place, "block", counting, isPriority = false)

        assertEquals(ShowReservationOutcome.NOT_NEEDED, manager.reserve(overlay, "unlimited", unlimited, isPriority = false))
        assertEquals(ShowReservationOutcome.NOT_NEEDED, manager.reserve(ShowBudgetOwner.overlay("prio"), "prio", counting, isPriority = true))
        assertEquals(setOf(place), sessionStorageManager.showReservations.keys)

        manager.commit(overlay, "unlimited", unlimited, now)
        verify(exactly = 0) { inAppRepository.setInAppShown("unlimited") }
        verify(exactly = 0) { inAppRepository.saveInAppStateChangeTime(any()) }
    }

    @Test
    fun `the owner re-asking about its own hold is not counted against itself`() {
        // The idle second resolve pass of a place must not empty the block that just reserved.
        manager.reserve(place, "block", counting, isPriority = false)

        assertTrue(manager.isWithinBudgets(counting, isPriority = false, owner = place))
        assertFalse(manager.isWithinBudgets(counting, isPriority = false, owner = overlay))
        assertFalse(manager.isWithinBudgets(counting, isPriority = false))
    }

    @Test
    fun `re-reserving the same in-app for the same owner keeps the one hold`() {
        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(place, "block", counting, isPriority = false))

        // The second asker owns nothing: it must not give the first one's hold back.
        assertEquals(ShowReservationOutcome.ALREADY_HELD, manager.reserve(place, "block", counting, isPriority = false))

        assertEquals(1, sessionStorageManager.showReservations.size)
    }

    @Test
    fun `a newer candidate for the same owner replaces the old hold, then is judged without it`() {
        manager.reserve(place, "block-1", counting, isPriority = false)

        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(place, "block-2", counting, isPriority = false))

        assertEquals("block-2", sessionStorageManager.showReservations.getValue(place).inAppId)
    }

    @Test
    fun `a replacement refused by the budget leaves the owner with no hold`() {
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(maxInappsPerSession = 1)
        manager.reserve(place, "block-1", counting, isPriority = false)
        sessionStorageManager.inAppMessageShownInSession.add("someone-else")

        assertEquals(ShowReservationOutcome.REFUSED, manager.reserve(place, "block-2", counting, isPriority = false))

        assertNull(sessionStorageManager.showReservations[place])
    }

    @Test
    fun `the daily budget counts the holds too`() {
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(maxInappsPerDay = 2)
        every { inAppRepository.getShownInApps() } returns mapOf("earlier" to listOf(now.ms - 1_000L))
        manager.reserve(place, "block", counting, isPriority = false)

        assertEquals(ShowReservationOutcome.REFUSED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
    }

    @Test
    fun `the cooldown runs from the latest of the last show and the last hold`() {
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(minIntervalBetweenShows = Milliseconds(60_000L))
        manager.reserve(place, "block", counting, isPriority = false)

        // A hold taken just now is as good as a show for the pause: the overlay waits.
        assertEquals(ShowReservationOutcome.REFUSED, manager.reserve(overlay, "modal-1", counting, isPriority = false))

        every { timeProvider.currentTimestamp() } returns Timestamp(now.ms + 61_000L)
        manager.release(place)
        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
    }

    @Test
    fun `recordCooldown moves the stamp for a counted frequency only`() {
        manager.recordCooldown(unlimited, now)
        verify(exactly = 0) { inAppRepository.saveInAppStateChangeTime(any()) }

        manager.recordCooldown(counting, now)
        verify(exactly = 1) { inAppRepository.saveInAppStateChangeTime(now) }
    }

    @Test
    fun `sixteen candidates racing for one place in the budget - exactly one gets it`() {
        val pool = Executors.newFixedThreadPool(8)
        val gate = CountDownLatch(1)
        val granted = AtomicInteger()
        val done = CountDownLatch(16)
        repeat(16) { index ->
            pool.execute {
                gate.await()
                if (manager.reserve("owner-$index", "in-app-$index", counting, isPriority = false) == ShowReservationOutcome.GRANTED) granted.incrementAndGet()
                done.countDown()
            }
        }
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(1, granted.get())
        assertEquals(1, sessionStorageManager.showReservations.size)
    }

    @Test
    fun `a session reset wipes the holds with the counters`() {
        manager.reserve(place, "block", counting, isPriority = false)

        sessionStorageManager.clearSessionData()

        assertTrue(sessionStorageManager.showReservations.isEmpty())
        assertEquals(ShowReservationOutcome.GRANTED, manager.reserve(overlay, "modal-1", counting, isPriority = false))
    }

    @Test
    fun `a reserve that blocks mid-read keeps the second asker out until it decides`() {
        // Deterministic version of the race: the first asker is held inside the critical section
        // (on the daily read), the second must wait for it and then find the budget taken.
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(maxInappsPerSession = 1, maxInappsPerDay = 5)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        every { inAppRepository.getShownInApps() } answers {
            entered.countDown()
            proceed.await(5, TimeUnit.SECONDS)
            emptyMap()
        }
        val workers = Executors.newFixedThreadPool(2)
        val first = workers.submit<ShowReservationOutcome> {
            manager.reserve(place, "block", counting, isPriority = false)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = workers.submit<ShowReservationOutcome> {
            manager.reserve(overlay, "modal-1", counting, isPriority = false)
        }
        // While the first asker holds the lock, the second cannot answer at all.
        assertFalse(second.isDone)
        Thread.sleep(150)
        assertFalse(second.isDone)

        proceed.countDown()

        assertEquals(ShowReservationOutcome.GRANTED, first.get(5, TimeUnit.SECONDS))
        assertEquals(ShowReservationOutcome.REFUSED, second.get(5, TimeUnit.SECONDS))
        workers.shutdown()
    }

    @Test
    fun `a session reset waits for a reserve in flight and leaves no hold behind`() {
        // The reset used to wipe the holds outside the manager's lock: a reserve that had already
        // passed its check would then put its hold into the fresh session.
        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(maxInappsPerSession = 1, maxInappsPerDay = 5)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        every { inAppRepository.getShownInApps() } answers {
            entered.countDown()
            proceed.await(5, TimeUnit.SECONDS)
            emptyMap()
        }
        val workers = Executors.newFixedThreadPool(2)
        val reserve = workers.submit<ShowReservationOutcome> {
            manager.reserve(place, "block", counting, isPriority = false)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val reset = workers.submit { sessionStorageManager.clearSessionData() }
        Thread.sleep(150)
        assertFalse(reset.isDone)

        proceed.countDown()

        assertEquals(ShowReservationOutcome.GRANTED, reserve.get(5, TimeUnit.SECONDS))
        reset.get(5, TimeUnit.SECONDS)
        assertTrue(sessionStorageManager.showReservations.isEmpty())
        workers.shutdown()
    }

    @Test
    fun `commit leaves no window in which the budget reads as free`() {
        // The repository really writes the session counter here, and blocks right after the
        // reservation was removed — a concurrent reserve must still see the budget taken.
        val counting = this.counting
        val blockingRepository = object : InAppRepository by inAppRepository {
            override fun setInAppShown(inAppId: String) {
                sessionStorageManager.inAppMessageShownInSession.add(inAppId)
            }

            override fun saveShownInApp(id: String, timeStamp: Long) {
                inCommit.countDown()
                letCommitFinish.await(5, TimeUnit.SECONDS)
            }

            override fun saveInAppStateChangeTime(timeStamp: Timestamp) {}
        }
        val manager = managerWith(blockingRepository)
        manager.reserve(place, "block", counting, isPriority = false)

        val workers = Executors.newFixedThreadPool(2)
        val commit = workers.submit { manager.commit(place, "block", counting, now) }
        assertTrue(inCommit.await(5, TimeUnit.SECONDS))
        val rival = workers.submit<ShowReservationOutcome> {
            manager.reserve(overlay, "modal-1", counting, isPriority = false)
        }
        Thread.sleep(150)
        assertFalse(rival.isDone)

        letCommitFinish.countDown()
        commit.get(5, TimeUnit.SECONDS)

        assertEquals(ShowReservationOutcome.REFUSED, rival.get(5, TimeUnit.SECONDS))
        assertTrue(sessionStorageManager.showReservations.isEmpty())
        workers.shutdown()
    }

    private val inCommit = CountDownLatch(1)
    private val letCommitFinish = CountDownLatch(1)
}
