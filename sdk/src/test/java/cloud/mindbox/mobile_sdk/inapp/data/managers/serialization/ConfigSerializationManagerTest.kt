package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import android.app.Application
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test

internal class ConfigSerializationManagerTest {

    private val manager: MobileConfigSerializationManagerImpl by mindboxInject {
        mobileConfigSerializationManager as MobileConfigSerializationManagerImpl
    }

    private val context = mockk<Application>(relaxed = true) {
        every { applicationContext } returns this
    }

    @Before
    fun onTestStart() {
        MindboxDI.init(context)
    }

    @Test
    fun config_shouldParseSuccessfully() {
        // Correct config
        val json = getJson("ConfigParsing/Config/ConfigWithSettingsABTestsMonitoringInapps.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNotNull(config.inApps)
        assertNotNull(config.abtests)
        assertNotNull(config.settings)
        assertNotNull(config.monitoring)
    }

    @Test
    fun config_withSettingsError_shouldSetSettingsToNull() {
        // Key is `settingsTest` instead of `settings`
        val json = getJson("ConfigParsing/Config/ConfigSettingsError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.settings)
        assertNotNull(config.inApps)
        assertNotNull(config.abtests)
        assertNotNull(config.monitoring)
    }

    @Test
    fun config_withSettingsTypeError_shouldSetSettingsToNull() {
        // Type of `settings` is Int instead of Settings
        val json = getJson("ConfigParsing/Config/ConfigSettingsTypeError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.settings)
        assertNotNull(config.inApps)
        assertNotNull(config.abtests)
        assertNotNull(config.monitoring)
    }

    @Test
    fun config_withMonitoringError_shouldSetMonitoringToNull() {
        // Key is `monitoringTest` instead of `monitoring`
        val json = getJson("ConfigParsing/Config/ConfigMonitoringError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
        assertNotNull(config.abtests)
    }

    @Test
    fun config_withMonitoringTypeError_shouldSetMonitoringToNull() {
        // Type of `monitoring` is Int instead of Monitoring
        val json = getJson("ConfigParsing/Config/ConfigMonitoringTypeError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
        assertNotNull(config.abtests)
    }

    @Test
    fun config_withABTestsError_shouldSetABTestsToNull() {
        // Key is `abtestsTest` instead of `abtests`
        val json = getJson("ConfigParsing/Config/ConfigABTestsError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
    }

    @Test
    fun config_withABTestsTypeError_shouldSetABTestsToNull() {
        // Type of `abtests` is Int instead of [ABTest]
        val json = getJson("ConfigParsing/Config/ConfigABTestsTypeError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
    }

    @Test
    fun config_withInAppsError_shouldSetInAppsToNull() {
        // Key is `inappsTest` instead of `inapps`
        val json = getJson("ConfigParsing/Config/ConfigInAppsError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.inApps)
        assertNotNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
    }

    @Test
    fun config_withInAppsTypeError_shouldSetInAppsToNull() {
        // Type of `inapps` is Int instead of FailableDecodableArray<InAppDTO>
        val json = getJson("ConfigParsing/Config/ConfigInAppsTypeError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.inApps)
        assertNotNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
    }

    @Test
    fun config_withABTestsOneElementError_shouldSetABTestsToNull() {
        // Key is `saltTest` instead of `salt`
        val json = getJson("ConfigParsing/Config/ConfigABTestsOneElementError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
    }

    @Test
    fun config_withABTestsOneElementTypeError_shouldSetABTestsToNull() {
        // Type of `variants` is Int instead of [ABTestVariant]
        val json = getJson("ConfigParsing/Config/ConfigABTestsOneElementTypeError.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNotNull(config.monitoring)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)
    }

    @Test
    fun `empty config`() {
        // Key is `abtestsTest` instead of `abtests`
        mockkStatic(::mindboxLogE)
        val json = getJson("ConfigParsing/Config/EmptyConfigs/EmptyConfig.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNull(config.monitoring)
        assertNull(config.settings)
        assertNull(config.inApps)

        verify(exactly = 0) { mindboxLogE(any(), any()) }
    }

    @Test
    fun `no monitoring in config`() {
        // Key is `abtestsTest` instead of `abtests`
        mockkStatic(::mindboxLogE)
        val json = getJson("ConfigParsing/Config/EmptyConfigs/NoMonitoring.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.monitoring)
        assertNotNull(config.abtests)
        assertNotNull(config.settings)
        assertNotNull(config.inApps)

        verify(exactly = 0) { mindboxLogE(any(), any()) }
    }

    @Test
    fun `empty monitoring in config`() {
        // Key is `abtestsTest` instead of `abtests`
        mockkStatic(::mindboxLogE)
        val json = getJson("ConfigParsing/Config/EmptyConfigs/EmptyMonitoring.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNull(config.monitoring)
        assertNull(config.settings)
        assertNull(config.inApps)

        verify(exactly = 0) { mindboxLogE(any(), any()) }
    }

    @Test
    fun `empty monitoring logs in config`() {
        // Key is `abtestsTest` instead of `abtests`
        mockkStatic(::mindboxLogE)
        val json = getJson("ConfigParsing/Config/EmptyConfigs/EmptyLogsMonitoring.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.abtests)
        assertNull(config.monitoring)
        assertNull(config.settings)
        assertNull(config.inApps)

        verify(exactly = 0) { mindboxLogE(any(), any()) }
    }
}