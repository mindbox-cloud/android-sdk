package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class GatewayManagerTest {

    private lateinit var gatewayManager: GatewayManager
    private lateinit var mockMindboxServiceGenerator: MindboxServiceGenerator
    private lateinit var mockConfiguration: Configuration

    @Before
    fun setUp() {
        mockMindboxServiceGenerator = mockk(relaxed = true)
        gatewayManager = GatewayManager(mockMindboxServiceGenerator)
        mockConfiguration = Configuration(
            configurationId = 1L,
            previousInstallationId = "prev-install-id",
            previousDeviceUUID = "prev-device-uuid",
            endpointId = "test-endpoint-id",
            domain = "api.mindbox.ru",
            packageName = "com.test.app",
            versionName = "1.0.0",
            versionCode = "1",
            subscribeCustomerIfCreated = true,
            shouldCreateCustomer = true
        )

        // Mock MindboxPreferences.deviceUuid
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.deviceUuid } returns "test-device-uuid-123"
    }

    @Test
    fun `getCustomerSegmentationsUrl should return correct URL with endpointId and deviceUUID`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        // When
        val actualUrl = gatewayManager.getCustomerSegmentationsUrl(customConfig)

        // Then
        assertEquals("https://api.mindbox.ru/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=test-endpoint-id&operation=Tracker.CheckCustomerSegments", actualUrl)
    }

    @Test
    fun `getCustomerSegmentationsUrl should use configuration domain and endpointId`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "custom.domain.com",
            endpointId = "custom-endpoint"
        )

        // When
        val actualUrl = gatewayManager.getCustomerSegmentationsUrl(customConfig)

        // Then
        assertEquals("https://custom.domain.com/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=custom-endpoint&operation=Tracker.CheckCustomerSegments", actualUrl)
    }

    @Test
    fun `getProductSegmentationUrl should return correct URL structure with endpointId and transactionId`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        // When
        val actualUrl = gatewayManager.getProductSegmentationUrl(customConfig)

        // Then
        assertEquals("https://api.mindbox.ru/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=test-endpoint-id&operation=Tracker.CheckProductSegments", actualUrl)
    }

    @Test
    fun `getProductSegmentationUrl should use configuration domain and endpointId`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "custom.domain.com",
            endpointId = "custom-endpoint"
        )

        // When
        val actualUrl = gatewayManager.getProductSegmentationUrl(customConfig)

        // Then
        assertEquals("https://custom.domain.com/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=custom-endpoint&operation=Tracker.CheckProductSegments", actualUrl)
    }

    @Test
    fun `getLogsUrl should return correct URL structure with endpointId, deviceUUID and transactionId`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        // When
        val actualUrl = gatewayManager.getLogsUrl(customConfig)

        // Then
        // Since UUID.randomUUID() generates random values, we'll test the structure instead
        assertTrue(actualUrl.startsWith("https://api.mindbox.ru/v3/operations/async?"))
        assertTrue(actualUrl.contains("endpointId=test-endpoint-id"))
        assertTrue(actualUrl.contains("operation=MobileSdk.Logs"))
        assertTrue(actualUrl.contains("deviceUUID=test-device-uuid-123"))
        assertTrue(actualUrl.contains("dateTimeOffset=0"))
        assertTrue(actualUrl.contains("transactionId="))

        // Verify transactionId is a valid UUID format
        val transactionId = extractTransactionId(actualUrl)
        assertTrue("Transaction ID should be a valid UUID", isValidUuid(transactionId))
    }

    @Test
    fun `getLogsUrl should generate different transactionIds on multiple calls`() {
        // Given
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        // When
        val url1 = gatewayManager.getLogsUrl(customConfig)
        val url2 = gatewayManager.getLogsUrl(customConfig)

        // Then
        val transactionId1 = extractTransactionId(url1)
        val transactionId2 = extractTransactionId(url2)

        // Transaction IDs should be different (UUID.randomUUID() generates unique values)
        assertTrue("Transaction IDs should be different", transactionId1 != transactionId2)
        assertTrue("Transaction ID 1 should be valid UUID", isValidUuid(transactionId1))
        assertTrue("Transaction ID 2 should be valid UUID", isValidUuid(transactionId2))
    }

    private fun isValidUuid(uuid: String): Boolean {
        return runCatching { UUID.fromString(uuid) }.isSuccess
    }

    private fun extractTransactionId(url: String): String {
        return url.split("&", "?")
            .first { it.startsWith("transactionId=") }
            .split('=')
            .last()
    }
}
