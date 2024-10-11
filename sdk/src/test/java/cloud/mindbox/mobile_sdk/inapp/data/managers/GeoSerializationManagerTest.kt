package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.app.Application
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.models.GeoTargetingStub
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class GeoSerializationManagerTest {

    private val gson: Gson by mindboxInject { gson }

    private val validCityId = "123"

    private val validRegionId = "456"

    private val validCountryId = "789"

    private val geoSerializationManager: GeoSerializationManager by lazy {
        GeoSerializationManagerImpl(gson)
    }

    val context = mockk<Application>(relaxed = true) {
        every { applicationContext } returns this
    }

    @Before
    fun onTestStart() {
        MindboxDI.init(context)
    }

    @Test
    fun `deserialize to geo targeting success`() {
        val testJson =
            "{\"cityId\":\"$validCityId\",\"regionId\":\"$validRegionId\",\"countryId\":\"$validCountryId\"}"
        val expectedResult = GeoTargetingStub.getGeoTargeting().copy(cityId = validCityId,
            regionId = validRegionId,
            countryId = validCountryId)

        val actualResult = geoSerializationManager.deserializeToGeoTargeting(testJson)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to geo targeting empty string`() {
        val testJson = ""
        val expectedResult = GeoTargetingStub.getGeoTargeting().copy(cityId = "",
            regionId = "",
            countryId = ""
        )
        val actualResult = geoSerializationManager.deserializeToGeoTargeting(testJson)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to geo targeting invalid json`() {
        val testJson = "invalidJson"
        val expectedResult = GeoTargetingStub.getGeoTargeting().copy(cityId = "",
            regionId = "",
            countryId = "")
        val actualResult = geoSerializationManager.deserializeToGeoTargeting(testJson)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to geo targeting error`() {
        val testJson = "invalidJson"
        val gson: Gson = mockk()
        val expectedResult = GeoTargetingStub.getGeoTargeting().copy(cityId = "",
            regionId = "",
            countryId = "")
        every {
            gson.fromJson(testJson, GeoTargeting::class.java)
        } throws Error("errorMessage")
        val actualResult = geoSerializationManager.deserializeToGeoTargeting(testJson)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to geo targeting string success`() {
        val expectedResult =
            "{\"cityId\":\"$validCityId\",\"regionId\":\"$validRegionId\",\"countryId\":\"$validCountryId\"}"
        val actualResult =
            geoSerializationManager.serializeToGeoString(GeoTargetingStub.getGeoTargeting().copy(
                cityId = validCityId,
                regionId = validRegionId,
                countryId = validCountryId))
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to geo targeting string throws error`() {
        val gson: Gson = mockk()
        val testGeoTargeting = GeoTargetingStub.getGeoTargeting().copy(cityId = "",
            regionId = "",
            countryId = "")
        every {
            gson.toJson(any())
        } throws Error("errorMessage")
        val expectedResult = ""
        val geoSerializationManager = GeoSerializationManagerImpl(gson)
        val actualResult = geoSerializationManager.serializeToGeoString(testGeoTargeting)
        assertEquals(expectedResult, actualResult)
    }
}
