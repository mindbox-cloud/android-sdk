package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class MonitoringValidatorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @InjectMockKs
    private lateinit var monitoringValidator: MonitoringValidator

    @Test
    fun `monitoring validation success`() {
        assertTrue(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "123", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation requestId error empty string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation requestId error null`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation deviceId error empty string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "", deviceId = "", from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation deviceId error null`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "asd", deviceId = "", from = "2023-01-15T00:00:00", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation from error empty string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "123", deviceId = "abvc", from = "", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation from error null`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "123", deviceId = "abvc", from = "", to = "2023-01-30T00:00:00")
        ))
    }


    @Test
    fun `monitoring validation from error random string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "123", deviceId = "abvc", from = "null", to = "2023-01-30T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation to error empty string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "asd", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "")
        ))
    }
    @Test
    fun `monitoring validation to error null`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "asd", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "")
        ))
    }

    @Test
    fun `monitoring validation to error random string`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(
            LogRequestStub.getLogRequestDtoBlank()
                .copy(requestId = "asd", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "abc")
        ))
    }

    @Test
    fun `monitoring validation from parsing error`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(LogRequestStub.getLogRequestDtoBlank()
            .copy(requestId = "asd", deviceId = "abvc", from = "1970-01-01T00:00:00", to = "2023-01-15T00:00:00")
        ))
    }

    @Test
    fun `monitoring validation to parsing error`() {
        assertFalse(monitoringValidator.validateLogRequestDtoBlank(LogRequestStub.getLogRequestDtoBlank()
            .copy(requestId = "asd", deviceId = "abvc", from = "2023-01-15T00:00:00", to = "1970-01-01T00:00:00")
        ))
    }
    @Test
    fun `monitoring validation error dates filter`() {
        monitoringValidator.validateMonitoring(MonitoringEntity(id = 0, time = "1970-01-01T00:00:00", log = ""))
    }


}