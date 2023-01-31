package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogRequestDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogResponseDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest

@OptIn(ExperimentalCoroutinesApi::class)
internal class MonitoringInteractorImplTest : KoinTest {

    @MockK
    private lateinit var monitoringRepository: MonitoringRepository

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @MockK
    private lateinit var logResponseDataManager: LogResponseDataManager

    @MockK
    private lateinit var logRequestDataManager: LogRequestDataManager

    @InjectMockKs
    private lateinit var monitoringInteractor: MonitoringInteractorImpl

    @get:Rule
    val mockkRule = MockKRule(this)

 /*   @Test
    fun `test monitoring happy path`() = runTest {
        val processedRequestId = "123"
        val unprocessedRequestId = "456"
        val myDeviceUuid = "789"
        val notMyDeviceUuid = "345"
        val logRequests = listOf(
            LogRequestStub.getLogRequest().copy(
                requestId = unprocessedRequestId,
                deviceId = myDeviceUuid,
                from = "2023-01-02T00:00:00".convertToZonedDateTime(),
                to = "2023-01-10T00:00:00".convertToZonedDateTime()
            ),
            LogRequestStub.getLogRequest().copy(
                requestId = processedRequestId,
                deviceId = myDeviceUuid,
                from = "2023-01-15T00:00:00".convertToZonedDateTime(),
                to = "2023-01-30T00:00:00".convertToZonedDateTime()
            ),
            LogRequestStub.getLogRequest().copy(
                requestId = unprocessedRequestId,
                deviceId = notMyDeviceUuid,
                from = "2023-01-15T00:00:00".convertToZonedDateTime(),
                to = "2023-01-30T00:00:00".convertToZonedDateTime()
            ),
            LogRequestStub.getLogRequest().copy(
                requestId = unprocessedRequestId,
                deviceId = myDeviceUuid,
                from = "2023-01-15T00:00:00".convertToZonedDateTime(),
                to = "2023-01-30T00:00:00".convertToZonedDateTime()
            ),
            LogRequestStub.getLogRequest().copy(
                requestId = unprocessedRequestId,
                deviceId = myDeviceUuid,
                from = "2023-02-15T00:00:00".convertToZonedDateTime(),
                to = "2023-02-30T00:00:00".convertToZonedDateTime()
            ),
            LogRequestStub.getLogRequest().copy(
                requestId = unprocessedRequestId,
                deviceId = myDeviceUuid,
                from = "2022-01-15T00:00:00".convertToZonedDateTime(),
                to = "2022-01-30T00:00:00".convertToZonedDateTime()
            )
        )
        coEvery {
            monitoringRepository.getFirstLog()
        } returns LogResponseStub.get().copy("2023-01-01T00:00:00")
        coEvery {
            monitoringRepository.getLastLog()
        } returns LogResponseStub.get().copy("2023-01-31T00:00:00")

        every { inAppRepository.listenInAppConfig() } returns flowOf(
            InAppConfigStub.getConfig().copy(
                monitoring = logRequests
            )
        )
        every {
            logRequestDataManager.filterCurrentDeviceUuidLogs(logRequests)
        }
        monitoringInteractor.processLogs()
        verify {
        }
    }
*/
}