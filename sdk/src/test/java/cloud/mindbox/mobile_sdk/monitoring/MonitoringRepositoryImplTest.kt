package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.monitoring.data.repositories.MonitoringRepositoryImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class MonitoringRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val monitoringRepository by lazy {
        MonitoringRepositoryImpl(
            monitoringDao = mockk(),
            monitoringMapper = mockk(),
            gson = Gson(),
            logStoringDataChecker = mockk(),
            monitoringValidator = mockk(),
            gatewayManager = mockk()
        )
    }

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
    }

    @Test
    fun `log request ids is not empty and is a valid json`() {
        val testHashSet = hashSetOf(
            "71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5"
        )
        every { MindboxPreferences.logsRequestIds } returns
            "[\"71110297-58ad-4b3c-add1-60df8acb9e5e\",\"ad487f74-924f-44f0-b4f7-f239ea5643c5\"]"
        assertTrue(
            monitoringRepository.getRequestIds()
                .containsAll(testHashSet) && testHashSet.containsAll(monitoringRepository.getRequestIds())
        )
    }

    @Test
    fun `log request ids returns null`() {
        every { MindboxPreferences.logsRequestIds } returns "not_json"
        assertNotNull(monitoringRepository.getRequestIds())
    }

    @Test
    fun `log request ids empty`() {
        val expectedIds = hashSetOf<String>()
        every { MindboxPreferences.logsRequestIds } returns ""
        val actualIds = monitoringRepository.getRequestIds()
        assertTrue(
            expectedIds.containsAll(actualIds) && expectedIds.containsAll(
                monitoringRepository.getRequestIds()
            )
        )
    }

    @Test
    fun `log request ids is not empty and is not a json`() {
        every { MindboxPreferences.logsRequestIds } returns "123"
        val expectedResult = hashSetOf<String>()
        val actualResult = monitoringRepository.getRequestIds()
        assertTrue(actualResult.containsAll(expectedResult))
    }

    @Test
    fun `save log request success`() {
        val expectedJson = """
            |["123","456"]
        """.trimMargin()
        every { MindboxPreferences.logsRequestIds } returns "[123]"
        monitoringRepository.saveRequestId("456")
        verify(exactly = 1) {
            MindboxPreferences.logsRequestIds = expectedJson
        }
    }
}
