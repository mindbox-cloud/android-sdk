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
        } returns DEVICE_UUID
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
            LogRequestStub.getLogRequest().copy(target = DEVICE_UUID_HASH),
            LogRequestStub.getLogRequest().copy(target = OTHER_DEVICE_UUID_HASH)
        )
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(testLogRequests)
        assertTrue(actualResult.size == 1 && actualResult.first().target == DEVICE_UUID_HASH)
    }

    @Test
    fun `test uppercase deviceUuid is lowercased before hashing`() {
        every {
            MindboxPreferences.deviceUuid
        } returns DEVICE_UUID.uppercase()
        val testLogRequests = listOf(
            LogRequestStub.getLogRequest().copy(target = DEVICE_UUID_HASH)
        )
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(testLogRequests)
        assertEquals(1, actualResult.size)
    }

    @Test
    fun `test target hash comparison is case insensitive`() {
        val testLogRequests = listOf(
            LogRequestStub.getLogRequest().copy(target = DEVICE_UUID_HASH.uppercase())
        )
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(testLogRequests)
        assertEquals(1, actualResult.size)
    }

    @Test
    fun `test blank deviceUuid matches nothing`() {
        every {
            MindboxPreferences.deviceUuid
        } returns ""
        val testLogRequests = listOf(
            LogRequestStub.getLogRequest().copy(target = "d41d8cd98f00b204e9800998ecf8427e"),
            LogRequestStub.getLogRequest().copy(target = DEVICE_UUID_HASH)
        )
        val actualResult = logRequestDataManager.filterCurrentDeviceUuidLogs(testLogRequests)
        assertEquals(emptyList<LogRequest>(), actualResult)
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

    @Test
    fun `md5 matches shared deviceUUID vectors`() {
        val vectors = mapOf(
            DEVICE_UUID to DEVICE_UUID_HASH,
            "126e6225-3170-4089-a6f0-3d1ed8f64153" to OTHER_DEVICE_UUID_HASH,
            "7e570ddf-8270-40a8-a369-b584ff5e9ff0" to "000baa91b37b3c201e3f8604c7845201",
            DEVICE_UUID.uppercase() to DEVICE_UUID_HASH,
        )
        vectors.forEach { (deviceUuid, expectedTarget) ->
            assertEquals(expectedTarget, deviceUuid.md5())
        }
    }

    @Test
    fun `md5 of empty string is the well-known constant`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", "".md5())
    }

    companion object {
        private const val DEVICE_UUID = "216e6225-3170-4089-a6f0-3d1ed8f64153"
        private const val DEVICE_UUID_HASH = "334db432a8f72f64a89664682f7bc032"
        private const val OTHER_DEVICE_UUID_HASH = "248eccb79da2bbca61c133c59e4a1516"
    }
}
