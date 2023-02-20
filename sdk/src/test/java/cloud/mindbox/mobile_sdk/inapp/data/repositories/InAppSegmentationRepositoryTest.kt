package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
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
    fun `request segmentations success`() = runTest {
        inAppSegmentationRepository.unShownInApps = mutableListOf(InAppStub.getInApp())
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        val segCheckResponse = SegmentationCheckInAppStub.getSegmentationCheckResponse()
            .copy("Success", listOf())
        coEvery {
            GatewayManager.checkSegmentation(
                context = context,
                configuration = configuration,
                inAppMapper.mapToSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        } returns segCheckResponse
        every {
            inAppMapper.mapToSegmentationCheck(any())
        } returns SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        every {
            sessionStorageManager.inAppSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        } just runs
        inAppSegmentationRepository.fetchSegmentations()
        verify(exactly = 1) {
            sessionStorageManager.inAppSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        }
    }

    @Test
    fun `request segmentations no inApps`() = runTest {
        inAppSegmentationRepository.unShownInApps = mutableListOf()
        every {
            sessionStorageManager.segmentationFetchStatus =
                SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        } just runs
        inAppSegmentationRepository.fetchSegmentations()
        verify(exactly = 1) {
            sessionStorageManager.segmentationFetchStatus =
                SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        }

        coVerify(exactly = 0) {
            GatewayManager.checkSegmentation(
                context = context,
                configuration = configuration,
                inAppMapper.mapToSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        }
    }

    @Test
    fun `request segmentation error`() = runTest {
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
            sessionStorageManager.inAppSegmentations =
                SegmentationCheckInAppStub.getSegmentationCheckWrapper()
        } just runs
        coEvery {
            GatewayManager.checkSegmentation(
                context = context,
                configuration = configuration,
                inAppMapper.mapToSegmentationCheckRequest(listOf(InAppStub.getInApp()))
            )
        } throws VolleyError("test message")
        assertThrows(VolleyError::class.java) {
            runBlocking {
                inAppSegmentationRepository.fetchSegmentations()
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
            inAppSegmentationRepository.getSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation not fetched`() {
        every {
            sessionStorageManager.segmentationFetchStatus
        } returns SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
        assertEquals(
            SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED,
            inAppSegmentationRepository.getSegmentationFetched()
        )
    }

    @Test
    fun `get segmentation fetched error`() {
        every {
            sessionStorageManager.segmentationFetchStatus
        } throws Error()
        assertEquals(
            SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR,
            inAppSegmentationRepository.getSegmentationFetched()
        )
    }

    @Test
    fun `get inApps segmentations success`() {
        val expectedResult = listOf(SegmentationCheckInAppStub.getCustomerSegmentation())
        every {
            sessionStorageManager.inAppSegmentations?.customerSegmentations
        } returns expectedResult
        val actualResult = inAppSegmentationRepository.getSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations returns null`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        every {
            sessionStorageManager.inAppSegmentations?.customerSegmentations
        } returns null
        val actualResult = inAppSegmentationRepository.getSegmentations()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get inApps segmentations error`() {
        val expectedResult = emptyList<CustomerSegmentationInApp>()
        every {
            sessionStorageManager.inAppSegmentations?.customerSegmentations
        } throws Error()
        val actualResult = inAppSegmentationRepository.getSegmentations()
        assertEquals(expectedResult, actualResult)
    }


}