package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl.Companion.STATUS_NO_LOGS
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl.Companion.STATUS_NO_NEW_LOGS
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl.Companion.STATUS_NO_OLD_LOGS
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl.Companion.STATUS_OK
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl.Companion.STATUS_REQUESTED_LOG_IS_TOO_LARGE
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringInteractorImplTest {

    @MockK
    private lateinit var monitoringRepository: MonitoringRepository

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @InjectMockKs
    private lateinit var monitoringInteractor: MonitoringInteractorImpl

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
        every {
            MindboxPreferences.deviceUuid
        } returns "456"
    }


    @Test
    fun `test get status returns status ok`() {
        runTest {
            val validRez = STATUS_OK
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = "abc"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest()
                        .copy(from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test get status returns status no data found`() {
        runTest {
            val validRez = STATUS_NO_LOGS
            coEvery {
                monitoringRepository.getLogs()
            } returns emptyList()
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest()
                        .copy(from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test get status returns status no old logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = STATUS_NO_OLD_LOGS + startDate
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy(time = "2023-02-14T00:00:00", log = "abc"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test get status returns status no new logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = STATUS_NO_NEW_LOGS + endDate
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy(time = "2023-01-14T00:00:00", log = "abc"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test get status returns status too large one log`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = STATUS_REQUESTED_LOG_IS_TOO_LARGE
            val veryBigLog = "abc".repeat(136534)
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = veryBigLog)
            )
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test get status returns status too large more than one log`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = STATUS_REQUESTED_LOG_IS_TOO_LARGE
            val veryBigLog = "abc".repeat(136534)
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(
                LogResponseStub.get().copy(time = "2023-01-20T00:00:00", log = veryBigLog)
            ) + listOf(LogResponseStub.get().copy(time = "2023-01-21T00:00:00", log = veryBigLog))
            monitoringInteractor::class.java.getDeclaredMethod(
                "getStatus",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as String
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with empty logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = emptyList<LogResponse>()
            coEvery {
                monitoringRepository.getLogs()
            } returns emptyList()
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with old logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = emptyList<LogResponse>()
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-01-14T00:00:00", "123123"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with new logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = emptyList<LogResponse>()
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-02-14T00:00:00", "123123"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with one log`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", "123123"))
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", "123123"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with one long log`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val veryBigLog = "abc".repeat(136534)
            val validRez = emptyList<LogResponse>()
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", veryBigLog))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with two or more logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val validRez = listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", "123123")) +  listOf(LogResponseStub.get().copy("2023-01-21T00:00:00", "123123"))
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", "123123")) +  listOf(LogResponseStub.get().copy("2023-01-21T00:00:00", "123123"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test filter sending logs with two or more long logs`() {
        runTest {
            val startDate = "2023-01-15T00:00:00"
            val endDate = "2023-01-30T00:00:00"
            val veryBigLog = "abc".repeat(136534)
            val validRez = listOf(LogResponseStub.get().copy("2023-01-21T00:00:00", "123123"))
            coEvery {
                monitoringRepository.getLogs()
            } returns listOf(LogResponseStub.get().copy("2023-01-20T00:00:00", veryBigLog)) +  listOf(LogResponseStub.get().copy("2023-01-21T00:00:00", "123123"))
            monitoringInteractor::class.java.getDeclaredMethod(
                "filterSendingLogs",
                LogRequest::class.java,
                Continuation::class.java
            ).apply {
                isAccessible = true
                val invocationRez = invokeSuspend(
                    monitoringInteractor,
                    LogRequestStub.getLogRequest().copy(from = startDate, to = endDate)
                ) as List<LogResponse>
                    assertTrue(invocationRez == validRez)
            }
        }
    }

    @Test
    fun `test monitoring checks only current deviceUuid`() {
        val testLogRequests = listOf(
            LogRequestStub.getLogRequest().copy(deviceId = "456"),
            LogRequestStub.getLogRequest().copy(deviceId = "123")
        )
        monitoringInteractor.javaClass.getDeclaredMethod(
            "filterCurrentDeviceUuidLogs",
            List::class.java
        ).apply {
            isAccessible = true
            val invocationRez = invoke(
                monitoringInteractor, testLogRequests
            ) as List<LogRequest>
            assertTrue(invocationRez.size == 1 && invocationRez.first().deviceId == "456")
        }
    }
}

suspend fun Method.invokeSuspend(obj: Any, vararg args: Any?): Any? =
    suspendCoroutine { cont ->
        cont.resume(invoke(obj, *args, cont))
    }