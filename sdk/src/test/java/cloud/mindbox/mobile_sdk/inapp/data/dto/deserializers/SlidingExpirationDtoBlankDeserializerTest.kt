package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SlidingExpirationDtoBlankDeserializerTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = GsonBuilder()
            .registerTypeAdapter(
                SettingsDtoBlank.SlidingExpirationDtoBlank::class.java,
                SlidingExpirationDtoBlankDeserializer()
            )
            .create()
    }

    @Test
    fun `test deserialize with valid time span strings`() {
        val jsonObject = JsonObject().apply {
            addProperty("config", "0:0:10.0")
            addProperty("pushTokenKeepalive", "17.00:00:00")
        }

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertEquals(TimeSpan.fromStringOrNull("0:0:10.0"), result.config)
        assertEquals(TimeSpan.fromStringOrNull("17.00:00:00"), result.pushTokenKeepalive)
    }

    @Test
    fun `test deserialize with invalid time span strings`() {
        val jsonObject = JsonObject().apply {
            addProperty("config", "invalid")
            addProperty("pushTokenKeepalive", "not_a_time")
        }

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertNull(result.config)
        assertNull(result.pushTokenKeepalive)
    }

    @Test
    fun `test deserialize with non-string values`() {
        val jsonObject = JsonObject().apply {
            addProperty("config", 123)
            addProperty("pushTokenKeepalive", true)
        }

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertNull(result.config)
        assertNull(result.pushTokenKeepalive)
    }

    @Test
    fun `test deserialize with missing fields`() {
        val jsonObject = JsonObject()

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertNull(result.config)
        assertNull(result.pushTokenKeepalive)
    }

    @Test
    fun `test deserialize with one valid and one invalid field`() {
        val jsonObject = JsonObject().apply {
            addProperty("config", "0:1:00.0")
            addProperty("pushTokenKeepalive", "invalid")
        }

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertEquals(TimeSpan.fromStringOrNull("0:1:00.0"), result.config)
        assertNull(result.pushTokenKeepalive)
    }

    @Test
    fun `test deserialize with syntactically correct but semantically invalid time span`() {
        val jsonObject = JsonObject().apply {
            addProperty("config", "0:0:99.0")
            addProperty("pushTokenKeepalive", "0:0:10.0")
        }

        val result = gson.fromJson(
            jsonObject,
            SettingsDtoBlank.SlidingExpirationDtoBlank::class.java
        )

        assertNull(result.config)
        assertEquals(TimeSpan.fromStringOrNull("0:0:10.0"), result.pushTokenKeepalive)
    }
}
