package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.models.*
import com.android.volley.VolleyError
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
internal class InAppProcessingManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("testEvent"), null)

    private val mockkInAppContentFetcher = mockk<InAppContentFetcher> {
        coEvery { fetchContent(any(), any()) } returns true
    }

    private val mockInAppRepository = mockk<InAppRepository> {
        every {
            sendUserTargeted(any())
        } just runs
        every {
            saveTargetedInAppWithEvent(any(), any())
        } just runs
    }

    private val mockkInAppGeoRepository = mockk<InAppGeoRepository> {
        every { getGeoFetchedStatus() } returns GeoFetchStatus.GEO_FETCH_SUCCESS
        coEvery { fetchGeo() } just runs
        every { setGeoStatus(any()) } just runs
        every { getGeo() } returns GeoTargetingStub.getGeoTargeting().copy(
            cityId = "", regionId = "regionId", countryId = ""
        )
    }

    private val mockkInAppSegmentationRepository = mockk<InAppSegmentationRepository> {
        every {
            getCustomerSegmentationFetched()
        } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS

        coEvery { fetchCustomerSegmentations() } just runs

        every {
            getCustomerSegmentations()
        } returns listOf(
            SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                segmentation = "segmentationEI", segment = "segmentEI"
            )
        )
    }

    @Before
    fun onTestStart() {
        // mockk 'by mindboxInject { }'
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { inAppGeoRepository } returns mockkInAppGeoRepository
            every { inAppSegmentationRepository } returns mockkInAppSegmentationRepository
            every { inAppRepository } returns mockInAppRepository
        }
    }

    private val inAppProcessingManager = InAppProcessingManagerImpl(
        inAppGeoRepository = mockkInAppGeoRepository,
        inAppSegmentationRepository = mockkInAppSegmentationRepository,
        inAppContentFetcher = mockkInAppContentFetcher,
        inAppRepository = mockInAppRepository
    )

    @Test
    fun `check targeting returns false sends 0 targeted requests`() = runTest {
        val testInApp = mockk<InApp>(relaxed = true)
        every {
            testInApp.targeting.checkTargeting(any())
        } returns false
        coEvery {
            testInApp.targeting.fetchTargetingInfo(any())
        } just runs
        inAppProcessingManager.sendTargetedInApp(
            testInApp,
            InAppEventType.OrdinalEvent(EventType.AsyncOperation(""), "")
        )
        verify(exactly = 0) {
            mockInAppRepository.sendUserTargeted(any())
        }
    }

    @Test
    fun `check targeting returns true sends inApp count targeted requests`() = runTest {
        val testInApp = mockk<InApp>(relaxed = true)
        val testInApp2 = mockk<InApp>(relaxed = true)
        every {
            testInApp.targeting.checkTargeting(any())
        } returns true
        coEvery {
            testInApp.targeting.fetchTargetingInfo(any())
        } just runs
        every {
            testInApp2.targeting.checkTargeting(any())
        } returns true
        coEvery {
            testInApp2.targeting.fetchTargetingInfo(any())
        } just runs
        inAppProcessingManager.sendTargetedInApp(
            testInApp,
            InAppEventType.OrdinalEvent(EventType.AsyncOperation(""), "")
        )
        inAppProcessingManager.sendTargetedInApp(
            testInApp2,
            InAppEventType.OrdinalEvent(EventType.AsyncOperation(""), "")
        )
        verify(exactly = 2) {
            mockInAppRepository.sendUserTargeted(any())
        }
    }

    @Test
    fun `choose inApp to show chooses first correct inApp`() = runTest {
        val validId = "validId"
        val expectedResult = InAppStub.getModalWindow().copy(inAppId = validId)

        val actualResult = inAppProcessingManager.chooseInAppToShow(
            listOf(
                InAppStub.getInApp().copy(
                    id = "123",
                    targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                    )
                ),
                InAppStub
                    .getInApp()
                    .copy(
                        id = validId,
                        targeting = InAppStub.getTargetingTrueNode(),
                        form = InAppStub.getInApp().form.copy(
                            listOf(
                                InAppStub.getModalWindow().copy(
                                    inAppId = validId
                                )
                            )
                        )
                    ),
            ),
            event
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `choose inApp to show has no choosable inApps`() = runTest {
        assertNull(
            inAppProcessingManager.chooseInAppToShow(
                listOf(
                    InAppStub.getInApp().copy(
                        id = "123",
                        targeting = InAppStub.getTargetingRegionNode().copy(
                            type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                        )
                    ),
                    InAppStub.getInApp().copy(
                        id = "123",
                        targeting = InAppStub.getTargetingRegionNode().copy(
                            type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId2")
                        )
                    ),
                ),
                event
            )
        )
    }

    @Test
    fun `choose inApp to show general error`() {
        every { mockkInAppGeoRepository.getGeo() } throws Error()

        assertThrows(Throwable::class.java) {
            runBlocking {
                MindboxDI.appModule.inAppProcessingManager.chooseInAppToShow(
                    listOf(
                        InAppStub.getInApp().copy(
                            id = "123",
                            targeting = InAppStub.getTargetingRegionNode().copy(
                                type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                            )
                        ),
                        InAppStub.getInApp().copy(
                            id = "123",
                            targeting = InAppStub.getTargetingRegionNode().copy(
                                type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId2")
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
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingSegmentNode().copy(
                    type = "",
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "segmentationExternalId1",
                    segmentExternalId = "segmentExternalId1"
                )
            ),
            InAppStub.getInApp().copy(
                id = "124",
                targeting = InAppStub.getTargetingSegmentNode().copy(
                    type = "",
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "segmentationExternalId2",
                    segmentExternalId = "segmentExternalId2"
                )
            ),
            InAppStub.getInApp().copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(
                        InAppStub.getModalWindow().copy(
                            inAppId = validId
                        )
                    )
                )
            )
        )
        coEvery {
            mockkInAppSegmentationRepository.fetchCustomerSegmentations()
        } throws CustomerSegmentationError(VolleyError())
        every {
            mockkInAppSegmentationRepository.setCustomerSegmentationStatus(any())
        } just runs
        val expectedResult = InAppStub.getModalWindow().copy(inAppId = validId)
        val actualResult = inAppProcessingManager.chooseInAppToShow(
            testInAppList, event
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `choose inApp to show geo error`() = runTest {
        val validId = "validId"
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingRegionNode().copy(
                    type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                )
            ),
            InAppStub.getInApp().copy(
                id = "124",
                targeting = InAppStub.getTargetingRegionNode().copy(
                    type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId2")
                )
            ),
            InAppStub.getInApp().copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(
                        InAppStub.getModalWindow().copy(
                            inAppId = validId
                        )
                    )
                )
            )
        )

        val inAppProcessingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = mockk {
                coEvery { fetchGeo() } throws GeoError(VolleyError())
            },
            inAppSegmentationRepository = mockkInAppSegmentationRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository
        )

        val expectedResult = InAppStub.getModalWindow().copy(inAppId = validId)
        val actualResult = inAppProcessingManager.chooseInAppToShow(
            testInAppList, event
        )
        assertEquals(expectedResult, actualResult)
    }
}
