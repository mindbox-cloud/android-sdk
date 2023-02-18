package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.inject

internal class InAppSerializationManagerTest : KoinTest {

    private val gson: Gson by inject()
    private lateinit var inAppSerializationManager: InAppSerializationManager

    private val validInAppId = "validInAppId"

    @Before
    fun onTestStart() {
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
    }

    @Test
    fun `serialize to inApp handled string success`() {
        val expectedResult = "{\"inappid\"}"
        val actualResult = inAppSerializationManager.serializeToInAppHandledString(validInAppId)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to inApp handled string error`() {
        val gson: Gson = mockk()
        every {
            gson.toJson(any())
        } throws Error("errorMessage")
        val expectedResult = ""
        inAppSerializationManager = InAppSerializationManagerImpl(gson)
        val actualResult = inAppSerializationManager.serializeToInAppHandledString(validInAppId)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `serialize to shown inApps string success`() {
      /*  inAppSerializationManager.serializeToShownInAppsString(HashSet().apply {
            add("")
        }, "")*/
    }
}