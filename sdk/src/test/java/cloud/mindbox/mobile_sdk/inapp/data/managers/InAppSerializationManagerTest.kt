package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

internal class InAppSerializationManagerTest : KoinTest {

    private val gson: Gson by inject()
    private lateinit var inAppSerializationManager: InAppSerializationManager
    private val inAppId = "validInAppId"
    private val otherInAppId = "otherInAppId"

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
    }

    @Before
    fun onTestStart() {
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
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
        val testHashset = hashSetOf(inAppId, otherInAppId)
        val gson: Gson = mockk()
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