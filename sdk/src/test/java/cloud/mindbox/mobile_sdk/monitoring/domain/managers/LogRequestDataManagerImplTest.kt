package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.monitoring.LogRequestStub
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.jakewharton.threetenabp.AndroidThreeTen
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LogRequestDataManagerImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @InjectMockKs
    private lateinit var logRequestDataManager: LogRequestDataManagerImpl

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        every {
            MindboxPreferences.deviceUuid
        } returns "456"
        AndroidThreeTen.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `test monitoring checks only current deviceUUid null error`() {
        val expectedResult = emptyList<LogRequest>()
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(null)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test monitoring checks only current deviceUUid empty list error`() {
        val expectedResult = emptyList<LogRequest>()
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(emptyList())
        assertEquals(expectedResult, actualResult)
    }


    @Test
    fun `test monitoring checks only current deviceUuid success`() {
        val testLogRequests = listOf(
            LogRequestStub.getLogRequest().copy(deviceId = "456"),
            LogRequestStub.getLogRequest().copy(deviceId = "123")
        )
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(testLogRequests)
        assertTrue(actualResult.size == 1 && actualResult.first().deviceId == "456")
    }

    @Test
    fun `test request id has already been processed`() {
        val processedId = "1234"
        val testHashSet = hashSetOf(processedId)
        assertTrue(logRequestDataManager.checkRequestIdProcessed(testHashSet, processedId))
    }

    @Test
    fun `test request id has not already been processed`() {
        val processedId = "1234"
        val unprocessedId = "5678"
        val testHashSet = hashSetOf(processedId)
        assertFalse(logRequestDataManager.checkRequestIdProcessed(testHashSet, unprocessedId))
    }
}