package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class InAppFilteringManagerTest {


    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppFilteringManager: InAppFilteringManagerImpl

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @Test
    fun `filter not shown inApps`() {
        val testHashSet = hashSetOf("inAppId")
        val expectedResult = listOf(InAppStub.getInApp().copy(id = "otherInAppId"))
        every { inAppRepository.getShownInApps() } returns testHashSet
        val actualResult = inAppFilteringManager.filterNotShownInApps(
            testHashSet,
            listOf(
                InAppStub.getInApp().copy(id = "inAppId"),
                InAppStub.getInApp().copy(id = "otherInAppId")
            )
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `filter inApp by event with App startUp`() {
        val testInApps =  listOf(InAppStub.getInApp().copy(id = "otherInAppId"))
        val actualResult = inAppFilteringManager.filterInAppsByEvent(testInApps, InAppEventType.AppStartup)
        assertEquals(testInApps, actualResult)
    }

    @Test
    fun `filter inApp by event with Ordinal event`() {
        val testInApps =  listOf(InAppStub.getInApp().copy(id = "otherInAppId"))
        val testOperation = "testOperation"
        every { inAppRepository.getOperationalInAppsByOperation(testOperation) } returns listOf(InAppStub.getInApp().copy(id = "testInAppId"))
        val actualResult = inAppFilteringManager.filterInAppsByEvent(testInApps, InAppEventType.AppStartup)
        assertEquals(testInApps, actualResult)
    }

}