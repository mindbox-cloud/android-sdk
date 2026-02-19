package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppSegmentationRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppTargetingErrorRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppTargetingErrorRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import com.android.volley.NetworkResponse
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
    private val inAppFailureTracker: InAppFailureTracker = mockk(relaxed = true)
    private val inAppTargetingErrorRepository: InAppTargetingErrorRepository =
        spyk(InAppTargetingErrorRepositoryImpl(sessionStorageManager))

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
        segmentationRepository: InAppSegmentationRepository,
        targetingErrorRepository: InAppTargetingErrorRepository = inAppTargetingErrorRepository
    ) {
        val appModuleMock = mockk<cloud.mindbox.mobile_sdk.di.modules.AppModule>(relaxed = true)
        every { appModuleMock.inAppGeoRepository } returns geoRepository
        every { appModuleMock.inAppSegmentationRepository } returns segmentationRepository
        every { appModuleMock.inAppTargetingErrorRepository } returns targetingErrorRepository
        every { appModuleMock.inAppRepository } returns mockInAppRepository
        every { appModuleMock.gson } returns Gson()
        every { appModuleMock.sessionStorageManager } returns sessionStorageManager
        every { appModuleMock.inAppProcessingManager } returns inAppProcessingManager
        every { MindboxDI.appModule } returns appModuleMock
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
        inAppTargetingErrorRepository = inAppTargetingErrorRepository,
        inAppContentFetcher = mockkInAppContentFetcher,
        inAppRepository = mockInAppRepository,
        inAppFailureTracker = inAppFailureTracker
    )

    private val inAppProcessingManagerTestImpl = InAppProcessingManagerImpl(
        inAppGeoRepository = inAppGeoRepositoryTestImpl,
        inAppSegmentationRepository = inAppSegmentationRepositoryTestImpl,
        inAppTargetingErrorRepository = inAppTargetingErrorRepository,
        inAppContentFetcher = mockkInAppContentFetcher,
        inAppRepository = mockInAppRepository,
        inAppFailureTracker = inAppFailureTracker
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
        val expectedResult = InAppStub
            .getInApp()
            .copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(
                        InAppStub.getModalWindow().copy(
                            inAppId = validId
                        )
                    )
                )
            )

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
                            variants = listOf(
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
    fun `choose inApp to show chooses WebView inApp when targeting matches`() = runTest {
        val validId = "webview-valid-id"
        val expectedResult = InAppStub.getInApp().copy(
            id = validId,
            targeting = InAppStub.getTargetingTrueNode(),
            form = InAppStub.getInApp().form.copy(
                variants = listOf(InAppStub.getWebView().copy(inAppId = validId))
            )
        )
        val actualResult = inAppProcessingManager.chooseInAppToShow(
            listOf(
                InAppStub.getInApp().copy(
                    id = "123",
                    targeting = InAppStub.getTargetingRegionNode().copy(
                        type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                    )
                ),
                InAppStub.getInApp().copy(
                    id = validId,
                    targeting = InAppStub.getTargetingTrueNode(),
                    form = InAppStub.getInApp().form.copy(
                        variants = listOf(InAppStub.getWebView().copy(inAppId = validId))
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
                    variants = listOf(
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
        val expectedResult = InAppStub.getInApp().copy(
            id = validId,
            targeting = InAppStub.getTargetingTrueNode(),
            form = InAppStub.getInApp().form.copy(
                variants = listOf(
                    InAppStub.getModalWindow().copy(
                        inAppId = validId
                    )
                )
            )
        )
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
                    variants = listOf(
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
                every { getGeoFetchedStatus() } returns GeoFetchStatus.GEO_FETCH_ERROR
                every { setGeoStatus(any()) } just runs
            },
            inAppSegmentationRepository = mockkInAppSegmentationRepository,
            inAppTargetingErrorRepository = mockk(relaxed = true),
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = mockk(relaxed = true)
        )

        val expectedResult = InAppStub.getInApp().copy(
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
                type = TreeTargetingDto.UnionNodeDto.OR_JSON_NAME,
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
                type = TreeTargetingDto.UnionNodeDto.OR_JSON_NAME,
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
                type = TreeTargetingDto.UnionNodeDto.OR_JSON_NAME,
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

    @Test
    fun `choose inApp to show tracks product segmentation failure when ViewProductSegmentNode has error`() = runTest {
        val viewProductBody = """{
            "viewProduct": {
                "product": {
                    "ids": {
                        "website": "ProductRandomName"
                    }
                }
            }
        }""".trimIndent()
        val product = "website" to "ProductRandomName"
        val viewProductEvent = InAppEventType.OrdinalEvent(
            EventType.SyncOperation("viewProduct"),
            viewProductBody
        )
        val inAppWithProductSegId = "inAppWithProductSeg"
        val validId = "validId"
        val serverError = VolleyError(NetworkResponse(500, null, false, 0, emptyList()))
        val mockSegmentationRepo = mockk<InAppSegmentationRepository> {
            every { getCustomerSegmentationFetched() } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            every { getCustomerSegmentations() } returns listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                    segmentation = "segmentationEI", segment = "segmentEI"
                )
            )
            coEvery { fetchCustomerSegmentations() } just runs
            every { getProductSegmentationFetched(product) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            coEvery { fetchProductSegmentation(product) } throws ProductSegmentationError(serverError)
            every { getProductSegmentations(product) } returns emptySet<ProductSegmentationResponseWrapper?>()
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every {
                getError(TargetingErrorKey.ProductSegmentation(product))
            } returns "Product segmentation fetch failed. statusCode=500"
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(mockkInAppGeoRepository, mockSegmentationRepo, targetingErrorRepository)
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = mockkInAppGeoRepository,
            inAppSegmentationRepository = mockSegmentationRepo,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = inAppWithProductSegId,
                targeting = InAppStub.getTargetingUnionNode().copy(
                    nodes = listOf(
                        InAppStub.viewProductSegmentNode.copy(
                            kind = Kind.POSITIVE,
                            segmentationExternalId = "segmentationExternalId",
                            segmentExternalId = "segmentExternalId"
                        )
                    )
                ),
                form = InAppStub.getInApp().form.copy(
                    listOf(InAppStub.getModalWindow().copy(inAppId = inAppWithProductSegId))
                )
            ),
            InAppStub.getInApp().copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(InAppStub.getModalWindow().copy(inAppId = validId))
                )
            )
        )

        val result = processingManager.chooseInAppToShow(testInAppList, viewProductEvent)

        assertNotNull(result)
        assertEquals(validId, result?.id)
        verify(exactly = 1) {
            failureTracker.collectFailure(
                inAppId = inAppWithProductSegId,
                failureReason = FailureReason.PRODUCT_SEGMENT_REQUEST_FAILED,
                errorDetails = "Product segmentation fetch failed. statusCode=500"
            )
        }
        verify(exactly = 1) { failureTracker.clearFailures() }
        verify(exactly = 0) { failureTracker.sendCollectedFailures() }
    }

    @Test
    fun `choose inApp to show geo error saves last geo error details`() = runTest {
        val errorDetails = "Geo fetch failed. statusCode=500"
        val geoRepo = mockk<InAppGeoRepository> {
            coEvery { fetchGeo() } throws GeoError(VolleyError())
            every { getGeoFetchedStatus() } returns GeoFetchStatus.GEO_FETCH_ERROR
            every { setGeoStatus(any()) } just runs
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every { getError(TargetingErrorKey.Geo) } returns errorDetails
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(geoRepo, mockkInAppSegmentationRepository, targetingErrorRepository)
        every { geoRepo.getGeo() } returns GeoTargetingStub.getGeoTargeting().copy(
            cityId = "234", regionId = "regionId", countryId = "123"
        )
        val validId = "validId"
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingRegionNode().copy(
                    type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                )
            ),
            InAppStub.getInApp().copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(InAppStub.getModalWindow().copy(inAppId = validId))
                )
            )
        )
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = geoRepo,
            inAppSegmentationRepository = mockkInAppSegmentationRepository,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )

        val result = processingManager.chooseInAppToShow(testInAppList, event)

        assertNotNull(result)
        assertEquals(validId, result?.id)
        verify(exactly = 1) {
            failureTracker.collectFailure(
                inAppId = "123",
                failureReason = FailureReason.GEO_TARGETING_FAILED,
                errorDetails = errorDetails
            )
        }
        verify(exactly = 1) { failureTracker.clearFailures() }
        verify(exactly = 0) { failureTracker.sendCollectedFailures() }
    }

    @Test
    fun `trackTargetingErrorIfAny collects customer segmentation failure when error was saved`() = runTest {
        val errorDetails = "Customer segmentation fetch failed. statusCode=500"
        val segmentationRepo = mockk<InAppSegmentationRepository> {
            coEvery { fetchCustomerSegmentations() } throws CustomerSegmentationError(VolleyError())
            every { getCustomerSegmentationFetched() } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            every { setCustomerSegmentationStatus(any()) } just runs
            every { getCustomerSegmentations() } returns listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                    segmentation = "segmentationEI", segment = "segmentEI"
                )
            )
            every { getProductSegmentationFetched(any()) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every { getError(TargetingErrorKey.CustomerSegmentation) } returns errorDetails
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(mockkInAppGeoRepository, segmentationRepo, targetingErrorRepository)
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = mockkInAppGeoRepository,
            inAppSegmentationRepository = segmentationRepo,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingSegmentNode().copy(
                    type = "",
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "segmentationEI",
                    segmentExternalId = "segmentEI"
                ),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = "123"))
                )
            ),
            InAppStub.getInApp().copy(
                id = "validId",
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = "validId"))
                )
            )
        )

        val result = processingManager.chooseInAppToShow(testInAppList, event)

        assertNotNull(result)
        assertEquals("validId", result?.id)
        verify(exactly = 1) {
            failureTracker.collectFailure(
                inAppId = "123",
                failureReason = FailureReason.CUSTOMER_SEGMENT_REQUEST_FAILED,
                errorDetails = errorDetails
            )
        }
    }

    @Test
    fun `trackTargetingErrorIfAny does not collect customer segmentation failure when error was not saved`() = runTest {
        val segmentationRepo = mockk<InAppSegmentationRepository> {
            coEvery { fetchCustomerSegmentations() } throws CustomerSegmentationError(VolleyError())
            every { getCustomerSegmentationFetched() } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            every { setCustomerSegmentationStatus(any()) } just runs
            every { getCustomerSegmentations() } returns listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                    segmentation = "segmentationEI", segment = "segmentEI"
                )
            )
            every { getProductSegmentationFetched(any()) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every { getError(TargetingErrorKey.CustomerSegmentation) } returns null
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(mockkInAppGeoRepository, segmentationRepo, targetingErrorRepository)
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = mockkInAppGeoRepository,
            inAppSegmentationRepository = segmentationRepo,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingSegmentNode().copy(
                    type = "",
                    kind = Kind.POSITIVE,
                    segmentationExternalId = "segmentationEI",
                    segmentExternalId = "segmentEI"
                ),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = "123"))
                )
            ),
            InAppStub.getInApp().copy(
                id = "validId",
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = "validId"))
                )
            )
        )

        val result = processingManager.chooseInAppToShow(testInAppList, event)

        assertNotNull(result)
        assertEquals("validId", result?.id)
        verify(exactly = 0) {
            failureTracker.collectFailure(
                inAppId = "123",
                failureReason = FailureReason.CUSTOMER_SEGMENT_REQUEST_FAILED,
                errorDetails = any()
            )
        }
    }

    @Test
    fun `trackTargetingErrorIfAny does not collect geo failure when error was not saved`() = runTest {
        val geoRepo = mockk<InAppGeoRepository> {
            coEvery { fetchGeo() } throws GeoError(VolleyError())
            every { getGeoFetchedStatus() } returns GeoFetchStatus.GEO_FETCH_ERROR
            every { setGeoStatus(any()) } just runs
            every { getGeo() } returns GeoTargetingStub.getGeoTargeting().copy(
                cityId = "234", regionId = "regionId", countryId = "123"
            )
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every { getError(TargetingErrorKey.Geo) } returns null
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(geoRepo, mockkInAppSegmentationRepository, targetingErrorRepository)
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = geoRepo,
            inAppSegmentationRepository = mockkInAppSegmentationRepository,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = "123",
                targeting = InAppStub.getTargetingRegionNode().copy(
                    type = "", kind = Kind.POSITIVE, ids = listOf("otherRegionId")
                ),
                form = InAppStub.getInApp().form.copy(
                    listOf(InAppStub.getModalWindow().copy(inAppId = "123"))
                )
            ),
            InAppStub.getInApp().copy(
                id = "validId",
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    listOf(InAppStub.getModalWindow().copy(inAppId = "validId"))
                )
            )
        )

        val result = processingManager.chooseInAppToShow(testInAppList, event)

        assertNotNull(result)
        assertEquals("validId", result?.id)
        verify(exactly = 0) {
            failureTracker.collectFailure(
                inAppId = "123",
                failureReason = FailureReason.GEO_TARGETING_FAILED,
                errorDetails = any()
            )
        }
    }

    @Test
    fun `trackTargetingErrorIfAny does not collect product segmentation failure when error was not saved`() = runTest {
        val viewProductBody = """{
            "viewProduct": {
                "product": {
                    "ids": {
                        "website": "ProductRandomName"
                    }
                }
            }
        }""".trimIndent()
        val product = "website" to "ProductRandomName"
        val viewProductEvent = InAppEventType.OrdinalEvent(
            EventType.SyncOperation("viewProduct"),
            viewProductBody
        )
        val inAppWithProductSegId = "inAppWithProductSeg"
        val validId = "validId"
        val mockSegmentationRepo = mockk<InAppSegmentationRepository> {
            every { getCustomerSegmentationFetched() } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            every { getCustomerSegmentations() } returns listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation().copy(
                    segmentation = "segmentationEI", segment = "segmentEI"
                )
            )
            coEvery { fetchCustomerSegmentations() } just runs
            every { getProductSegmentationFetched(product) } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
            coEvery { fetchProductSegmentation(product) } throws ProductSegmentationError(VolleyError())
            every { getProductSegmentations(product) } returns emptySet<ProductSegmentationResponseWrapper?>()
        }
        val targetingErrorRepository = mockk<InAppTargetingErrorRepository> {
            every { getError(TargetingErrorKey.ProductSegmentation(product)) } returns null
            every { saveError(any(), any()) } just runs
            every { clearErrors() } just runs
        }
        setDIModule(mockkInAppGeoRepository, mockSegmentationRepo, targetingErrorRepository)
        val failureTracker = mockk<InAppFailureTracker>(relaxed = true)
        val processingManager = InAppProcessingManagerImpl(
            inAppGeoRepository = mockkInAppGeoRepository,
            inAppSegmentationRepository = mockSegmentationRepo,
            inAppTargetingErrorRepository = targetingErrorRepository,
            inAppContentFetcher = mockkInAppContentFetcher,
            inAppRepository = mockInAppRepository,
            inAppFailureTracker = failureTracker
        )
        val testInAppList = listOf(
            InAppStub.getInApp().copy(
                id = inAppWithProductSegId,
                targeting = InAppStub.getTargetingUnionNode().copy(
                    nodes = listOf(
                        InAppStub.viewProductSegmentNode.copy(
                            kind = Kind.POSITIVE,
                            segmentationExternalId = "segmentationExternalId",
                            segmentExternalId = "segmentExternalId"
                        )
                    )
                ),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = inAppWithProductSegId))
                )
            ),
            InAppStub.getInApp().copy(
                id = validId,
                targeting = InAppStub.getTargetingTrueNode(),
                form = InAppStub.getInApp().form.copy(
                    variants = listOf(InAppStub.getModalWindow().copy(inAppId = validId))
                )
            )
        )

        val result = processingManager.chooseInAppToShow(testInAppList, viewProductEvent)

        assertNotNull(result)
        assertEquals(validId, result?.id)
        verify(exactly = 0) {
            failureTracker.collectFailure(
                inAppId = inAppWithProductSegId,
                failureReason = FailureReason.PRODUCT_SEGMENT_REQUEST_FAILED,
                errorDetails = any()
            )
        }
    }
}
