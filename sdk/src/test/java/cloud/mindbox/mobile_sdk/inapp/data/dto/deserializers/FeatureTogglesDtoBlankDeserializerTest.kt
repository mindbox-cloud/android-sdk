package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_SHOW_ERROR_FEATURE
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class FeatureTogglesDtoBlankDeserializerTest {
    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = GsonBuilder()
            .create()
    }

    @Test
    fun `deserialize valid true value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", true)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertEquals(true, result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize valid false value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", false)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertEquals(false, result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize multiple keys`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", true)
            addProperty("anotherToggle", false)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertEquals(true, result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
        assertEquals(false, result.toggles["anotherToggle"])
    }

    @Test
    fun `deserialize string true value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", "true")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize string false value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", "false")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize number 1 value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", 1)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize invalid string value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", "invalid")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize object value`() {
        val json = JsonObject().apply {
            add("shouldSendInAppShowError", JsonObject().apply {
                addProperty("value", true)
            })
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize array value`() {
        val json = JsonObject().apply {
            add("shouldSendInAppShowError", JsonArray().apply {
                add(true)
            })
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize empty string value`() {
        val json = JsonObject().apply {
            addProperty("shouldSendInAppShowError", "")
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }

    @Test
    fun `deserialize missing key`() {
        val json = JsonObject()

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertTrue(result.toggles.isEmpty())
    }

    @Test
    fun `deserialize null value`() {
        val json = JsonObject().apply {
            add("shouldSendInAppShowError", JsonNull.INSTANCE)
        }

        val result = gson.fromJson(json, SettingsDtoBlank.FeatureTogglesDtoBlank::class.java)

        assertNull(result.toggles[SEND_INAPP_SHOW_ERROR_FEATURE])
    }
}
