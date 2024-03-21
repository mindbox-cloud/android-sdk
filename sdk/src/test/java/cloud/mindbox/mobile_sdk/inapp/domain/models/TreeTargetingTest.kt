package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.GeoTargetingStub
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import io.mockk.*
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test


@OptIn(ExperimentalCoroutinesApi::class)
class TreeTargetingTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val mockkMobileConfigRepository: MobileConfigRepository = mockk {
        every {
            runBlocking { getOperations() }
        } returns mapOf(
            OperationName.VIEW_PRODUCT to OperationSystemName("TestSystemNameProduct"),
            OperationName.VIEW_CATEGORY to OperationSystemName("TestSystemNameCategory"),
        )
    }

    private val mockkInAppSegmentationRepository: InAppSegmentationRepository = mockk {
        every {
            getCustomerSegmentationFetched()
        } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
    }

    private val mockkInAppGeoRepository: InAppGeoRepository = mockk {
        every { getGeoFetchedStatus() } returns GeoFetchStatus.GEO_FETCH_SUCCESS
        every { getGeo() } returns GeoTargetingStub.getGeoTargeting()
            .copy(
                cityId = "123",
                regionId = "456",
                countryId = "789"
            )
    }

    @Before
    fun onTestStart() {
        mockkObject(MindboxEventManager)
        MindboxEventManager.eventFlow.resetReplayCache()

        // mockk 'by mindboxInject { }'
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { mobileConfigRepository } returns mockkMobileConfigRepository
            every { inAppSegmentationRepository } returns mockkInAppSegmentationRepository
            every { inAppGeoRepository } returns mockkInAppGeoRepository
            every { gson } returns Gson()
        }
    }

    @Test
    fun `true targeting always true`() {
        assertTrue(InAppStub.getTargetingTrueNode().checkTargeting(mockk()))
    }

    @Test
    fun `country targeting positive success check`() {
        assertTrue(
            InAppStub.getTargetingCountryNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("789", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `country targeting positive error check`() {
        assertFalse(
            InAppStub.getTargetingCountryNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("788", "456"))
                .checkTargeting(mockk())
        )

    }

    @Test
    fun `country targeting negative error check`() {
        assertFalse(
            InAppStub.getTargetingCountryNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("789", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `country targeting negative success check`() {
        assertTrue(
            InAppStub.getTargetingCountryNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("788", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `region targeting positive success check`() {
        assertTrue(
            InAppStub.getTargetingRegionNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("789", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `region targeting positive error check`() {
        assertFalse(
            InAppStub.getTargetingRegionNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("788", "455"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `region targeting negative error check`() {
        assertFalse(
            InAppStub.getTargetingRegionNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("789", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `region targeting negative success check`() {
        assertTrue(
            InAppStub.getTargetingRegionNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("788", "455"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `city targeting positive success check`() {
        assertTrue(
            InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("123", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `city targeting positive error check`() {
        assertFalse(
            InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("788", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `city targeting negative error check`() {
        assertFalse(
            InAppStub.getTargetingCityNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("123", "456"))
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `city targeting negative success check`() {
        assertTrue(
            InAppStub.getTargetingCityNode()
                .copy(kind = Kind.NEGATIVE, ids = listOf("788", "456"))
                .checkTargeting(mockk())
        )
    }


    @Test
    fun `segment targeting positive success check`() {
        every {
            mockkInAppSegmentationRepository.getCustomerSegmentations()
        } returns listOf(
            SegmentationCheckInAppStub.getCustomerSegmentation().copy(segmentation = "123", "234")
        )
        assertTrue(
            InAppStub.getTargetingSegmentNode()
                .copy(
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "123",
                    segmentExternalId = "234"
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `segment targeting positive error check`() {
        every {
            mockkInAppSegmentationRepository.getCustomerSegmentations()
        } returns listOf(SegmentationCheckInAppStub.getCustomerSegmentation().copy())
        assertFalse(
            InAppStub.getTargetingSegmentNode()
                .copy(
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "123",
                    segmentExternalId = "234"
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `segment targeting negative error check`() {
        every {
            mockkInAppSegmentationRepository.getCustomerSegmentations()
        } returns listOf(SegmentationCheckInAppStub.getCustomerSegmentation().copy())
        assertFalse(
            InAppStub.getTargetingSegmentNode()
                .copy(
                    kind = Kind.NEGATIVE,
                    segmentationExternalId = "123",
                    segmentExternalId = "234"
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `segment targeting negative success check`() {
        every {
            mockkInAppSegmentationRepository.getCustomerSegmentations()
        } returns listOf(
            SegmentationCheckInAppStub.getCustomerSegmentation().copy(segmentation = "123", "235")
        )
        assertTrue(
            InAppStub.getTargetingSegmentNode()
                .copy(
                    kind = Kind.NEGATIVE,
                    segmentationExternalId = "123",
                    segmentExternalId = "234"
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `intersection targeting check both true`() {
        assertTrue(
            InAppStub.getTargetingIntersectionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                        InAppStub.getTargetingRegionNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("456"))
                    )
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `intersection targeting check both false`() {
        assertFalse(
            InAppStub.getTargetingIntersectionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234")),
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234"))
                    )
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `intersection targeting check one true one false`() {
        assertFalse(
            InAppStub.getTargetingIntersectionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234"))
                    )
                )
                .checkTargeting(mockk())
        )
    }


    @Test
    fun `union targeting check both true`() {
        assertTrue(
            InAppStub.getTargetingUnionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                        InAppStub.getTargetingRegionNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("456"))
                    )
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `union targeting check both false`() {
        assertFalse(
            InAppStub.getTargetingUnionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234")),
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234"))
                    )
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `operation targeting check`() = runTest {

        val testTargeting = spyk(
            OperationNode(systemName = "testOperation", type = "apiMethodCall"),
            recordPrivateCalls = true
        )

        assertTrue(testTargeting.checkTargeting(TestTargetingData("testOperation")))

    }

    @Test
    fun `union targeting check one true one false`() {
        assertTrue(
            InAppStub.getTargetingUnionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                        InAppStub.getTargetingCityNode()
                            .copy(kind = Kind.POSITIVE, ids = listOf("234"))
                    )
                )
                .checkTargeting(mockk())
        )
    }

    @Test
    fun `get operations list in nodes`() = runTest {
        val expectedResult = setOf("testOperation")
        val actualResult = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingUnionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingIntersectionNode().copy(
                            nodes = listOf(
                                InAppStub.getTargetingTrueNode(),
                                InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingRegionNode(),
                                InAppStub.getTargetingCityNode(),
                                InAppStub.getTargetingCountryNode(),
                                InAppStub.getTargetingOperationNode().copy(
                                    systemName = "testOperation"
                                )
                            )
                        )
                    )
                )
        ).targeting.getOperationsSet()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `fetch targetings`() = runTest {
        coEvery {
            mockkInAppGeoRepository.fetchGeo()
        } just runs
        coEvery {
            mockkInAppSegmentationRepository.fetchCustomerSegmentations()
        } just runs
        every {
            mockkInAppSegmentationRepository.getCustomerSegmentationFetched()
        } returns CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        every {
            mockkInAppGeoRepository.getGeoFetchedStatus()
        } returns GeoFetchStatus.GEO_NOT_FETCHED
        InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingUnionNode()
                .copy(
                    nodes = listOf(
                        InAppStub.getTargetingIntersectionNode().copy(
                            nodes = listOf(
                                InAppStub.getTargetingTrueNode(),
                                InAppStub.getTargetingSegmentNode(),
                                InAppStub.getTargetingRegionNode(),
                                InAppStub.getTargetingCityNode(),
                                InAppStub.getTargetingCountryNode(),
                                InAppStub.getTargetingOperationNode().copy(
                                    systemName = "testOperation"
                                )
                            )
                        )
                    )
                )
        ).targeting.fetchTargetingInfo(mockk())
        coVerify {
            mockkInAppGeoRepository.fetchGeo()
        }
        coVerify {
            mockkInAppSegmentationRepository.fetchCustomerSegmentations()
        }

    }


    @Test
    fun `check targeting visit GTE returns false when user visit count is lower`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.GTE
        val value = 11L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertFalse(result)
    }

    @Test
    fun `check targeting visit GTE returns true when user visit count is higher`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.GTE
        val value = 9L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    @Test
    fun `check targeting visit GTE returns true when user visit count is equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.GTE
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    @Test
    fun `check targeting visit LTE returns false when user visit count is higher`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.LTE
        val value = 11L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 12L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertFalse(result)
    }

    @Test
    fun `check targeting visit LTE returns true when user visit count is higher`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.LTE
        val value = 9L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 8L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    @Test
    fun `check targeting visit LTE returns true when user visit count is equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.LTE
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    @Test
    fun `check targeting visit equals returns false when user visit is not equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.EQUALS
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 11L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertFalse(result)
    }

    @Test
    fun `check targeting visit equals returns true when user visit count is equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.EQUALS
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    @Test
    fun `check targeting visit not equals returns false when user visit count is equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.NOT_EQUALS
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 10L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertFalse(result)
    }

    @Test
    fun `check targeting visit not equals returns true when user visit count is not equal`() {
        mockkObject(MindboxPreferences)
        val kind = KindVisit.NOT_EQUALS
        val value = 10L
        val targeting = InAppStub.getTargetingVisitNode().copy(kind = kind, value = value)

        val userVisitCount = 9L
        every { MindboxPreferences.userVisitCount } returns (userVisitCount.toInt())

        val result = targeting.checkTargeting(mockk())

        assertTrue(result)
    }

    class TestTargetingData(
        override val triggerEventName: String,
        override val operationBody: String? = null
    ) : TargetingData.OperationBody, TargetingData.OperationName

}