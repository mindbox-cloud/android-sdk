package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionState
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckWrapper
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InAppSegmentationRepositoryTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val inAppMapper = mockk<InAppMapper>()
    private val sessionState = SessionState()
    private val sessionStorageManager = mockk<SessionStorageManager>(relaxUnitFun = true) { every { state } returns sessionState }

    private val gatewayManager = mockk<GatewayManager>()

    private val inAppSegmentationRepository = InAppSegmentationRepositoryImpl(
        inAppMapper = mockk(relaxed = true),
        sessionStorageManager = sessionStorageManager,
        gatewayManager = gatewayManager
    )

    @MockK
    private lateinit var configuration: Configuration

    @Before
    fun onTestStart() {
        mockkObject(DbManager)
        mockkObject(MindboxPreferences)
        // The fetch pre-checks the latched status under its mutex before going to the network.
        sessionStorageManager.state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    }

    @Test
    fun `request customer segmentations success`() = runTest {
        sessionStorageManager.state.currentSessionInApps = mutableListOf(InAppStub.getInApp())
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }

        val segCheckResponse = SegmentationCheckInAppStub.getSegmentationCheckResponse()
            .copy("Success", listOf())
        coEvery {
            gatewayManager.checkCustomerSegmentations(any(), any())
        } returns segCheckResponse

        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()

        inAppSegmentationRepository.fetchCustomerSegmentations()

        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS, sessionStorageManager.state.customerSegmentationFetchStatus)
        assertNotNull(sessionStorageManager.state.inAppCustomerSegmentations)
    }

    @Test
    fun `request customer segmentations no inApps`() = runTest {
        sessionStorageManager.state.currentSessionInApps = mutableListOf()
        inAppSegmentationRepository.fetchCustomerSegmentations()
        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR, sessionStorageManager.state.customerSegmentationFetchStatus)

        coVerify(exactly = 0) {
            gatewayManager.checkCustomerSegmentations(
                configuration = configuration,
                inAppMapper.mapToCustomerSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        }
    }

    @Test
    fun `request customer segmentation error`() = runTest {
        sessionStorageManager.state.currentSessionInApps = mutableListOf(InAppStub.getInApp())
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        coEvery {
            gatewayManager.checkCustomerSegmentations(any(), any())
        } throws VolleyError("test message")
        assertThrows(VolleyError::class.java) {
            runBlocking {
                inAppSegmentationRepository.fetchCustomerSegmentations()
            }
        }
    }

    @Test
    fun `get product segmentation success`() {
        val expectedResult = setOf(
            ProductSegmentationResponseWrapper(
                productSegmentations = listOf(
                    ProductSegmentationResponseStub.getProductResponse().copy(
                        productList = listOf(
                            ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                .copy(
                                    segmentationExternalId = "segmentationExternalId",
                                    segmentExternalId = "segmentExternalId"
                                )
                        )
                    )
                )
            )
        )
        sessionStorageManager.state.inAppProductSegmentations["testSystem" to "testValue"] = expectedResult
        assertEquals(expectedResult, inAppSegmentationRepository.getProductSegmentations("testSystem" to "testValue"))
    }

    @Test
    fun `get product segmentation no segmentation`() {
        assertEquals(
            emptySet<Set<ProductSegmentationResponseWrapper>>(),
            inAppSegmentationRepository.getProductSegmentations("testSystem" to "testValue")
        )
    }

    @Test
    fun `request product segmentation success`() = runTest {
        val result = ProductSegmentationResponseStub.getProductSegmentationResponseDto()
        val expectedResult =
            ProductSegmentationResponseWrapper(
                productSegmentations = listOf(
                    ProductSegmentationResponseStub.getProductResponse().copy(
                        productList = listOf(
                            ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                .copy(
                                    segmentationExternalId = "test2",
                                    segmentExternalId = "test2"
                                )
                        )
                    )
                )
            )
        every {
            inAppMapper.mapToProductSegmentationResponse(any())
        } answers {
            expectedResult
        }
        val dtoResult = ProductSegmentationRequestStub.getProductSegmentationRequestDto()
        every {
            inAppMapper.mapToProductSegmentationCheckRequest("testSystem" to "testValue", listOf())
        } returns dtoResult
        sessionStorageManager.state.processedProductSegmentations["testSystem" to "testValue"] = ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        coEvery {
            gatewayManager.checkProductSegmentation(any(), any())
        } answers {
            result
        }
        sessionStorageManager.state.inAppProductSegmentations["testSystem" to "testValue"] = setOf(expectedResult)
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        inAppSegmentationRepository.fetchProductSegmentation("testSystem" to "testValue")
        assertEquals(
            expectedResult,
            sessionStorageManager.state.inAppProductSegmentations["testSystem" to "testValue"]?.firstOrNull()
        )
    }

    @Test
    fun `request product segmentation error`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        val dtoResult = ProductSegmentationRequestStub.getProductSegmentationRequestDto()
        every {
            inAppMapper.mapToProductSegmentationCheckRequest("test1" to "test2", listOf())
        } returns dtoResult
        coEvery {
            gatewayManager.checkProductSegmentation(any(), any())
        } throws VolleyError("test message")
        assertThrows(VolleyError::class.java) {
            runBlocking {
                inAppSegmentationRepository.fetchProductSegmentation("test1" to "test2")
            }
        }
    }

    @Test
    fun `get segmentation fetched success`() {
        sessionStorageManager.state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation not fetched`() {
        sessionStorageManager.state.customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation fetched error`() {
        every { sessionStorageManager.state } throws Error()
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get inApps segmentations success`() {
        val expectedResult = listOf(SegmentationCheckInAppStub.getCustomerSegmentation())
        sessionStorageManager.state.inAppCustomerSegmentations = SegmentationCheckWrapper("", expectedResult)
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations returns null`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations error`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        every { sessionStorageManager.state } throws Error()
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }
}
