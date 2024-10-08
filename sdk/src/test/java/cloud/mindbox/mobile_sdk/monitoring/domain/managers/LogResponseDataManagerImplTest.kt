package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.monitoring.LogResponseStub
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import com.jakewharton.threetenabp.AndroidThreeTen
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogResponseDataManagerImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @InjectMockKs
    private lateinit var logResponseDataManagerImpl: LogResponseDataManagerImpl

    @Before
    fun onTestStart() {
        AndroidThreeTen.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `test get status returns status ok`() {
        val expectedResult = LogResponseDataManagerImpl.STATUS_OK
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                )
            )
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abc")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val from = "2023-01-15T00:00:00".convertToZonedDateTime()
        val to = "2023-01-30T00:00:00".convertToZonedDateTime()
        val actualResult = logResponseDataManagerImpl.getStatus(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog, from = from, to = to
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test get status returns no data found`() {
        val expectedResult = LogResponseDataManagerImpl.STATUS_NO_LOGS
        val filteredLogs = emptyList<LogResponse>()
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abc")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val from = "2023-01-15T00:00:00".convertToZonedDateTime()
        val to = "2023-01-30T00:00:00".convertToZonedDateTime()
        val actualResult = logResponseDataManagerImpl.getStatus(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog, from = from, to = to
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test get status returns status no old logs`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val expectedResult = LogResponseDataManagerImpl.STATUS_NO_OLD_LOGS + firstLog.zonedDateTime
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                )
            )

        val from = "2022-01-15T00:00:00".convertToZonedDateTime()
        val to = "2022-01-30T00:00:00".convertToZonedDateTime()
        val actualResult = logResponseDataManagerImpl.getStatus(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from,
            to = to
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test get status returns status no new logs`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val expectedResult = LogResponseDataManagerImpl.STATUS_NO_NEW_LOGS + lastLog.zonedDateTime
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                )
            )

        val from = "2024-01-15T00:00:00"
        val to = "2024-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getStatus(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test get status returns status too large`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val expectedResult = LogResponseDataManagerImpl.STATUS_REQUESTED_LOG_IS_TOO_LARGE
        val veryBigLog = "abc".repeat(300000)
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T15:00:00".convertToZonedDateTime(),
                    log = veryBigLog
                )
            )
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult =
            logResponseDataManagerImpl.getStatus(
                filteredLogs = filteredLogs,
                firstLog = firstLog,
                lastLog = lastLog,
                from = from.convertToZonedDateTime(),
                to = to.convertToZonedDateTime()
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test filter sending logs success`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-21T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
            )
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getFilteredLogs(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )

        assertEquals(filteredLogs, actualResult)
    }

    @Test
    fun `test filter sending logs returns no logs`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val filteredLogs = emptyList<LogResponse>()
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getFilteredLogs(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )

        assertEquals(filteredLogs, actualResult)
    }

    @Test
    fun `test filter sending logs with no old logs`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-30T00:00:01".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val expectedResult = emptyList<LogResponse>()
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-21T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
            )
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getFilteredLogs(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test filter sending logs with no new logs`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2022-12-15T00:00:00".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-14T23:58:16".convertToZonedDateTime())
        val expectedResult = emptyList<LogResponse>()
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-21T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
            )
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getFilteredLogs(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `test filter sending logs with long log`() {
        val firstLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-01-15T00:00:01".convertToZonedDateTime(), log = "abcd")
        val lastLog = LogResponseStub.get()
            .copy(zonedDateTime = "2023-02-20T00:00:00".convertToZonedDateTime())
        val veryBigLog = "abc".repeat(300000)
        val expectedResult = listOf(
            LogResponseStub.get()
                .copy(zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(), log = "abc"),
        )
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-20T15:00:00".convertToZonedDateTime(),
                    log = veryBigLog
                ),
                LogResponseStub.get().copy(
                    zonedDateTime = "2023-01-21T00:00:00".convertToZonedDateTime(),
                    log = "abc"
                ),
            )
        val from = "2023-01-15T00:00:00"
        val to = "2023-01-30T00:00:00"
        val actualResult = logResponseDataManagerImpl.getFilteredLogs(
            filteredLogs = filteredLogs,
            firstLog = firstLog,
            lastLog = lastLog,
            from = from.convertToZonedDateTime(),
            to = to.convertToZonedDateTime()
        )
        assertEquals(expectedResult, actualResult)
    }
}
