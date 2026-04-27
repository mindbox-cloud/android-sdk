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

        mockkObject(MindboxPreferences)
        every { MindboxPreferences.deviceUuid } returns "test-device-uuid-123"
        every { MindboxPreferences.operationsDomainFromConfig } returns null
    }

    // region resolveOperationsDomain priority chain

    @Test
    fun `resolveOperationsDomain returns domain when no operationsDomain configured anywhere`() {
        val config = mockConfiguration.copy(domain = "api.mindbox.ru", operationsDomain = null)

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = null)

        assertEquals("api.mindbox.ru", result)
    }

    @Test
    fun `resolveOperationsDomain returns operationsDomain from init when config value is null`() {
        val config = mockConfiguration.copy(operationsDomain = "anonymizer.client.ru")

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = null)

        assertEquals("anonymizer.client.ru", result)
    }

    @Test
    fun `resolveOperationsDomain returns operationsDomainFromConfig over operationsDomain from init`() {
        val config = mockConfiguration.copy(operationsDomain = "init-host.com")

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = "config-host.com")

        assertEquals("config-host.com", result)
    }

    @Test
    fun `resolveOperationsDomain returns operationsDomainFromConfig when no init value`() {
        val config = mockConfiguration.copy(operationsDomain = null)

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = "config-host.com")

        assertEquals("config-host.com", result)
    }

    @Test
    fun `resolveOperationsDomain falls through blank operationsDomainFromConfig to init value`() {
        val config = mockConfiguration.copy(operationsDomain = "init-host.com")

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = "   ")

        assertEquals("init-host.com", result)
    }

    @Test
    fun `resolveOperationsDomain falls through blank operationsDomain to domain`() {
        val config = mockConfiguration.copy(domain = "api.mindbox.ru", operationsDomain = "   ")

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = null)

        assertEquals("api.mindbox.ru", result)
    }

    @Test
    fun `resolveOperationsDomain preserves https scheme from init value`() {
        val config = mockConfiguration.copy(operationsDomain = "https://proxy.com")

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = null)

        assertEquals("https://proxy.com", result)
    }

    @Test
    fun `resolveOperationsDomain preserves http scheme from config value`() {
        val config = mockConfiguration.copy(operationsDomain = null)

        val result = gatewayManager.resolveOperationsDomain(config, operationsDomainFromConfig = "http://internal-proxy.com")

        assertEquals("http://internal-proxy.com", result)
    }

    // endregion

    // region operationsDomain URL routing

    @Test
    fun `operations URL uses domain when no operationsDomain configured anywhere (backward compat)`() {
        val config = mockConfiguration.copy(domain = "api.mindbox.ru", operationsDomain = null)

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue("Expected domain fallback", url.startsWith("https://api.mindbox.ru/"))
    }

    @Test
    fun `operations URL uses operationsDomain from init when SharedPrefs has no value`() {
        val config = mockConfiguration.copy(operationsDomain = "anonymizer.client.ru")

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.startsWith("https://anonymizer.client.ru/"))
    }

    @Test
    fun `operationsDomainFromConfig in SharedPrefs overrides operationsDomain from init`() {
        every { MindboxPreferences.operationsDomainFromConfig } returns "config-host.com"
        val config = mockConfiguration.copy(operationsDomain = "init-host.com")

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.startsWith("https://config-host.com/"))
    }

    @Test
    fun `operationsDomainFromConfig in SharedPrefs overrides domain when no init value`() {
        every { MindboxPreferences.operationsDomainFromConfig } returns "config-host.com"
        val config = mockConfiguration.copy(operationsDomain = null)

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.startsWith("https://config-host.com/"))
    }

    @Test
    fun `operationsDomain with https scheme preserves scheme in URL`() {
        val config = mockConfiguration.copy(operationsDomain = "https://anonymizer.client.ru")

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.startsWith("https://anonymizer.client.ru/"))
    }

    @Test
    fun `operationsDomain with http scheme uses http scheme`() {
        val config = mockConfiguration.copy(operationsDomain = "http://internal-proxy.com")

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.startsWith("http://internal-proxy.com/"))
    }

    @Test
    fun `logs URL uses operationsDomain from init`() {
        val config = mockConfiguration.copy(operationsDomain = "anonymizer.client.ru")

        val url = gatewayManager.getLogsUrl(config)

        assertTrue(url.startsWith("https://anonymizer.client.ru/"))
    }

    @Test
    fun `product segmentation URL uses operationsDomain from init`() {
        val config = mockConfiguration.copy(operationsDomain = "anonymizer.client.ru")

        val url = gatewayManager.getProductSegmentationUrl(config)

        assertTrue(url.startsWith("https://anonymizer.client.ru/"))
    }

    @Test
    fun `operationsDomain does not affect endpoint ID in URL`() {
        val config = mockConfiguration.copy(
            endpointId = "test-endpoint-id",
            operationsDomain = "anonymizer.client.ru"
        )

        val url = gatewayManager.getCustomerSegmentationsUrl(config)

        assertTrue(url.contains("endpointId=test-endpoint-id"))
    }

    // endregion

    @Test
    fun `getCustomerSegmentationsUrl should return correct URL with endpointId and deviceUUID`() {
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        val actualUrl = gatewayManager.getCustomerSegmentationsUrl(customConfig)

        assertEquals("https://api.mindbox.ru/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=test-endpoint-id&operation=Tracker.CheckCustomerSegments", actualUrl)
    }

    @Test
    fun `getCustomerSegmentationsUrl should use configuration domain and endpointId`() {
        val customConfig = mockConfiguration.copy(
            domain = "custom.domain.com",
            endpointId = "custom-endpoint"
        )

        val actualUrl = gatewayManager.getCustomerSegmentationsUrl(customConfig)

        assertEquals("https://custom.domain.com/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=custom-endpoint&operation=Tracker.CheckCustomerSegments", actualUrl)
    }

    @Test
    fun `getProductSegmentationUrl should return correct URL structure with endpointId and transactionId`() {
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        val actualUrl = gatewayManager.getProductSegmentationUrl(customConfig)

        assertEquals("https://api.mindbox.ru/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=test-endpoint-id&operation=Tracker.CheckProductSegments", actualUrl)
    }

    @Test
    fun `getProductSegmentationUrl should use configuration domain and endpointId`() {
        val customConfig = mockConfiguration.copy(
            domain = "custom.domain.com",
            endpointId = "custom-endpoint"
        )

        val actualUrl = gatewayManager.getProductSegmentationUrl(customConfig)

        assertEquals("https://custom.domain.com/v3/operations/sync?deviceUUID=test-device-uuid-123&endpointId=custom-endpoint&operation=Tracker.CheckProductSegments", actualUrl)
    }

    @Test
    fun `getLogsUrl should return correct URL structure with endpointId, deviceUUID and transactionId`() {
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        val actualUrl = gatewayManager.getLogsUrl(customConfig)

        assertTrue(actualUrl.startsWith("https://api.mindbox.ru/v3/operations/async?"))
        assertTrue(actualUrl.contains("endpointId=test-endpoint-id"))
        assertTrue(actualUrl.contains("operation=MobileSdk.Logs"))
        assertTrue(actualUrl.contains("deviceUUID=test-device-uuid-123"))
        assertTrue(actualUrl.contains("dateTimeOffset=0"))
        assertTrue(actualUrl.contains("transactionId="))

        val transactionId = extractTransactionId(actualUrl)
        assertTrue("Transaction ID should be a valid UUID", isValidUuid(transactionId))
    }

    @Test
    fun `getLogsUrl should generate different transactionIds on multiple calls`() {
        val customConfig = mockConfiguration.copy(
            domain = "api.mindbox.ru",
            endpointId = "test-endpoint-id"
        )

        val url1 = gatewayManager.getLogsUrl(customConfig)
        val url2 = gatewayManager.getLogsUrl(customConfig)

        val transactionId1 = extractTransactionId(url1)
        val transactionId2 = extractTransactionId(url2)

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
