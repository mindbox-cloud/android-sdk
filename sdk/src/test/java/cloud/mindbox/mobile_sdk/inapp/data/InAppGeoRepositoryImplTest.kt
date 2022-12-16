package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.android.volley.VolleyError
import com.google.gson.Gson
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class InAppGeoRepositoryImplTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppGeoRepository: InAppGeoRepositoryImpl

    @MockK
    private lateinit var gson: Gson

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var inAppMessageMapper: InAppMessageMapper

    @MockK
    private lateinit var configuration: Configuration

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        mockkObject(DbManager)
        mockkObject(GatewayManager)
        mockkObject(MindboxPreferences)
        mockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `fetch geo success test`() = runTest {
        coEvery { DbManager.listenConfigurations() } answers {
            flow {
                emit(configuration)
            }
        }

        every { configuration.domain } returns ""
        val geoTargetingDto = GeoTargetingDto(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            GatewayManager.checkGeoTargeting(context = context, configuration = configuration)
        } returns geoTargetingDto

        val geoTargeting = GeoTargeting(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            gson.toJson(geoTargeting)
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
        val geoTargetingDto = GeoTargetingDto(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            GatewayManager.checkGeoTargeting(context = context, configuration = configuration)
        } throws VolleyError()

        val geoTargeting = GeoTargeting(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            gson.toJson(geoTargeting)
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
        val geoTargetingDto = GeoTargetingDto(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            GatewayManager.checkGeoTargeting(context = context, configuration = configuration)
        } throws Error()

        val geoTargeting = GeoTargeting(cityId = "123", regionId = "456", countryId = "798")
        coEvery {
            inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(geoTargetingDto)
        } returns geoTargeting
        every {
            gson.toJson(geoTargeting)
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
        val geoTargeting = GeoTargeting(cityId = "123", regionId = "456", countryId = "789")
        every {
            gson.fromJson("{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}",
                GeoTargeting::class.java)
        } returns GeoTargeting(cityId = "123", regionId = "456", countryId = "789")
        assertEquals(geoTargeting, inAppGeoRepository.geoGeo())
    }

    @Test
    fun `get geo empty string`() {
        val geoTargeting = GeoTargeting(cityId = "", regionId = "", countryId = "")
        every { MindboxPreferences.inAppGeo } returns ""
        every {
            gson.fromJson(MindboxPreferences.inAppGeo,
                GeoTargeting::class.java)
        } returns GeoTargeting("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.geoGeo())
    }

    @Test
    fun `get geo null`() {
        val geoTargeting = GeoTargeting(cityId = "", regionId = "", countryId = "")
        every { MindboxPreferences.inAppGeo } returns ""
        every {
            gson.fromJson(MindboxPreferences.inAppGeo,
                GeoTargeting::class.java)
        } returns null
        assertEquals(geoTargeting, inAppGeoRepository.geoGeo())
    }

    @Test
    fun `get geo invalid json`() {
        every { MindboxPreferences.inAppGeo } returns "123"
        val geoTargeting = GeoTargeting(cityId = "", regionId = "", countryId = "")
        every {
            gson.fromJson(MindboxPreferences.inAppGeo,
                GeoTargeting::class.java)
        } returns GeoTargeting("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.geoGeo())
    }
}
