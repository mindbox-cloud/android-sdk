package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InAppRepositoryTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var inAppSerializationManager: InAppSerializationManager

    @OverrideMockKs
    private lateinit var inAppRepository: InAppRepositoryImpl

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        mockkObject(MindboxEventManager)
    }

    @Test
    fun `save operational inApps success`() {
        val testOperation = "testOperation"
        val newInApp = InAppStub.getInApp().copy(id = "newInAppId")
        val existingInApp = InAppStub.getInApp().copy(id = "existingId")
        val expectedList = mutableListOf(existingInApp, newInApp)
        every { sessionStorageManager.unShownOperationalInApps } returns hashMapOf(
            testOperation to mutableListOf(
                existingInApp
            )
        )
        inAppRepository.saveUnShownOperationalInApp(testOperation, newInApp)
        assertEquals(expectedList, sessionStorageManager.unShownOperationalInApps[testOperation])
    }

    @Test
    fun `save operation inApps success empty list`() {
        val testOperation = "testOperation"
        val newInApp = InAppStub.getInApp().copy(id = "newInAppId")
        val expectedList = mutableListOf(newInApp)
        every { sessionStorageManager.unShownOperationalInApps } returns hashMapOf()
        inAppRepository.saveUnShownOperationalInApp(testOperation, newInApp)
        assertEquals(expectedList, sessionStorageManager.unShownOperationalInApps[testOperation])
    }

    @Test
    fun `get operation inApps returns null`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf<InApp>()
        every { sessionStorageManager.unShownOperationalInApps[testOperation.lowercase()] } returns null
        val actualResult = inAppRepository.getUnShownOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get operation inApps no inApps`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf<InApp>()
        every { sessionStorageManager.unShownOperationalInApps[testOperation.lowercase()] } returns expectedResult
        val actualResult = inAppRepository.getUnShownOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get operation inApps success`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf(
            InAppStub.getInApp()
        )
        every { sessionStorageManager.unShownOperationalInApps[testOperation.lowercase()] } returns expectedResult
        val actualResult = inAppRepository.getUnShownOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `save shown inApp`() {
        val id = "testId"
        val timeStamp = System.currentTimeMillis()
        val serializedData = "serializedData"

        every { inAppSerializationManager.serializeToShownInAppsString(any<Map<String, Long>>()) } returns serializedData
        every { MindboxPreferences.shownInApps = any() } just runs
        every {
            inAppSerializationManager.deserializeToShownInAppsMap(any())
        } returns hashMapOf()

        // Call the method under test
        inAppRepository.saveShownInApp(id, timeStamp)

        // Verify that the correct methods were called with the expected arguments
        verify { inAppSerializationManager.serializeToShownInAppsString(match<Map<String, Long>> { it[id] == timeStamp }) }
        verify { MindboxPreferences.shownInApps = serializedData }
    }

    @Test
    fun `send in app shown success`() {
        val testInAppId = "testInAppId"
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppShown(testInAppId)
        verify(exactly = 1) {
            MindboxEventManager.inAppShown(context, serializedString)
        }
    }

    @Test
    fun `send in app shown empty string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppShown(testInAppId)
        verify(exactly = 0) {
            MindboxEventManager.inAppShown(context, serializedString)
        }
    }

    @Test
    fun `send in app clicked success`() {
        val testInAppId = "testInAppId"
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId)
        verify(exactly = 1) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }

    @Test
    fun `send in app clicked empty string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId)
        verify(exactly = 0) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }

    @Test
    fun `send user targeted success`() {
        val testInAppId = "testInAppId"
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId)
        verify(exactly = 1) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }

    @Test
    fun `send user targeted string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppHandledString(any()) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId)
        verify(exactly = 0) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }
}