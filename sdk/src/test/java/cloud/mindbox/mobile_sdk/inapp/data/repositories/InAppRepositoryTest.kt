package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
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
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTime
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider

class InAppRepositoryTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var sessionStorageManager: SessionStorageManager

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var inAppSerializationManager: InAppSerializationManager

    @MockK
    private lateinit var timeProvider: SystemTimeProvider

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
        every { sessionStorageManager.unShownOperationalInApps } returns ConcurrentHashMap(mapOf(
            testOperation to mutableListOf(
                existingInApp
            )
        ))
        inAppRepository.saveUnShownOperationalInApp(testOperation, newInApp)
        assertEquals(expectedList, sessionStorageManager.unShownOperationalInApps[testOperation])
    }

    @Test
    fun `save operation inApps success empty list`() {
        val testOperation = "testOperation"
        val newInApp = InAppStub.getInApp().copy(id = "newInAppId")
        val expectedList = mutableListOf(newInApp)
        every { sessionStorageManager.unShownOperationalInApps } returns ConcurrentHashMap()
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

        every { timeProvider.currentTimeMillis() } returns timeStamp
        every { inAppSerializationManager.serializeToShownInAppsString(any<Map<String, List<Long>>>()) } returns serializedData
        every { MindboxPreferences.shownInApps = any() } just runs
        every {
            inAppSerializationManager.deserializeToShownInAppsMap(any())
        } returns ConcurrentHashMap()

        // Call the method under test
        inAppRepository.saveShownInApp(id, timeStamp)

        // Verify that the correct methods were called with the expected arguments
        verify { inAppSerializationManager.serializeToShownInAppsString(match<Map<String, List<Long>>> { it[id] == listOf(timeStamp) }) }
        verify { MindboxPreferences.shownInApps = serializedData }
    }

    @Test
    fun `send in app shown success`() {
        val testInAppId = "testInAppId"
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppShownActionString(any(), any(), any()) } returns serializedString
        inAppRepository.sendInAppShown(testInAppId, "0:00:00:00.2250000", null)
        verify(exactly = 1) {
            MindboxEventManager.inAppShown(context, serializedString)
        }
    }

    @Test
    fun `send in app shown empty string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppShownActionString(any(), any(), any()) } returns serializedString
        inAppRepository.sendInAppShown(testInAppId, "0:00:00:00.2250000", null)
        verify(exactly = 0) {
            MindboxEventManager.inAppShown(context, serializedString)
        }
    }

    @Test
    fun `send in app show errors ships the serialized errors body`() {
        val serializedString = """{"errors":[{}]}"""
        every { inAppSerializationManager.serializeToInAppShowErrorsString(any()) } returns serializedString

        inAppRepository.sendInAppShowErrors(
            listOf(
                cloud.mindbox.mobile_sdk.models.operation.request.EmbeddedBlockShowFailure(
                    placeSystemName = "main-screen-top",
                    failureReason = cloud.mindbox.mobile_sdk.models.operation.request.FailureReason.WAIT_BUDGET_EXCEEDED,
                    errorDetails = null,
                    dateTimeUtc = "2024-02-10T00:00:00Z"
                )
            )
        )

        verify(exactly = 1) { MindboxEventManager.inAppShowFailure(context, serializedString) }
    }

    @Test
    fun `send in app show errors sends nothing for an empty list`() {
        inAppRepository.sendInAppShowErrors(emptyList())

        verify(exactly = 0) { MindboxEventManager.inAppShowFailure(any(), any()) }
    }

    @Test
    fun `send in app clicked success`() {
        val testInAppId = "testInAppId"
        val tags = mapOf("templateType" to "Popup")
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppClickActionString(testInAppId, tags) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId, tags)
        verify(exactly = 1) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }

    @Test
    fun `send in app clicked empty string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppClickActionString(any(), any()) } returns serializedString
        inAppRepository.sendInAppClicked(testInAppId, null)
        verify(exactly = 0) {
            MindboxEventManager.inAppClicked(context, serializedString)
        }
    }

    @Test
    fun `send user targeted success`() {
        val testInAppId = "testInAppId"
        val tags = mapOf("templateType" to "Popup")
        val serializedString = "serializedString"
        every { inAppSerializationManager.serializeToInAppTargetingString(testInAppId, tags) } returns serializedString
        inAppRepository.sendUserTargeted(testInAppId, tags)
        verify(exactly = 1) {
            MindboxEventManager.sendUserTargeted(context, serializedString)
        }
    }

    @Test
    fun `send user targeted string`() {
        val testInAppId = "testInAppId"
        val serializedString = ""
        every { inAppSerializationManager.serializeToInAppTargetingString(any(), any()) } returns serializedString
        inAppRepository.sendUserTargeted(testInAppId, null)
        verify(exactly = 0) {
            MindboxEventManager.sendUserTargeted(context, serializedString)
        }
    }

    @Test
    fun `isTimeDelayInapp returns true when in-app exists and has TimeDelay`() {
        val inAppId = "testId"
        val inApp = InAppStub.getInApp().copy(
            id = "testId", frequency = Frequency(
                Frequency.Delay.TimeDelay(
                    time = 1,
                    unit = InAppTime.DAYS
                )
            )
        )
        every { sessionStorageManager.currentSessionInApps } returns listOf(inApp)

        val result = inAppRepository.isTimeDelayInapp(inAppId)

        assertTrue(result)
    }

    @Test
    fun `isTimeDelayInapp returns false when in-app exists but has different delay type`() {
        val inAppId = "testId"
        val inApp = InAppStub.getInApp().copy(
            id = inAppId,
            frequency = InAppStub.getFrequency().copy(
                delay = Frequency.Delay.OneTimePerSession
            )
        )
        every { sessionStorageManager.currentSessionInApps } returns listOf(inApp)

        val result = inAppRepository.isTimeDelayInapp(inAppId)

        assertFalse(result)
    }

    @Test
    fun `isTimeDelayInapp returns false when in-app does not exist`() {
        val inAppId = "nonExistentId"
        every { sessionStorageManager.currentSessionInApps } returns emptyList()

        val result = inAppRepository.isTimeDelayInapp(inAppId)

        assertFalse(result)
    }

    @Test
    fun `isInAppShown returns true when in-app was shown`() {
        val inAppId = "testId"
        every { sessionStorageManager.inAppMessageShownInSession } returns mutableListOf(inAppId)

        val result = inAppRepository.isInAppShown(inAppId)

        assertTrue(result)
    }

    @Test
    fun `isInAppShown returns false when in-app was not shown`() {
        val inAppId = "testId"
        val otherInAppId = "otherId"
        every { sessionStorageManager.inAppMessageShownInSession } returns mutableListOf(otherInAppId)

        val result = inAppRepository.isInAppShown(inAppId)

        assertFalse(result)
    }

    @Test
    fun `isInAppShown returns false when no in-apps were shown`() {
        val inAppId = "testId"
        every { sessionStorageManager.inAppMessageShownInSession } returns mutableListOf()

        val result = inAppRepository.isInAppShown(inAppId)

        assertFalse(result)
    }

    @Test
    fun `saveShownInApp filters timestamps older than two days and adds new timestamp`() {
        val currentTime = System.currentTimeMillis()
        val twoDaysAgo = currentTime - (2 * 24 * 60 * 60 * 1000)
        val twoDaysAndOneMsSecondAgo = currentTime - (2 * 24 * 60 * 60 * 1000) - 1
        val threeDaysAgo = currentTime - (3 * 24 * 60 * 60 * 1000)
        val oneDayAgo = currentTime - (24 * 60 * 60 * 1000)

        val inAppId = "testId"
        val timestamps = listOf(threeDaysAgo, twoDaysAndOneMsSecondAgo, twoDaysAgo, oneDayAgo)
        val expectedTimestamps = listOf(twoDaysAgo, oneDayAgo, currentTime)

        every { timeProvider.currentTimeMillis() } returns currentTime
        every { MindboxPreferences.shownInApps } returns "oldData"
        every { inAppSerializationManager.deserializeToShownInAppsMap("oldData") } returns hashMapOf(
            inAppId to timestamps
        )
        every { inAppSerializationManager.serializeToShownInAppsString(match { it[inAppId] == expectedTimestamps }) } returns "newData"
        every { MindboxPreferences.shownInApps = "newData" } just runs

        inAppRepository.saveShownInApp(inAppId, currentTime)

        verify(exactly = 1) {
            inAppSerializationManager.serializeToShownInAppsString(match { it[inAppId] == expectedTimestamps })
        }
    }

    @Test
    fun `concurrent saveShownInApp does not lose a write`() {
        // Embedded blocks count shows concurrently (one per place). saveShownInApp is a
        // read-modify-write over one preference: without synchronization the last writer
        // erases the other block's entry — caught live on the emulator (14.08).
        val currentTime = System.currentTimeMillis()
        var stored = ""
        val gson = com.google.gson.Gson()
        every { timeProvider.currentTimeMillis() } returns currentTime
        every { MindboxPreferences.shownInApps } answers { stored }
        every { MindboxPreferences.shownInApps = any() } answers { stored = firstArg() }
        every { inAppSerializationManager.deserializeToShownInAppsMap(any()) } answers {
            val raw = firstArg<String>()
            if (raw.isBlank()) {
                hashMapOf()
            } else {
                gson.fromJson(raw, object : com.google.gson.reflect.TypeToken<Map<String, List<Long>>>() {}.type)
            }
        }
        every { inAppSerializationManager.serializeToShownInAppsString(any<Map<String, List<Long>>>()) } answers {
            gson.toJson(firstArg<Map<String, List<Long>>>())
        }

        val ids = (1..8).map { index -> "in-app-$index" }
        val threads = ids.map { id ->
            Thread { inAppRepository.saveShownInApp(id, currentTime) }
        }
        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        val result = inAppSerializationManager.deserializeToShownInAppsMap(stored)
        assertEquals(ids.toSet(), result.keys)
    }

    @Test
    fun `concurrent saveUnShownOperationalInApp does not lose a write`() {
        val operation = "testOperation"
        every { sessionStorageManager.unShownOperationalInApps } returns ConcurrentHashMap()

        val inApps = (1..8).map { index -> InAppStub.getInApp().copy(id = "in-app-$index") }
        val threads = inApps.map { inApp ->
            Thread { inAppRepository.saveUnShownOperationalInApp(operation, inApp) }
        }
        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertEquals(
            inApps.map { inApp -> inApp.id }.toSet(),
            sessionStorageManager.unShownOperationalInApps[operation]?.map { inApp -> inApp.id }?.toSet()
        )
    }

    @Test
    fun `concurrent saveOperationalInApp does not lose a write`() {
        val operation = "testOperation"
        every { sessionStorageManager.operationalInApps } returns ConcurrentHashMap()

        val inApps = (1..8).map { index -> InAppStub.getInApp().copy(id = "in-app-$index") }
        val threads = inApps.map { inApp ->
            Thread { inAppRepository.saveOperationalInApp(operation, inApp) }
        }
        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertEquals(
            inApps.map { inApp -> inApp.id }.toSet(),
            sessionStorageManager.operationalInApps[operation]?.map { inApp -> inApp.id }?.toSet()
        )
    }

    @Test
    fun `concurrent saveTargetedInAppWithEvent does not lose a write`() {
        val inAppId = "testInAppId"
        every { sessionStorageManager.shownInAppIdsWithEvents } returns ConcurrentHashMap()

        val hashes = (1..8).toList()
        val threads = hashes.map { hash ->
            Thread { inAppRepository.saveTargetedInAppWithEvent(inAppId, hash) }
        }
        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertEquals(hashes.toSet(), sessionStorageManager.shownInAppIdsWithEvents[inAppId])
    }
}
