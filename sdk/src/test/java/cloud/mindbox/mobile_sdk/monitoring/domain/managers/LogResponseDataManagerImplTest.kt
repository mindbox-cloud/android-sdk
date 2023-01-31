package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.monitoring.LogResponseStub
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.ZonedDateTime

class LogResponseDataManagerImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @InjectMockKs
    private lateinit var logResponseDataManagerImpl: LogResponseDataManagerImpl


    @Test
    fun `test get status returns status ok`() {

        val expectedResult = LogResponseDataManagerImpl.STATUS_OK
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc")
            )
        val firstLog = LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
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
        val firstLog = LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val expectedResult = LogResponseDataManagerImpl.STATUS_NO_OLD_LOGS + firstLog.time
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc")
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
        val firstLog = LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val expectedResult = LogResponseDataManagerImpl.STATUS_NO_NEW_LOGS + lastLog.time
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc")
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
        val firstLog = LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val expectedResult = LogResponseDataManagerImpl.STATUS_REQUESTED_LOG_IS_TOO_LARGE
        val veryBigLog = "abc".repeat(300000)
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T15:00:00", log = veryBigLog)
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
        val firstLog = LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"),
                LogResponseStub.get().copy(time = "2023-01-21T00:00:00", log = "abc"),
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
    fun `test filter sending logs with no old logs`() {
        val firstLog = LogResponseStub.get().copy(time = "2023-01-30T00:00:01", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val expectedResult = emptyList<LogResponse>()
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"),
                LogResponseStub.get().copy(time = "2023-01-21T00:00:00", log = "abc"),
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
        val firstLog = LogResponseStub.get().copy(time = "2022-12-15T00:00:00", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-01-14T23:58:16")
        val expectedResult = emptyList<LogResponse>()
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"),
                LogResponseStub.get().copy(time = "2023-01-21T00:00:00", log = "abc"),
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
        val firstLog = LogResponseStub.get().copy(time = "2023-01-15T00:00:01", log = "abcd")
        val lastLog = LogResponseStub.get().copy(time = "2023-02-20T00:00:00")
        val veryBigLog = "abc".repeat(300000)
        val expectedResult = listOf(
            LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"),
        )
        val filteredLogs =
            listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"),
                LogResponseStub.get().copy(time = "2023-01-20T15:00:00", log = veryBigLog),
                LogResponseStub.get().copy(time = "2023-01-21T00:00:00", log = "abc"),
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