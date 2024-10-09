package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
import cloud.mindbox.mobile_sdk.models.EventType
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
    fun `filter inApp by event with App startUp`() {
        val testInApps = listOf(InAppStub.getInApp().copy(id = "otherInAppId"))
        val actualResult =
            inAppFilteringManager.filterUnShownInAppsByEvent(testInApps, InAppEventType.AppStartup)
        assertEquals(testInApps, actualResult)
    }

    @Test
    fun `filter inApp by event with Ordinal event`() {
        val testInApps = listOf(InAppStub.getInApp().copy(id = "otherInAppId"))
        val expectedResult = listOf(InAppStub.getInApp().copy(id = "testInAppId"))
        every { inAppRepository.getUnShownOperationalInAppsByOperation(any()) } returns expectedResult
        val actualResult =
            inAppFilteringManager.filterUnShownInAppsByEvent(
                testInApps,
                InAppEventType.OrdinalEvent(EventType.SyncOperation(""))
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `filter inApps without operations`() {
        val expectedResult = listOf(
            InAppStub.getInApp()
                .copy(id = "validId", targeting = TreeTargeting.TrueNode("true"))
        )
        val actualResult = inAppFilteringManager.filterOperationFreeInApps(
            listOf(
                InAppStub.getInApp()
                    .copy(
                        id = "invalidId",
                        targeting = InAppStub.getTargetingOperationNode().copy("operation", "testSystemName")
                    ),
                InAppStub.getInApp()
                    .copy(id = "validId", targeting = InAppStub.getTargetingTrueNode().copy("true"))
            )
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `filter inApps without geo`() {
        val expectedResult = listOf(
            InAppStub.getInApp()
                .copy(id = "validId", targeting = TreeTargeting.TrueNode("true"))
        )
        val actualResult = inAppFilteringManager.filterGeoFreeInApps(
            listOf(
                InAppStub.getInApp()
                    .copy(
                        id = "invalidId",
                        targeting = InAppStub.getTargetingRegionNode()
                    ),
                InAppStub.getInApp()
                    .copy(id = "validId", targeting = TreeTargeting.TrueNode("true"))
            )
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `filter inApps without segmentations`() {
        val expectedResult = listOf(
            InAppStub.getInApp()
                .copy(id = "validId", targeting = TreeTargeting.TrueNode("true"))
        )
        val actualResult = inAppFilteringManager.filterSegmentationFreeInApps(
            listOf(
                InAppStub.getInApp()
                    .copy(
                        id = "invalidId",
                        targeting = InAppStub.getTargetingSegmentNode()
                    ),
                InAppStub.getInApp()
                    .copy(id = "validId", targeting = TreeTargeting.TrueNode("true"))
            )
        )
        assertEquals(expectedResult, actualResult)
    }
}
