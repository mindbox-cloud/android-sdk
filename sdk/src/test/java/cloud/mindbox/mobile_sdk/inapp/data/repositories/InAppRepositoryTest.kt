package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

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
        every { sessionStorageManager.operationalInApps } returns hashMapOf(
            testOperation to mutableListOf(
                existingInApp
            )
        )
        inAppRepository.saveOperationalInApp(testOperation, newInApp)
        assertEquals(expectedList, sessionStorageManager.operationalInApps[testOperation])
    }

    @Test
    fun `save operation inApps success empty list`() {
        val testOperation = "testOperation"
        val newInApp = InAppStub.getInApp().copy(id = "newInAppId")
        val expectedList = mutableListOf(newInApp)
        every { sessionStorageManager.operationalInApps } returns hashMapOf()
        inAppRepository.saveOperationalInApp(testOperation, newInApp)
        assertEquals(expectedList, sessionStorageManager.operationalInApps[testOperation])
    }

    @Test
    fun `get operation inApps returns null`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf<InApp>()
        every { sessionStorageManager.operationalInApps[testOperation.lowercase()] } returns null
        val actualResult = inAppRepository.getOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get operation inApps no inApps`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf<InApp>()
        every { sessionStorageManager.operationalInApps[testOperation.lowercase()] } returns expectedResult
        val actualResult = inAppRepository.getOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `get operation inApps success`() {
        val testOperation = "testOperation"
        val expectedResult = mutableListOf(
            InAppStub.getInApp()
        )
        every { sessionStorageManager.operationalInApps[testOperation.lowercase()] } returns expectedResult
        val actualResult = inAppRepository.getOperationalInAppsByOperation(testOperation)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `save shown inApp success`() {
        val expectedJson = """
            |["123","456"]
        """.trimMargin()
        every { MindboxPreferences.shownInAppIds } returns "[123]"
        every { inAppSerializationManager.deserializeToShownInApps(any()) } returns hashSetOf("123")
        every { inAppSerializationManager.serializeToShownInAppsString(any()) } returns expectedJson
        inAppRepository.saveShownInApp("456")
        verify(exactly = 1) {
            MindboxPreferences.shownInAppIds = expectedJson
        }
    }

    @Test
    fun `save shown inApp error`() {
        val expectedJson = """
            |["123","456"]
        """.trimMargin()
        every { MindboxPreferences.shownInAppIds } returns "[123]"
        every { inAppSerializationManager.deserializeToShownInApps(any()) } returns hashSetOf("123")
        every { inAppSerializationManager.serializeToShownInAppsString(any()) } returns ""
        inAppRepository.saveShownInApp("456")
        verify(exactly = 0) {
            MindboxPreferences.shownInAppIds = expectedJson
        }
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