package cloud.mindbox.mobile_sdk.inapp.data.repositories

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
    private val sessionStorageManager = mockk<SessionStorageManager>(relaxUnitFun = true) {
        every {
            currentSessionInApps
        } returns emptyList()
        every {
            currentSessionInApps = any()
        } just runs
    }

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
    }

    @Test
    fun `request customer segmentations success`() = runTest {
        every {
            sessionStorageManager.currentSessionInApps
        } returns mutableListOf(InAppStub.getInApp())
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
            inAppSegmentationRepository.setCustomerSegmentationStatus(any())
        } just runs

        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        every {
            sessionStorageManager setProperty "inAppCustomerSegmentations" value any<SegmentationCheckWrapper>()
        } just runs

        inAppSegmentationRepository.fetchCustomerSegmentations()

        verify {
            sessionStorageManager setProperty "customerSegmentationFetchStatus" value
                CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        }
        verify(exactly = 1) {
            sessionStorageManager setProperty "inAppCustomerSegmentations" value any<SegmentationCheckWrapper>()
        }
    }

    @Test
    fun `request customer segmentations no inApps`() = runTest {
        sessionStorageManager.currentSessionInApps = mutableListOf()
        every {
            sessionStorageManager.customerSegmentationFetchStatus =
                CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        } just runs
        inAppSegmentationRepository.fetchCustomerSegmentations()
        verify(exactly = 1) {
            sessionStorageManager.customerSegmentationFetchStatus =
                CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        }

        coVerify(exactly = 0) {
            gatewayManager.checkCustomerSegmentations(
                configuration = configuration,
                inAppMapper.mapToCustomerSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        }
    }

    @Test
    fun `request customer segmentation error`() = runTest {
        every {
            sessionStorageManager.currentSessionInApps
        } returns mutableListOf(InAppStub.getInApp())
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        every {
            sessionStorageManager.inAppCustomerSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        } just runs
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
        every {
            sessionStorageManager.inAppProductSegmentations["testSystem" to "testValue"]
        } answers {
            expectedResult
        }
        assertEquals(expectedResult, inAppSegmentationRepository.getProductSegmentations("testSystem" to "testValue"))
    }

    @Test
    fun `get product segmentation no segmentation`() {
        every {
            sessionStorageManager.inAppProductSegmentations[any()]
        } answers {
            null
        }
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
        every {
            sessionStorageManager.processedProductSegmentations["testSystem" to "testValue"]
        } answers {
            ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        }
        every {
            sessionStorageManager.processedProductSegmentations["testSystem" to "testValue"] = ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        } just runs
        coEvery {
            gatewayManager.checkProductSegmentation(any(), any())
        } answers {
            result
        }
        every { sessionStorageManager.inAppProductSegmentations } returns HashMap()
        every {
            sessionStorageManager.inAppProductSegmentations = any()
        } just runs
        every {
            sessionStorageManager.inAppProductSegmentations["testSystem" to "testValue"]
        } answers {
            setOf(expectedResult)
        }
        every {
            sessionStorageManager.inAppProductSegmentations["testSystem" to "testValue"] = setOf(expectedResult)
        } just runs
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        inAppSegmentationRepository.fetchProductSegmentation("testSystem" to "testValue")
        assertEquals(
            expectedResult,
            sessionStorageManager.inAppProductSegmentations["testSystem" to "testValue"]?.firstOrNull()
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
        every {
            sessionStorageManager.customerSegmentationFetchStatus
        } returns CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation not fetched`() {
        every {
            sessionStorageManager.customerSegmentationFetchStatus
        } returns CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation fetched error`() {
        every {
            sessionStorageManager.customerSegmentationFetchStatus
        } throws Error()
        assertEquals(
            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get inApps segmentations success`() {
        val expectedResult = listOf(SegmentationCheckInAppStub.getCustomerSegmentation())
        every {
            sessionStorageManager.inAppCustomerSegmentations?.customerSegmentations
        } returns expectedResult
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations returns null`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        every {
            sessionStorageManager.inAppCustomerSegmentations?.customerSegmentations
        } returns null
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations error`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        every {
            sessionStorageManager.inAppCustomerSegmentations?.customerSegmentations
        } throws Error()
        val actualResult = inAppSegmentationRepository.getCustomerSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `setLastCustomerSegmentationError saves error details`() {
        val errorDetails = "Customer segmentation request failed"
        every { sessionStorageManager.lastCustomerSegmentationError = any() } just runs

        inAppSegmentationRepository.setLastCustomerSegmentationError(errorDetails)

        verify(exactly = 1) { sessionStorageManager.lastCustomerSegmentationError = errorDetails }
    }

    @Test
    fun `getLastCustomerSegmentationError returns saved error`() {
        val errorDetails = "Segmentation fetch failed"
        every { sessionStorageManager.lastCustomerSegmentationError } returns errorDetails

        val result = inAppSegmentationRepository.getLastCustomerSegmentationError()

        assertEquals(errorDetails, result)
    }

    @Test
    fun `getLastCustomerSegmentationError returns null when no error saved`() {
        every { sessionStorageManager.lastCustomerSegmentationError } returns null

        val result = inAppSegmentationRepository.getLastCustomerSegmentationError()

        assertEquals(null, result)
    }

    @Test
    fun `setLastProductSegmentationError saves error details`() {
        val product = "testSystem" to "testValue"
        val errorDetails = "Product segmentation request failed"
        val errorsMap = mutableMapOf<Pair<String, String>, String>()
        every { sessionStorageManager.lastProductSegmentationErrors } returns errorsMap

        inAppSegmentationRepository.setLastProductSegmentationError(product, errorDetails)

        assertEquals(errorDetails, errorsMap[product])
    }

    @Test
    fun `getLastProductSegmentationError returns saved error`() {
        val product = "testSystem" to "testValue"
        val errorDetails = "Product segmentation failed"
        every { sessionStorageManager.lastProductSegmentationErrors[product] } returns errorDetails

        val result = inAppSegmentationRepository.getLastProductSegmentationError(product)

        assertEquals(errorDetails, result)
    }

    @Test
    fun `getLastProductSegmentationError returns null when no error saved`() {
        val product = "testSystem" to "testValue"
        every { sessionStorageManager.lastProductSegmentationErrors[product] } returns null

        val result = inAppSegmentationRepository.getLastProductSegmentationError(product)

        assertEquals(null, result)
    }
}
