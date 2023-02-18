package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
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
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppGeoRepositoryTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppGeoRepository: InAppGeoRepositoryImpl

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var mobileConfigSerializationManager: MobileConfigSerializationManager

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
            mobileConfigSerializationManager.serializeToGeoString(geoTargeting)
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
            mobileConfigSerializationManager.serializeToGeoString(geoTargeting)
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
            mobileConfigSerializationManager.serializeToGeoString(geoTargeting)
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
            mobileConfigSerializationManager.deserializeToGeoTargeting("{\"cityId\":\"123\", \"regionId\":\"456\", \"countryId\":\"789\"}")
        } returns GeoTargeting(cityId = "123", regionId = "456", countryId = "789")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }

    @Test
    fun `get geo empty string`() {
        val geoTargeting = GeoTargeting(cityId = "", regionId = "", countryId = "")
        every { MindboxPreferences.inAppGeo } returns ""
        every {
            mobileConfigSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        } returns GeoTargeting("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }

    @Test
    fun `get geo invalid json`() {
        every { MindboxPreferences.inAppGeo } returns "123"
        val geoTargeting = GeoTargeting(cityId = "", regionId = "", countryId = "")
        every {
            mobileConfigSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        } returns GeoTargeting("", "", "")
        assertEquals(geoTargeting, inAppGeoRepository.getGeo())
    }
}
