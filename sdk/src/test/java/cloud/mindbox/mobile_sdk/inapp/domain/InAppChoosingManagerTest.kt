package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppChoosingManagerTest : KoinTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
        androidContext(mockkClass(Context::class))
    }

    @get:Rule
    val mockProvider = MockProviderRule.create { clazz ->
        mockkClass(clazz)
    }

    @MockK
    private lateinit var inAppGeoRepository: InAppGeoRepository

    @MockK
    private lateinit var inAppSegmentationRepository: InAppSegmentationRepository

    @MockK
    private lateinit var inAppFilteringManager: InAppFilteringManager

    @MockK
    private lateinit var inAppRepository: InAppRepository


    @OverrideMockKs
    private lateinit var inAppChoosingManager: InAppChoosingManagerImpl

    val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("testEvent"), null)

    @Before
    fun onTestStart() {
        coEvery {
            inAppGeoRepository.fetchGeo()
        } just runs
        mockkObject(SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED)
        mockkObject(SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS)
        mockkObject(SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR)
        mockkObject(MindboxKoin)
        every {
            MindboxKoin.koin
        } returns getKoin()
        inAppRepository = declareMock()
        inAppGeoRepository = declareMock()
        inAppSegmentationRepository = declareMock()
        every {
            inAppGeoRepository.getGeoFetchedStatus()
        } returns GeoFetchStatus.GEO_FETCH_SUCCESS
        every { inAppSegmentationRepository.getCustomerSegmentationFetched() } returns SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        coEvery {
            inAppSegmentationRepository.fetchCustomerSegmentations()
        } just runs
        every {
            inAppGeoRepository.setGeoStatus(any())
        } just runs
        every {
            inAppGeoRepository.getGeo()
        } returns GeoTargetingStub.getGeoTargeting().copy(
            cityId = "",
            regionId = "regionId",
            countryId = ""
        )
        every {
            inAppSegmentationRepository.getCustomerSegmentations()
        } returns listOf(
            SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                segmentation = "segmentationEI",
                segment = "segmentEI"
            )
        )

    }

    @Test
    fun `choose inApp to show chooses first correct inApp`() = runTest {
        val validId = "validId"
        val expectedResult = InAppTypeStub.get().copy(inAppId = validId)
        val actualResult = inAppChoosingManager.chooseInAppToShow(
            listOf(
                InAppStub.getInApp()
                    .copy(
                        id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                            type = "",
                            kind = Kind.POSITIVE,
                            ids = listOf("otherRegionId")
                        )
                    ),
                InAppStub.getInApp()
                    .copy(id = validId, targeting = InAppStub.getTargetingTrueNode()),

                ),
            event
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `choose inApp to show has no choosable inApps`() = runTest {
        assertNull(
            inAppChoosingManager.chooseInAppToShow(
                listOf(
                    InAppStub.getInApp()
                        .copy(
                            id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                                type = "",
                                kind = Kind.POSITIVE,
                                ids = listOf("otherRegionId")
                            )
                        ),
                    InAppStub.getInApp()
                        .copy(
                            id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                                type = "",
                                kind = Kind.POSITIVE,
                                ids = listOf("otherRegionId2")
                            )
                        ),

                    ),
                event
            )
        )
    }

    @Test
    fun `choose inApp to show general error`() {
        every {
            inAppGeoRepository.getGeo()
        } throws Error()
        assertThrows(Throwable::class.java) {
            runBlocking {
                inAppChoosingManager.chooseInAppToShow(
                    listOf(
                        InAppStub.getInApp()
                            .copy(
                                id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                                    type = "",
                                    kind = Kind.POSITIVE,
                                    ids = listOf("otherRegionId")
                                )
                            ),
                        InAppStub.getInApp()
                            .copy(
                                id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                                    type = "",
                                    kind = Kind.POSITIVE,
                                    ids = listOf("otherRegionId2")
                                )
                            ),

                        ),
                    event
                )
            }
        }
    }

    @Test
    fun `choose inApp to show segmentation error`() = runTest {
        val validId = "validId"
        val testInAppList = listOf(
            InAppStub.getInApp()
                .copy(
                    id = "123", targeting = InAppStub.getTargetingSegmentNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        segmentationExternalId = "segmentationExternalId1",
                        segmentExternalId = "segmentExternalId1"
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = "124", targeting = InAppStub.getTargetingSegmentNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        segmentationExternalId = "segmentationExternalId2",
                        segmentExternalId = "segmentExternalId2"
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = validId, targeting = InAppStub.getTargetingTrueNode()
                )
        )
        coEvery {
            inAppSegmentationRepository.fetchCustomerSegmentations()
        } throws SegmentationError(VolleyError())
        every {
            inAppFilteringManager.filterSegmentationFreeInApps(any())
        } returns listOf(
            InAppStub.getInApp()
                .copy(
                    id = validId, targeting = InAppStub.getTargetingTrueNode()
                )
        )
        every {
            inAppSegmentationRepository.setCustomerSegmentationStatus(any())
        } just runs
        val expectedResult = InAppTypeStub.get().copy(inAppId = validId)
        val actualResult = inAppChoosingManager.chooseInAppToShow(
            testInAppList,
            event
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `choose inApp to show geo error`() = runTest {
        val validId = "validId"
        val testInAppList = listOf(
            InAppStub.getInApp()
                .copy(
                    id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        ids = listOf("otherRegionId")
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = "124", targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        ids = listOf("otherRegionId2")
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = validId, targeting = InAppStub.getTargetingTrueNode()
                )
        )
        coEvery {
            inAppGeoRepository.fetchGeo()
        } throws GeoError(VolleyError())
        every {
            inAppFilteringManager.filterGeoFreeInApps(any())
        } returns listOf(
            InAppStub.getInApp()
                .copy(
                    id = validId, targeting = InAppStub.getTargetingTrueNode()
                )
        )
        val expectedResult = InAppTypeStub.get().copy(inAppId = validId)
        val actualResult = inAppChoosingManager.chooseInAppToShow(
            testInAppList,
            event
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `choose inApp to show geo and segmentation error`() = runTest {
        val validId = "validId"
        inAppFilteringManager = InAppFilteringManagerImpl(inAppRepository)
        inAppChoosingManager = InAppChoosingManagerImpl(
            inAppGeoRepository = inAppGeoRepository,
            inAppSegmentationRepository = inAppSegmentationRepository,
            inAppFilteringManager = inAppFilteringManager
        )
        every {
            inAppGeoRepository.getGeoFetchedStatus()
        } returns GeoFetchStatus.GEO_NOT_FETCHED

        every {
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        } returns SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        every {
            inAppSegmentationRepository.setCustomerSegmentationStatus(any())
        } just runs
        every {
            inAppGeoRepository.setGeoStatus(any())
        } just runs
        val testInAppList = listOf(
            InAppStub.getInApp()
                .copy(
                    id = "123", targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        ids = listOf("otherRegionId")
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = "124", targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        ids = listOf("otherRegionId2")
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = "125", targeting = InAppStub.getTargetingSegmentNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        segmentationExternalId = "segmentationExternalId1",
                        segmentExternalId = "segmentExternalId1"
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = "126", targeting = InAppStub.getTargetingSegmentNode().copy(
                        type = "",
                        kind = Kind.POSITIVE,
                        segmentationExternalId = "segmentationExternalId2",
                        segmentExternalId = "segmentExternalId2"
                    )
                ),
            InAppStub.getInApp()
                .copy(
                    id = validId, targeting = InAppStub.getTargetingTrueNode()
                )
        )
        coEvery {
            inAppSegmentationRepository.fetchCustomerSegmentations()
        } throws SegmentationError(VolleyError())
        coEvery {
            inAppGeoRepository.fetchGeo()
        } throws GeoError(VolleyError())
        val expectedResult = InAppTypeStub.get().copy(inAppId = validId)
        val actualResult = inAppChoosingManager.chooseInAppToShow(
            testInAppList,
            event
        )
        assertEquals(expectedResult, actualResult)
    }
}