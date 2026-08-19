package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class ConfigurationTest {

    private fun configuration(versionCode: String) = Configuration(
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "test-endpoint",
        domain = "api.mindbox.ru",
        packageName = "com.example.app",
        versionName = "1.2.3",
        versionCode = versionCode,
        subscribeCustomerIfCreated = false,
        shouldCreateCustomer = true,
    )

    @Test
    fun `hostAppVersion contains versionCode in brackets when it is not blank`() {
        assertEquals("1.2.3(123)", configuration(versionCode = "123").hostAppVersion)
    }

    @Test
    fun `hostAppVersion is versionName only when versionCode is blank`() {
        assertEquals("1.2.3", configuration(versionCode = "").hostAppVersion)
    }

    @Test
    fun `user agent contains app version with versionCode when it is not blank`() {
        val userAgent = configuration(versionCode = "123").getUserAgent()

        assertTrue(
            "Unexpected User-Agent: $userAgent",
            userAgent.endsWith(" com.example.app/1.2.3(123)"),
        )
    }

    @Test
    fun `user agent contains app version without versionCode when it is blank`() {
        val userAgent = configuration(versionCode = "").getUserAgent()

        assertTrue(
            "Unexpected User-Agent: $userAgent",
            userAgent.endsWith(" com.example.app/1.2.3"),
        )
    }

    @Test
    fun `short user agent contains versionCode when it is not blank`() {
        assertEquals(
            "com.example.app/1.2.3-123 mindbox.sdk/${BuildConfig.VERSION_NAME}",
            configuration(versionCode = "123").getShortUserAgent(),
        )
    }

    @Test
    fun `short user agent contains versionName only when versionCode is blank`() {
        assertEquals(
            "com.example.app/1.2.3 mindbox.sdk/${BuildConfig.VERSION_NAME}",
            configuration(versionCode = "").getShortUserAgent(),
        )
    }
}
