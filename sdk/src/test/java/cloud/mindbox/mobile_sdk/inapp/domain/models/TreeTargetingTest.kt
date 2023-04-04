package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.models.*
import io.mockk.*
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TreeTargetingTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
        androidContext(mockkClass(Context::class))
    }

    @get:Rule
    val mockProvider = MockProviderRule.create { clazz ->
        mockkClass(clazz)
    }

    @get:Rule
    val mockkRule = MockKRule(this)

    private lateinit var inAppGeoRepository: InAppGeoRepository

    private lateinit var inAppSegmentationRepository: InAppSegmentationRepository

    @Before
    fun onTestStart() {
        mockkObject(MindboxKoin)
        every { MindboxKoin.koin } returns getKoin()
        inAppGeoRepository = declareMock()
        inAppSegmentationRepository = declareMock()
        every { inAppGeoRepository.getGeo() } returns GeoTargetingStub.getGeoTargeting()
            .copy(
                cityId = "123",
                regionId = "456",
                countryId = "789"
            )
        every {
            inAppGeoRepository.getGeoFetchedStatus()
        } returns GeoFetchStatus.GEO_FETCH_SUCCESS
        every {
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        } returns SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
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
            inAppSegmentationRepository.getCustomerSegmentations()
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
            inAppSegmentationRepository.getCustomerSegmentations()
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
            inAppSegmentationRepository.getCustomerSegmentations()
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
            inAppSegmentationRepository.getCustomerSegmentations()
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
            inAppGeoRepository.fetchGeo()
        } just runs
        coEvery {
            inAppSegmentationRepository.fetchCustomerSegmentations()
        } just runs
        every {
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        } returns SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        every {
            inAppGeoRepository.getGeoFetchedStatus()
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
            inAppGeoRepository.fetchGeo()
        }
        coVerify {
            inAppSegmentationRepository.fetchCustomerSegmentations()
        }

    }

    class TestTargetingData(
        override val triggerEventName: String,
        override val operationBody: String? = null
    ): TargetingData.OperationBody, TargetingData.OperationName

}