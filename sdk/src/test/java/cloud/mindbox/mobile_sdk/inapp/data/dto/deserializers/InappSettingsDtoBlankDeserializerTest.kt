package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class InappSettingsDtoBlankDeserializerTest {
    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = GsonBuilder()
            .registerTypeAdapter(
                SettingsDtoBlank.InappSettingsDtoBlank::class.java,
                InappSettingsDtoBlankDeserializer()
            )
            .create()
    }

    @Test
    fun `deserialize valid values`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", 2)
            addProperty("maxInappsPerDay", 1)
            addProperty("minIntervalBetweenShows", "0.00:00:10")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertEquals(2, result.maxInappsPerSession)
        assertEquals(1, result.maxInappsPerDay)
        assertEquals("0.00:00:10", result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize string number values`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", "2")
            addProperty("maxInappsPerDay", "1")
            addProperty("minIntervalBetweenShows", "0.00:00:10")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertEquals(2, result.maxInappsPerSession)
        assertEquals(1, result.maxInappsPerDay)
        assertEquals("0.00:00:10", result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize number in string property`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", "2")
            addProperty("maxInappsPerDay", "1")
            addProperty("minIntervalBetweenShows", 2)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertEquals(2, result.maxInappsPerSession)
        assertEquals(1, result.maxInappsPerDay)
        assertNull(result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize invalid string values`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", "invalid")
            addProperty("maxInappsPerDay", "not a number")
            addProperty("minIntervalBetweenShows", "0.00:00:10")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertNull(result.maxInappsPerSession)
        assertNull(result.maxInappsPerDay)
        assertEquals("0.00:00:10", result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize object values`() {
        val json = JsonObject().apply {
            add("maxInappsPerSession", JsonObject().apply {
                addProperty("value", 2)
            })
            add("maxInappsPerDay", JsonObject().apply {
                addProperty("value", 1)
            })
            add("minIntervalBetweenShowsy", JsonObject().apply {
                addProperty("value", 1)
            })
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertNull(result.maxInappsPerSession)
        assertNull(result.maxInappsPerDay)
        assertNull(result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize values exceeding Int MAX_VALUE`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", Int.MAX_VALUE.toLong() + 1)
            addProperty("maxInappsPerDay", "2147483648")
            addProperty("minIntervalBetweenShows", "0.00:00:10")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertNull(result.maxInappsPerSession)
        assertNull(result.maxInappsPerDay)
        assertEquals("0.00:00:10", result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize values exceeding Int MIN_VALUE`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", Int.MIN_VALUE.toLong() - 1)
            addProperty("maxInappsPerDay", "-2147483649")
            addProperty("minIntervalBetweenShows", "0.00:00:10")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertNull(result.maxInappsPerSession)
        assertNull(result.maxInappsPerDay)
        assertEquals("0.00:00:10", result.minIntervalBetweenShows)
    }

    @Test
    fun `deserialize values when empty string`() {
        val json = JsonObject().apply {
            addProperty("maxInappsPerSession", "")
            addProperty("maxInappsPerDay", "")
            addProperty("minIntervalBetweenShows", "")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.InappSettingsDtoBlank::class.java)

        assertNull(result.maxInappsPerSession)
        assertNull(result.maxInappsPerDay)
        assertEquals("", result.minIntervalBetweenShows)
    }
}
