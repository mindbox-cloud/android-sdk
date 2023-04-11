package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationResponseWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

class InAppSegmentationRepositoryTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var inAppMapper: InAppMapper

    @MockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @OverrideMockKs
    private lateinit var inAppSegmentationRepository: InAppSegmentationRepositoryImpl

    @MockK
    private lateinit var configuration: Configuration


    @Before
    fun onTestStart() {
        mockkObject(DbManager)
        mockkObject(GatewayManager)
        mockkObject(MindboxPreferences)
        mockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `request customer segmentations success`() = runTest {
        every {
            inAppSegmentationRepository.setCustomerSegmentationStatus(any())
        } just runs
        inAppSegmentationRepository.unShownInApps = mutableListOf(InAppStub.getInApp())
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        val segCheckResponse = SegmentationCheckInAppStub.getSegmentationCheckResponse()
            .copy("Success", listOf())
        coEvery {
            GatewayManager.checkCustomerSegmentations(
                context = context,
                configuration = configuration,
                inAppMapper.mapToCustomerSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        } returns segCheckResponse
        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        every {
            sessionStorageManager.inAppCustomerSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        } just runs
        inAppSegmentationRepository.fetchCustomerSegmentations()
        verify(exactly = 1) {
            sessionStorageManager.inAppCustomerSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        }
    }

    @Test
    fun `request customer segmentations no inApps`() = runTest {
        inAppSegmentationRepository.unShownInApps = mutableListOf()
        every {
            sessionStorageManager.segmentationFetchStatus =
                SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        } just runs
        inAppSegmentationRepository.fetchCustomerSegmentations()
        verify(exactly = 1) {
            sessionStorageManager.segmentationFetchStatus =
                SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        }

        coVerify(exactly = 0) {
            GatewayManager.checkCustomerSegmentations(
                context = context,
                configuration = configuration,
                inAppMapper.mapToCustomerSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        }
    }

    @Test
    fun `request customer segmentation error`() = runTest {
        inAppSegmentationRepository.unShownInApps = mutableListOf(InAppStub.getInApp())
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
            GatewayManager.checkCustomerSegmentations(
                context = context,
                configuration = configuration,
                inAppMapper.mapToCustomerSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
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
            sessionStorageManager.inAppProductSegmentations["testId"]
        } answers {
            expectedResult
        }
        assertEquals(expectedResult, inAppSegmentationRepository.getProductSegmentations("testId"))
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
            inAppSegmentationRepository.getProductSegmentations("testId1")
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
        coEvery {
            GatewayManager.checkProductSegmentation(
                context = context,
                configuration = configuration,
                inAppMapper.mapToProductSegmentationCheckRequest("test1" to "test2", listOf())
            )
        } answers {
            result
        }
        every { sessionStorageManager.inAppProductSegmentations } returns HashMap()
        every {
            sessionStorageManager.inAppProductSegmentations = any()
        } just runs
        every {
            sessionStorageManager.inAppProductSegmentations["test2"]
        } answers {
            setOf(expectedResult)
        }
        every {
            sessionStorageManager.inAppProductSegmentations["test2"] = setOf(expectedResult)
        } just runs
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        inAppSegmentationRepository.fetchProductSegmentation("test1" to "test2")
        assertEquals(expectedResult, sessionStorageManager.inAppProductSegmentations["test2"]?.firstOrNull())
    }

    @Test
    fun `request product segmentation error`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        coEvery {
            GatewayManager.checkProductSegmentation(
                context = context,
                configuration = configuration,
                inAppMapper.mapToProductSegmentationCheckRequest("test1" to "test2", listOf())
            )
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
            sessionStorageManager.segmentationFetchStatus
        } returns SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        assertEquals(
            SegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation not fetched`() {
        every {
            sessionStorageManager.segmentationFetchStatus
        } returns SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        assertEquals(
            SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED,
            inAppSegmentationRepository.getCustomerSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation fetched error`() {
        every {
            sessionStorageManager.segmentationFetchStatus
        } throws Error()
        assertEquals(
            SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR,
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


}