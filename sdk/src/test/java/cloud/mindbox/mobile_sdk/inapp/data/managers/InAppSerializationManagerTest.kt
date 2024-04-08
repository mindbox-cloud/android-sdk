package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class InAppSerializationManagerTest {

    private val gson: Gson  = Gson()
    private lateinit var inAppSerializationManager: InAppSerializationManager
    private val inAppId = "validInAppId"
    private val otherInAppId = "otherInAppId"

    @Before
    fun onTestStart() {
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
    }

    @Test
    fun `deserializeToShownInAppsMap returns valid map`() {
        val json = "{\"app1\":100,\"app2\":200}"
        val expectedMap = mapOf("app1" to 100L, "app2" to 200L)

        val actualMap = inAppSerializationManager.deserializeToShownInAppsMap(json)

        assertEquals(expectedMap, actualMap)
    }

    @Test
    fun `deserializeToShownInAppsMap returns empty map when exception occurs`() {
        val json = "{\"app1\":100,\"app2\":200}"
        val gson: Gson = mockk()
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        // Mocking runCatching to throw an exception
        every {gson.fromJson<String>(json, object : TypeToken<HashMap<String, Long>>() {}.type) } throws RuntimeException("Some exception")


        val actualMap = inAppSerializationManager.deserializeToShownInAppsMap(json)

        assertEquals(emptyMap<String, Long>(), actualMap)
    }

    @Test
    fun `serializeToShownInAppsString returns valid JSON string`() {
        val shownInApps = mapOf("app1" to 100L, "app2" to 200L)
        val expectedJson = "{\"app1\":100,\"app2\":200}"

        val actualJson = inAppSerializationManager.serializeToShownInAppsString(shownInApps)

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `serializeToShownInAppsString returns empty string when exception occurs`() {
        val gson: Gson = mockk()
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        val shownInApps = mapOf("app1" to 100L, "app2" to 200L)
        every { gson.toJson(shownInApps,object : TypeToken<HashMap<String, Long>>() {}.type) } throws RuntimeException("Some exception")

        val actualJson = inAppSerializationManager.serializeToShownInAppsString(shownInApps)

        assertEquals("", actualJson)
    }

    @Test
    fun `serialize to inApp handled string success`() {
        val expectedResult = "{\"inappid\":\"${inAppId}\"}"
        val actualResult = inAppSerializationManager.serializeToInAppHandledString(inAppId)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to inApp handled string error`() {
        val gson: Gson = mockk()
        every {
            gson.toJson(any())
        } throws Error("errorMessage")
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        val expectedResult = ""
        val actualResult = inAppSerializationManager.serializeToInAppHandledString(inAppId)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to shown inApps string success`() {
        val testHashset = hashSetOf(inAppId, otherInAppId)
        val expectedResult = "[\"${otherInAppId}\",\"$inAppId\"]"
        val actualResult = inAppSerializationManager.serializeToShownInAppsString(testHashset)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to shown inApps string error`() {
        val gson: Gson = mockk()
        val testHashset = hashSetOf(inAppId, otherInAppId)
        every {
            gson.toJson(any())
        } throws Error("errorMessage")
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        val expectedResult = ""
        val actualResult = inAppSerializationManager.serializeToShownInAppsString(testHashset)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to shown inApps success`() {
        val testString = "[\"${inAppId}\", \"$otherInAppId\"]"
        val expectedResult = hashSetOf(inAppId, otherInAppId)
        val actualResult = inAppSerializationManager.deserializeToShownInApps(testString)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to shown inApps error`() {
        val testString = "someString"
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppSerializationManager.deserializeToShownInApps(testString)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to shown inApps empty string`() {
        val testString = ""
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppSerializationManager.deserializeToShownInApps(testString)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to shown inApps returns null`() {
        val testString = "someString"
        val gson: Gson = mockk()
        every {
            gson.fromJson<HashSet<String>?>(
                testString,
                object : TypeToken<HashSet<String>>() {}.type
            )
        } returns null
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppSerializationManager.deserializeToShownInApps(testString)
        assertEquals(expectedResult, actualResult)
    }


}