package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTime
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InAppFrequencyManagerImplTest {

    private lateinit var inAppFrequencyManager: InAppFrequencyManagerImpl
    private lateinit var inAppRepository: InAppRepository

    @Before
    fun setUp() {
        inAppRepository = mockk()
        inAppFrequencyManager = InAppFrequencyManagerImpl(inAppRepository)
    }

    @Test
    fun `filterInAppsFrequency returns empty list for inApps with lifetime delay`() {
        val inApp = InAppStub.getInApp().copy(id = "1", frequency = InAppStub.getFrequency().copy(delay = Frequency.Delay.LifetimeDelay))

        val inApps = listOf(inApp)
        every { inAppRepository.getShownInApps() } returns mapOf(inApp.id to 15000L)
        val result = inAppFrequencyManager.filterInAppsFrequency(inApps)

        assertEquals(emptyList<InApp>(), result)
    }

    @Test
    fun `filterInAppsFrequency returns inApps with time delay that are expired`() {
        val currentTime = System.currentTimeMillis()
        val inAppWithTimeDelay = InAppStub.getInApp().copy(
            id = "2",
            frequency = Frequency(
                // 10 seconds delay
                delay = Frequency.Delay.TimeDelay(time = 10L, InAppTime.SECONDS)
            )
        )

        val inApps = listOf(
            inAppWithTimeDelay
        )

        every { inAppRepository.getShownInApps() } returns mapOf(inAppWithTimeDelay.id to currentTime - 5000L)

        val result = inAppFrequencyManager.filterInAppsFrequency(inApps)

        assertEquals(emptyList<InApp>(), result)
    }

    @Test
    fun `filterInAppsFrequency returns inApps with time delay that are not expired`() {
        val currentTime = System.currentTimeMillis()
        val inAppWithTimeDelay = InAppStub.getInApp().copy(
            id = "3",
            frequency = Frequency(
                // 10 seconds delay
                delay = Frequency.Delay.TimeDelay(time = 10L, InAppTime.SECONDS)
            )
        )

        val inApps = listOf(
            inAppWithTimeDelay
        )
        every { inAppRepository.getShownInApps() } returns mapOf(inAppWithTimeDelay.id to currentTime - 15000L)

        val result = inAppFrequencyManager.filterInAppsFrequency(inApps)

        assertEquals(listOf(inAppWithTimeDelay), result)
    }
}
