package cloud.mindbox.mobile_sdk.inapp.presentation.view

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.models.Configuration
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AC35: a config param whose value is JSON must reach the page as a structure — the stories
 * page checks `Array.isArray(stories)` and silently draws nothing for a string. Plain string
 * params keep their type (the regression pair).
 */
@RunWith(RobolectricTestRunner::class)
class DataCollectorParamsTest {

    private val permissionManager: PermissionManager = mockk {
        every { getCameraPermissionStatus() } returns PermissionStatus.DENIED
        every { getLocationPermissionStatus() } returns PermissionStatus.DENIED
        every { getMicrophonePermissionStatus() } returns PermissionStatus.DENIED
        every { getNotificationPermissionStatus() } returns PermissionStatus.DENIED
        every { getPhotoLibraryPermissionStatus() } returns PermissionStatus.DENIED
    }

    private fun collect(params: Map<String, String>): JsonObject {
        val configuration: Configuration = mockk(relaxed = true) {
            every { endpointId } returns "endpoint-id"
            every { versionName } returns "1.0"
        }
        val payload = DataCollector(
            appContext = ApplicationProvider.getApplicationContext(),
            sessionStorageManager = mockk<SessionStorageManager>(relaxed = true),
            permissionManager = permissionManager,
            configuration = configuration,
            params = params,
            inAppInsets = InAppInsets(),
            gson = Gson(),
            inAppId = "in-app-id",
        ).get()
        return JsonParser.parseString(payload).asJsonObject
    }

    @Test
    fun `stories value that is a json array is sent as an array`() {
        val payload = collect(mapOf("stories" to """[{"inAppId":"story-1"},{"inAppId":"story-2"}]"""))

        assertTrue(payload.get("stories").isJsonArray)
        assertEquals(2, payload.getAsJsonArray("stories").size())
        assertEquals(
            "story-1",
            payload.getAsJsonArray("stories").get(0).asJsonObject.get("inAppId").asString
        )
    }

    @Test
    fun `object param value is sent as an object`() {
        val payload = collect(mapOf("extra" to """{"key":"value"}"""))

        assertTrue(payload.get("extra").isJsonObject)
    }

    @Test
    fun `plain string params are still sent as strings`() {
        val payload = collect(mapOf("formId" to "73379", "flag" to "true", "name" to "plain"))

        // Values that merely parse as numbers or booleans must stay strings.
        assertTrue(payload.get("formId").asJsonPrimitive.isString)
        assertTrue(payload.get("flag").asJsonPrimitive.isString)
        assertTrue(payload.get("name").asJsonPrimitive.isString)
        assertEquals("73379", payload.get("formId").asString)
    }

    @Test
    fun `malformed json param value stays a string`() {
        val payload = collect(mapOf("broken" to """{"unclosed": """))

        assertTrue(payload.get("broken").asJsonPrimitive.isString)
    }
}
