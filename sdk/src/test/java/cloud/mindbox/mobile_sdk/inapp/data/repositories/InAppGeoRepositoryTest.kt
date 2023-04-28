package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.GeoTargetingStub
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
internal class InAppGeoRepositoryTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppGeoRepository: InAppGeoRepositoryImpl

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var geoSerializationManager: GeoSerializationManager

    @MockK
    private lateinit var inAppMapper: InAppMapper

    @MockK
    private lateinit var configuration: Configuration

    @MockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @MockK
    private lateinit var gatewayManager: GatewayManager

    @Before
    fun onTestStart() {
        mockkObject(DbManager)
        mockkObject(MindboxPreferences)
        every {
            inAppGeoRepository.setGeoStatus(any())
        } just runs
    }

    @Test
    fun `fetch geo success test`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        every { configuration.domain } returns ""
        val geoTargetingDto = GeoTargetingStub.getGeoTargetingDto()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            gatewayManager.checkGeoTargeting(configuration = configuration)
        } returns geoTargetingDto

        val geoTargeting = GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            geoSerializationManager.serializeToGeoString(geoTargeting)
        } returns "{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}"
        inAppGeoRepository.fetchGeo()
        verify {
            MindboxPreferences.inAppGeo =
                "{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}"
        }
    }

    @Test
    fun `fetch geo network error test`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }

        every { configuration.domain } returns ""
        val geoTargetingDto = GeoTargetingStub.getGeoTargetingDto()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            gatewayManager.checkGeoTargeting(configuration = configuration)
        } throws VolleyError()

        val geoTargeting = GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            geoSerializationManager.serializeToGeoString(geoTargeting)
        } returns "{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}"
        assertThrows(VolleyError::class.java) {
            runBlocking {
                inAppGeoRepository.fetchGeo()
            }
        }
    }

    @Test
    fun `fetch geo non network error test`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }
        every { configuration.domain } returns ""
        val geoTargetingDto = GeoTargetingStub.getGeoTargetingDto()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            gatewayManager.checkGeoTargeting(configuration = configuration)
        } throws Error()

        val geoTargeting = GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            geoSerializationManager.serializeToGeoString(geoTargeting)
        } returns "{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}"
        assertThrows(Error::class.java) {
            runBlocking {
                inAppGeoRepository.fetchGeo()

            }
        }
    }

    @Test
    fun `get geo success`() {
        every { MindboxPreferences.inAppGeo } returns "{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}"
        val geoTargeting = GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123", regionId = "456", countryId = "789")
        every {
            geoSerializationManager.deserializeToGeoTargeting("{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}")
        } returns GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123", regionId = "456", countryId = "789")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }

    @Test
    fun `get geo empty string`() {
        val geoTargeting =
            GeoTargetingStub.getGeoTargeting().copy(cityId = "", regionId = "", countryId = "")
        every { MindboxPreferences.inAppGeo } returns ""
        every {
            geoSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        } returns GeoTargetingStub.getGeoTargeting().copy("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }

    @Test
    fun `get geo invalid json`() {
        every { MindboxPreferences.inAppGeo } returns "123"
        val geoTargeting =
            GeoTargetingStub.getGeoTargeting().copy(cityId = "", regionId = "", countryId = "")
        every {
            geoSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        } returns GeoTargetingStub.getGeoTargeting().copy("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }

    @Test
    fun `get geo fetched status success`() {
        every { sessionStorageManager.geoFetchStatus } returns GeoFetchStatus.GEO_FETCH_SUCCESS
        assertEquals(GeoFetchStatus.GEO_FETCH_SUCCESS, inAppGeoRepository.getGeoFetchedStatus())
    }

    @Test
    fun `get segmentation not fetched`() {
        every {
            sessionStorageManager.geoFetchStatus
        } returns GeoFetchStatus.GEO_NOT_FETCHED
        assertEquals(
            GeoFetchStatus.GEO_NOT_FETCHED,
            inAppGeoRepository.getGeoFetchedStatus()
        )
    }

    @Test
    fun `get geo fetched status error`() {
        every { sessionStorageManager.geoFetchStatus } throws Error()
        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, inAppGeoRepository.getGeoFetchedStatus())
    }
}
