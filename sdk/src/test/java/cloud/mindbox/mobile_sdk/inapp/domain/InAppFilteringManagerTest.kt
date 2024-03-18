package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

internal class InAppFilteringManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppFilteringManager: InAppFilteringManagerImpl

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @MockK
    private lateinit var mindboxNotificationManager: MindboxNotificationManager

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

    @Test
    fun `filterPushInAppsByPermissionStatus returns all inApps when notifications disabled`() {
        every { mindboxNotificationManager.isNotificationEnabled() } returns false

        val inAppWithPushPermissionAction = InAppStub.getInApp().copy(
            form = Form(
                variants = listOf(
                    InAppStub.getModalWindow().copy(
                        type = "modal",
                        inAppId = "123",
                        layers = listOf(
                            Layer.ImageLayer(
                                action = InAppStub.getPushPermissionAction(),
                                source = InAppStub.getUrlSource()
                            )
                        )
                    )
                )
            )
        )

        val inAppWithRedirectUrlAction = InAppStub.getInApp().copy(
            form = Form(
                variants = listOf(
                    InAppStub.getModalWindow().copy(
                        type = "modal",
                        inAppId = "123",
                        layers = listOf(
                            Layer.ImageLayer(
                                action = InAppStub.getRedirectUrlAction(),
                                source = InAppStub.getUrlSource()
                            )
                        )
                    )
                )
            )
        )

        val inApps = listOf(inAppWithRedirectUrlAction, inAppWithPushPermissionAction)
        val filteredInApps = inAppFilteringManager.filterPushInAppsByPermissionStatus(inApps)

        assertEquals(filteredInApps, inApps)
    }

    @Test
    fun `filterPushInAppsByPermissionStatus filters  inApps with ImageLayer having PushPermissionAction when notifications enabled`() {
        every { mindboxNotificationManager.isNotificationEnabled() } returns true

        val inAppWithPushPermissionAction = InAppStub.getInApp().copy(
            form = Form(
                variants = listOf(
                    InAppStub.getModalWindow().copy(
                        type = "modal",
                        inAppId = "123",
                        layers = listOf(
                            Layer.ImageLayer(
                                action = InAppStub.getPushPermissionAction(),
                                source = InAppStub.getUrlSource()
                            )
                        )
                    )
                )
            )
        )

        val inAppWithRedirectUrlAction = InAppStub.getInApp().copy(
            form = Form(
                variants = listOf(
                    InAppStub.getModalWindow().copy(
                        type = "modal",
                        inAppId = "123",
                        layers = listOf(
                            Layer.ImageLayer(
                                action = InAppStub.getRedirectUrlAction(),
                                source = InAppStub.getUrlSource()
                            )
                        )
                    )
                )
            )
        )

        val inApps = listOf(inAppWithRedirectUrlAction, inAppWithPushPermissionAction)
        val filteredInApps = inAppFilteringManager.filterPushInAppsByPermissionStatus(inApps)

        assertEquals(filteredInApps, listOf(inAppWithRedirectUrlAction))
        assertNotEquals(filteredInApps,inApps)
    }

}