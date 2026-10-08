package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionState
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoError
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    private val sessionState = SessionState()

    @MockK
    private lateinit var gatewayManager: GatewayManager

    @Before
    fun onTestStart() {
        every { sessionStorageManager.state } returns sessionState
        sessionStorageManager.state.geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        mockkObject(DbManager)
        mockkObject(MindboxPreferences)
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
        sessionStorageManager.state.geoFetchStatus = GeoFetchStatus.GEO_FETCH_SUCCESS
        assertEquals(GeoFetchStatus.GEO_FETCH_SUCCESS, inAppGeoRepository.getGeoFetchedStatus())
    }

    @Test
    fun `get segmentation not fetched`() {
        sessionStorageManager.state.geoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
        assertEquals(
            GeoFetchStatus.GEO_NOT_FETCHED,
            inAppGeoRepository.getGeoFetchedStatus()
        )
    }

    @Test
    fun `get geo fetched status error`() {
        every { sessionStorageManager.state } throws Error()
        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, inAppGeoRepository.getGeoFetchedStatus())
    }

    @Test
    fun `failed geo fetch is cached for the session`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers { flow { emit(configuration) } }
        coEvery { gatewayManager.checkGeoTargeting(configuration = configuration) } throws GeoError(VolleyError("timeout"))

        assertTrue(runCatching { inAppGeoRepository.fetchGeo() }.exceptionOrNull() is GeoError)
        inAppGeoRepository.fetchGeo()

        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, sessionState.geoFetchStatus)
        coVerify(exactly = 1) { gatewayManager.checkGeoTargeting(any()) }
    }

    @Test
    fun `a geo fetch the session reset outlived writes into its own session only`() = runTest {
        val nextSession = SessionState()
        coEvery { DbManager.listenConfigurations() } answers { flow { emit(configuration) } }
        coEvery { gatewayManager.checkGeoTargeting(configuration = configuration) } answers {
            every { sessionStorageManager.state } returns nextSession
            throw GeoError(VolleyError("timeout"))
        }

        runCatching { inAppGeoRepository.fetchGeo() }

        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, sessionState.geoFetchStatus)
        assertEquals(GeoFetchStatus.GEO_NOT_FETCHED, nextSession.geoFetchStatus)
    }

    @Test
    fun `a geo request the queue dropped without an answer ends as a cached fetch error`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers { flow { emit(configuration) } }
        coEvery { gatewayManager.checkGeoTargeting(configuration = configuration) } coAnswers { awaitCancellation() }

        assertTrue(runCatching { inAppGeoRepository.fetchGeo() }.exceptionOrNull() is GeoError)
        assertEquals(GeoFetchStatus.GEO_FETCH_ERROR, sessionState.geoFetchStatus)
    }
}
