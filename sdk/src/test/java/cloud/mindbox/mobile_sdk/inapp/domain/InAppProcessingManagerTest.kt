package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppSegmentationRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import com.android.volley.VolleyError
import com.google.gson.Gson
import io.mockk.*
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
            cityId = "234", regionId = "regionId", countryId = "123"
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

    private val mockTimeProvider: TimeProvider = mockk()

    private val sessionStorageManager = spyk(SessionStorageManager(mockTimeProvider))

    private val context: Context = mockk(relaxed = true)
    private val inAppMapper: InAppMapper = mockk(relaxed = true)
    private val geoSerializationManager: GeoSerializationManager = mockk(relaxed = true)
    private val gatewayManager: GatewayManager = mockk(relaxed = true)

    private val inAppGeoRepositoryTestImpl: InAppGeoRepositoryImpl =
        spyk(
            InAppGeoRepositoryImpl(
                context = context,
                inAppMapper = inAppMapper,
                geoSerializationManager = geoSerializationManager,
                sessionStorageManager = sessionStorageManager,
                gatewayManager = gatewayManager
            )
        )

    private val inAppSegmentationRepositoryTestImpl: InAppSegmentationRepository =
        spyk(
            InAppSegmentationRepositoryImpl(
                inAppMapper = inAppMapper,
                sessionStorageManager = sessionStorageManager,
                gatewayManager = gatewayManager
            )
        )

    private fun setDIModule(
        geoRepository: InAppGeoRepository,
        segmentationRepository: InAppSegmentationRepository
    ) {
        every { MindboxDI.appModule } returns mockk {
            every { inAppGeoRepository } returns geoRepository
            every { inAppSegmentationRepository } returns segmentationRepository
            every { inAppRepository } returns mockInAppRepository
            every { gson } returns Gson()
        }
    }

    @Before
    fun onTestStart() {
        // mockk 'by mindboxInject { }'
        mockkObject(MindboxDI)
        setDIModule(mockkInAppGeoRepository, mockkInAppSegmentationRepository)
    }

    private val inAppProcessingManager = InAppProcessingManagerImpl(
        inAppGeoRepository = mockkInAppGeoRepository,
        inAppSegmentationRepository = mockkInAppSegmentationRepository,
        inAppContentFetcher = mockkInAppContentFetcher,
        inAppRepository = mockInAppRepository
    )

    private val inAppProcessingManagerTestImpl = InAppProcessingManagerImpl(
        inAppGeoRepository = inAppGeoRepositoryTestImpl,
        inAppSegmentationRepository = inAppSegmentationRepositoryTestImpl,
        inAppContentFetcher = mockkInAppContentFetcher,
        inAppRepository = mockInAppRepository
    )

    private fun setupTestGeoRepositoryForErrorScenario() {
        sessionStorageManager.geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        every { inAppGeoRepositoryTestImpl.getGeoFetchedStatus() } answers { callOriginal() }
        coEvery { inAppGeoRepositoryTestImpl.fetchGeo() } throws GeoError(VolleyError())
        every { inAppGeoRepositoryTestImpl.setGeoStatus(any()) } answers { callOriginal() }
    }

    private fun setupTestSegmentationRepositoryForErrorScenario() {
        sessionStorageManager.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        every { inAppSegmentationRepositoryTestImpl.getCustomerSegmentationFetched() } answers { callOriginal() }
        coEvery { inAppSegmentationRepositoryTestImpl.fetchCustomerSegmentations() } throws CustomerSegmentationError(VolleyError())
        coEvery { inAppSegmentationRepositoryTestImpl.setCustomerSegmentationStatus(any()) } answers { callOriginal() }
    }

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

    @Test
    fun `send inapptargeting for or node when geo return 500`() = runTest {
        setDIModule(inAppGeoRepositoryTestImpl, mockkInAppSegmentationRepository)
        setupTestGeoRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = TreeTargeting.UnionNode(
                type = TreeTargetingDto.UnionNodeDto.Companion.OR_JSON_NAME,
                nodes = listOf(
                    InAppStub.getTargetingCountryNode().copy(kind = Kind.NEGATIVE),
                    InAppStub.getTargetingTrueNode()
                )
            )
        )

        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)

        verify(exactly = 1) { mockInAppRepository.sendUserTargeted(any()) }
        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, sessionStorageManager.geoFetchStatus)
    }

    @Test
    fun `send inapptargeting for or node when customer segment return 500`() = runTest {
        setDIModule(mockkInAppGeoRepository, inAppSegmentationRepositoryTestImpl)
        setupTestSegmentationRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = TreeTargeting.UnionNode(
                type = TreeTargetingDto.UnionNodeDto.Companion.OR_JSON_NAME,
                nodes = listOf(
                    InAppStub.getTargetingSegmentNode().copy(kind = Kind.NEGATIVE),
                    InAppStub.getTargetingTrueNode()
                )
            )
        )
        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)
        verify(exactly = 1) { mockInAppRepository.sendUserTargeted(any()) }
        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR, sessionStorageManager.customerSegmentationFetchStatus)
    }

    @Test
    fun `send inapptargeting for or node when product segment return 500`() = runTest {
        every { mockkInAppSegmentationRepository.getProductSegmentationFetched(any()) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR

        val testInApp = InAppStub.getInApp().copy(
            targeting = TreeTargeting.UnionNode(
                type = TreeTargetingDto.UnionNodeDto.Companion.OR_JSON_NAME,
                nodes = listOf(
                    spyk(InAppStub.getTargetingViewProductSegmentNode().copy(kind = Kind.NEGATIVE)) {
                        coEvery { fetchTargetingInfo(any()) } throws ProductSegmentationError(VolleyError())
                    },
                    InAppStub.getTargetingTrueNode()
                )
            )
        )
        inAppProcessingManager.sendTargetedInApp(testInApp, InAppEventType.AppStartup)
        verify(exactly = 1) { mockInAppRepository.sendUserTargeted(any()) }
    }

    @Test
    fun `not send inapptargeting when geo return 500`() = runTest {
        setDIModule(inAppGeoRepositoryTestImpl, mockkInAppSegmentationRepository)
        setupTestGeoRepositoryForErrorScenario()

        val testInApp = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingCountryNode().copy(kind = Kind.NEGATIVE)
        )
        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)
        verify(exactly = 0) { mockInAppRepository.sendUserTargeted(any()) }
        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, sessionStorageManager.geoFetchStatus)
    }

    @Test
    fun `not send inapptargeting when customer segment return 500`() = runTest {
        setDIModule(mockkInAppGeoRepository, inAppSegmentationRepositoryTestImpl)
        setupTestSegmentationRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingSegmentNode().copy(kind = Kind.NEGATIVE)
        )
        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)
        verify(exactly = 0) { mockInAppRepository.sendUserTargeted(any()) }
        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR, sessionStorageManager.customerSegmentationFetchStatus)
    }

    @Test
    fun `not send inapptargeting when product segment return 500`() = runTest {
        every { mockkInAppSegmentationRepository.getProductSegmentationFetched(any()) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR

        val testInApp = InAppStub.getInApp().copy(
            targeting = spyk(InAppStub.getTargetingViewProductSegmentNode().copy(kind = Kind.NEGATIVE)) {
                coEvery { fetchTargetingInfo(any()) } throws ProductSegmentationError(VolleyError())
            }
        )
        inAppProcessingManager.sendTargetedInApp(testInApp, InAppEventType.AppStartup)
        verify(exactly = 0) { mockInAppRepository.sendUserTargeted(any()) }
    }

    @Test
    fun `send inapptargeting in union node when customer segment return 500 but geo success`() = runTest {
        setDIModule(mockkInAppGeoRepository, inAppSegmentationRepositoryTestImpl)
        setupTestSegmentationRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingUnionNode().copy(
                nodes = listOf(
                    InAppStub.getTargetingSegmentNode(),
                    InAppStub.getTargetingCityNode().copy(kind = Kind.POSITIVE, ids = listOf("234"))
                )
            )
        )

        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)

        verify(exactly = 1) { mockInAppRepository.sendUserTargeted(any()) }
    }

    @Test
    fun `send inapptargeting in union node when geo return 500 but customer segment success`() = runTest {
        setDIModule(inAppGeoRepositoryTestImpl, mockkInAppSegmentationRepository)
        setupTestGeoRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingUnionNode().copy(
                nodes = listOf(
                    InAppStub.getTargetingCityNode()
                        .copy(kind = Kind.POSITIVE, ids = listOf()),
                    InAppStub.getTargetingSegmentNode().copy(
                        kind = Kind.POSITIVE,
                        segmentationExternalId = "segmentationEI",
                        segmentExternalId = "segmentEI"
                    )
                )
            )
        )

        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)

        verify(exactly = 1) { mockInAppRepository.sendUserTargeted(any()) }
    }

    @Test
    fun `not send inapptargeting with union node with 2 geo when geo return 500`() = runTest {
        setDIModule(inAppGeoRepositoryTestImpl, mockkInAppSegmentationRepository)
        setupTestGeoRepositoryForErrorScenario()
        val testInApp = InAppStub.getInApp().copy(
            targeting = InAppStub.getTargetingUnionNode().copy(
                nodes = listOf(
                    InAppStub.getTargetingCityNode()
                        .copy(kind = Kind.POSITIVE, ids = listOf("234")),
                    InAppStub.getTargetingCountryNode()
                        .copy(kind = Kind.POSITIVE, ids = listOf("123"))
                )
            )
        )

        inAppProcessingManagerTestImpl.sendTargetedInApp(testInApp, InAppEventType.AppStartup)

        verify(exactly = 0) { mockInAppRepository.sendUserTargeted(any()) }
    }
}
