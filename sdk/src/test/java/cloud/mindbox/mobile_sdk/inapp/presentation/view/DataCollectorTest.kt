package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.TrackVisitData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale
import android.content.res.Configuration as UiConfiguration

class DataCollectorTest {
    private lateinit var appContext: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var resources: Resources
    private lateinit var uiConfiguration: UiConfiguration
    private val gson: Gson = Gson()
    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun onTestStart() {
        previousLocale = Locale.getDefault()
        appContext = mockk()
        resources = mockk()
        uiConfiguration = UiConfiguration()
        permissionManager = mockk()
        sessionStorageManager = SessionStorageManager(timeProvider = mockk())
        val displayMetrics = DisplayMetrics().apply { density = 1f }
        every { appContext.resources } returns resources
        every { resources.configuration } returns uiConfiguration
        every { resources.displayMetrics } returns displayMetrics
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.localStateVersion } returns 1
    }

    @After
    fun onTestFinish() {
        Locale.setDefault(previousLocale)
        unmockkAll()
    }

    @Test
    fun `get builds payload with main data and permissions`() {
        Locale.setDefault(Locale.forLanguageTag("en-US"))
        uiConfiguration.uiMode = UiConfiguration.UI_MODE_NIGHT_NO
        every { MindboxPreferences.deviceUuid } returns "device-uuid"
        every { MindboxPreferences.localStateVersion } returns 12
        every { MindboxPreferences.firstInitializationTime } returns "2025-01-10T07:40:00Z"
        every { MindboxPreferences.userVisitCount } returns 7
        every { permissionManager.getCameraPermissionStatus() } returns PermissionStatus.GRANTED
        every { permissionManager.getLocationPermissionStatus() } returns PermissionStatus.DENIED
        every { permissionManager.getMicrophonePermissionStatus() } returns PermissionStatus.NOT_DETERMINED
        every { permissionManager.getNotificationPermissionStatus() } returns PermissionStatus.RESTRICTED
        every { permissionManager.getPhotoLibraryPermissionStatus() } returns PermissionStatus.LIMITED
        sessionStorageManager.lastTrackVisitData = TrackVisitData(
            ianaTimeZone = "Europe/Moscow",
            endpointId = "endpoint-id",
            source = "link",
            requestUrl = "https://mindbox.cloud/path",
            sdkVersionNumeric = Constants.SDK_VERSION_NUMERIC,
        )
        sessionStorageManager.inAppTriggerEvent = InAppEventType.OrdinalEvent(
            eventType = EventType.AsyncOperation("OpenScreen"),
            body = "{\"screen\":\"home\"}",
        )
        val dataCollector = DataCollector(
            appContext = appContext,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            configuration = createConfiguration(endpointId = "endpoint-id", versionName = "1.2.3"),
            params = mapOf("customKey" to "customValue"),
            inAppInsets = InAppInsets(left = 1, top = 2, right = 3, bottom = 4),
            gson = gson,
            inAppId = "inapp-id",
        )
        val actualPayload: String = dataCollector.get()
        val actualJson: JsonObject = JsonParser.parseString(actualPayload).asJsonObject
        assertEquals("device-uuid", actualJson.get("deviceUUID").asString)
        assertEquals("endpoint-id", actualJson.get("endpointId").asString)
        assertEquals("2025-01-10T07:40:00Z", actualJson.get("firstInitializationDateTime").asString)
        assertEquals("en_US", actualJson.get("locale").asString)
        assertEquals("OpenScreen", actualJson.get("operationName").asString)
        assertEquals("{\"screen\":\"home\"}", actualJson.get("operationBody").asString)
        assertEquals("android", actualJson.get("platform").asString)
        assertEquals("light", actualJson.get("theme").asString)
        assertEquals(12, actualJson.get("localStateVersion").asInt)
        assertEquals("link", actualJson.get("trackVisitSource").asString)
        assertEquals("https://mindbox.cloud/path", actualJson.get("trackVisitRequestUrl").asString)
        assertEquals("7", actualJson.get("userVisitCount").asString)
        assertEquals("1.2.3", actualJson.get("version").asString)
        assertEquals("customValue", actualJson.get("customKey").asString)
        assertEquals(1, actualJson.getAsJsonObject("insets").get("left").asInt)
        assertEquals(2, actualJson.getAsJsonObject("insets").get("top").asInt)
        assertEquals(3, actualJson.getAsJsonObject("insets").get("right").asInt)
        assertEquals(4, actualJson.getAsJsonObject("insets").get("bottom").asInt)
        val permissionsJson: JsonObject = actualJson.getAsJsonObject("permissions")
        assertEquals("granted", getPermissionStatus(actualJson, "camera"))
        assertFalse(permissionsJson.has("location"))
        assertFalse(permissionsJson.has("microphone"))
        assertFalse(permissionsJson.has("notifications"))
        assertFalse(permissionsJson.has("photoLibrary"))
        assertTrue(actualJson.has("sdkVersion"))
        assertEquals(Constants.SDK_VERSION_NUMERIC.toString(), actualJson.get("sdkVersionNumeric").asString)
    }

    @Test
    fun `get ignores blank values and applies params override`() {
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        uiConfiguration.uiMode = UiConfiguration.UI_MODE_NIGHT_YES
        every { MindboxPreferences.deviceUuid } returns ""
        every { MindboxPreferences.localStateVersion } returns 3
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.userVisitCount } returns 3
        every { permissionManager.getCameraPermissionStatus() } returns PermissionStatus.GRANTED
        every { permissionManager.getLocationPermissionStatus() } returns PermissionStatus.GRANTED
        every { permissionManager.getMicrophonePermissionStatus() } returns PermissionStatus.GRANTED
        every { permissionManager.getNotificationPermissionStatus() } returns PermissionStatus.GRANTED
        every { permissionManager.getPhotoLibraryPermissionStatus() } returns PermissionStatus.GRANTED
        sessionStorageManager.inAppTriggerEvent = InAppEventType.AppStartup
        sessionStorageManager.lastTrackVisitData = TrackVisitData(
            ianaTimeZone = "Europe/Moscow",
            endpointId = "endpoint-id",
            source = null,
            requestUrl = " ",
            sdkVersionNumeric = Constants.SDK_VERSION_NUMERIC,
        )
        val dataCollector = DataCollector(
            appContext = appContext,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            configuration = createConfiguration(endpointId = "", versionName = "2.0.0"),
            params = mapOf("endpointId" to "overridden-endpoint"),
            inAppInsets = InAppInsets(),
            gson = gson,
            inAppId = "inapp-id",
        )
        val actualPayload: String = dataCollector.get()
        val actualJson: JsonObject = JsonParser.parseString(actualPayload).asJsonObject
        assertFalse(actualJson.has("deviceUUID"))
        assertFalse(actualJson.has("firstInitializationDateTime"))
        assertFalse(actualJson.has("operationName"))
        assertFalse(actualJson.has("operationBody"))
        assertFalse(actualJson.has("trackVisitSource"))
        assertFalse(actualJson.has("trackVisitRequestUrl"))
        assertEquals(3, actualJson.get("localStateVersion").asInt)
        assertEquals("overridden-endpoint", actualJson.get("endpointId").asString)
        assertEquals("dark", actualJson.get("theme").asString)
        assertEquals("ru_RU", actualJson.get("locale").asString)
        val permissionsJson: JsonObject = actualJson.getAsJsonObject("permissions")
        assertEquals(5, permissionsJson.keySet().size)
        assertEquals("granted", getPermissionStatus(actualJson, "camera"))
        assertEquals("granted", getPermissionStatus(actualJson, "location"))
        assertEquals("granted", getPermissionStatus(actualJson, "microphone"))
        assertEquals("granted", getPermissionStatus(actualJson, "notifications"))
        assertEquals("granted", getPermissionStatus(actualJson, "photoLibrary"))
    }

    @Test
    fun `get converts insets to CSS pixels when density is not 1f`() {
        val density = 2.5f
        val displayMetrics = DisplayMetrics().apply { this.density = density }
        every { resources.displayMetrics } returns displayMetrics
        every { MindboxPreferences.deviceUuid } returns "device-uuid"
        every { MindboxPreferences.localStateVersion } returns 5
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.userVisitCount } returns 0
        every { permissionManager.getCameraPermissionStatus() } returns PermissionStatus.DENIED
        every { permissionManager.getLocationPermissionStatus() } returns PermissionStatus.DENIED
        every { permissionManager.getMicrophonePermissionStatus() } returns PermissionStatus.DENIED
        every { permissionManager.getNotificationPermissionStatus() } returns PermissionStatus.DENIED
        every { permissionManager.getPhotoLibraryPermissionStatus() } returns PermissionStatus.DENIED
        sessionStorageManager.lastTrackVisitData = null
        sessionStorageManager.inAppTriggerEvent = InAppEventType.AppStartup
        val inAppInsets = InAppInsets(left = 5, top = 10, right = 15, bottom = 20)
        val dataCollector = DataCollector(
            appContext = appContext,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            configuration = createConfiguration(endpointId = "endpoint-id", versionName = "1.0.0"),
            params = emptyMap(),
            inAppInsets = inAppInsets,
            gson = gson,
            inAppId = "inapp-id",
        )
        val actualPayload = dataCollector.get()
        val actualJson = JsonParser.parseString(actualPayload).asJsonObject
        val insetsJson = actualJson.getAsJsonObject("insets")
        assertNotNull(insetsJson)
        assertEquals(2, insetsJson.get("left").asInt)
        assertEquals(4, insetsJson.get("top").asInt)
        assertEquals(6, insetsJson.get("right").asInt)
        assertEquals(8, insetsJson.get("bottom").asInt)
        assertEquals(5, actualJson.get("localStateVersion").asInt)
    }

    private fun getPermissionStatus(payload: JsonObject, permissionKey: String): String {
        return payload
            .getAsJsonObject("permissions")
            .getAsJsonObject(permissionKey)
            .get("status")
            .asString
    }

    private fun createConfiguration(endpointId: String, versionName: String): Configuration {
        return Configuration(
            previousInstallationId = "prev-installation",
            previousDeviceUUID = "prev-device",
            endpointId = endpointId,
            domain = "api.test.mindbox.cloud",
            packageName = "cloud.mindbox.test",
            versionName = versionName,
            versionCode = "100",
            subscribeCustomerIfCreated = false,
            shouldCreateCustomer = true,
        )
    }
}
