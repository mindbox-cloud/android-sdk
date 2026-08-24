package cloud.mindbox.mobile_sdk.inapp.presentation.view

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun collect(
        params: Map<String, String>,
        extraParams: Map<String, JsonElement> = emptyMap(),
        operation: InAppEventType.OrdinalEvent? = null,
        session: SessionStorageManager = mockk(relaxed = true),
    ): JsonObject {
        val configuration: Configuration = mockk(relaxed = true) {
            every { endpointId } returns "endpoint-id"
            every { versionName } returns "1.0"
        }
        val payload = DataCollector(
            appContext = ApplicationProvider.getApplicationContext(),
            sessionStorageManager = session,
            permissionManager = permissionManager,
            configuration = configuration,
            params = DataCollector.mergedParams(config = params, fromCaller = extraParams),
            inAppInsets = InAppInsets(),
            gson = Gson(),
            inAppId = "in-app-id",
            operation = operation,
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

    @Test
    fun `the in-app id is sent under the contract spelling only`() {
        val payload = collect(emptyMap())

        // Cut hard, in sync with iOS: the page that reads `inappId` ships before any SDK release.
        assertEquals("in-app-id", payload.get("inappId").asString)
        assertNull(payload.get("inAppId"))
    }

    @Test
    fun `extra params overwrite the sdk keys and the config params alike`() {
        val payload = collect(
            params = mapOf("formId" to "73379"),
            extraParams = mapOf(
                "formId" to JsonPrimitive(42),
                "inappId" to JsonPrimitive("caller-wins"),
            ),
        )

        assertEquals(42, payload.get("formId").asInt)
        assertEquals("caller-wins", payload.get("inappId").asString)
    }

    @Test
    fun `the keys the sdk measures cannot be dictated by params`() {
        // In sync with iOS: what the page is told about the operation and the visit is measured
        // here, so a config that names those keys must not be able to rewrite them.
        val session = SessionStorageManager(mockk(relaxed = true)).apply {
            lastTrackVisitData = TrackVisitData(
                ianaTimeZone = "Europe/Moscow",
                endpointId = "endpoint-id",
                source = "link",
                requestUrl = "https://mindbox.cloud/path",
                sdkVersionNumeric = Constants.SDK_VERSION_NUMERIC,
            )
        }
        val payload = collect(
            params = mapOf(
                "operationName" to "FakeOperation",
                "trackVisitSource" to "fake-source",
                "trackVisitRequestUrl" to "https://evil.example/path",
            ),
            extraParams = mapOf("operationBody" to JsonPrimitive("fake-body")),
            operation = InAppEventType.OrdinalEvent(
                eventType = EventType.AsyncOperation("RealOperation"),
                body = """{"real":true}""",
            ),
            session = session,
        )

        assertEquals("RealOperation", payload.get("operationName").asString)
        assertEquals("""{"real":true}""", payload.get("operationBody").asString)
        assertEquals("link", payload.get("trackVisitSource").asString)
        assertEquals("https://mindbox.cloud/path", payload.get("trackVisitRequestUrl").asString)
    }

    @Test
    fun `a show with no operation behind it tells the page about none`() {
        // The block and the requested show pass `null`, and the session's last trigger — which
        // belongs to somebody else's in-app — stays out of the payload.
        val session = SessionStorageManager(mockk(relaxed = true)).apply {
            inAppTriggerEvent = InAppEventType.OrdinalEvent(
                eventType = EventType.AsyncOperation("SomebodyElsesOperation"),
                body = """{"not":"ours"}""",
            )
        }

        val payload = collect(params = emptyMap(), operation = null, session = session)

        assertNull(payload.get("operationName"))
        assertNull(payload.get("operationBody"))
    }

    @Test
    fun `extra param values keep their json types`() {
        val record = JsonParser.parseString("""{"title":"Сториз 1","rank":3}""")
        val payload = collect(
            params = emptyMap(),
            extraParams = mapOf(
                "record" to record,
                "count" to JsonPrimitive(5),
                "enabled" to JsonPrimitive(true),
            ),
        )

        assertEquals(record, payload.get("record"))
        assertEquals(5, payload.get("count").asInt)
        assertTrue(payload.get("enabled").asBoolean)
    }
}
