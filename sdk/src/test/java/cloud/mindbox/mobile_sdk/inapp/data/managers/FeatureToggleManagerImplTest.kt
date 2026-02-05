package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FeatureToggleManagerImplTest {

    private lateinit var featureToggleManager: FeatureToggleManagerImpl

    @Before
    fun onTestStart() {
        featureToggleManager = FeatureToggleManagerImpl()
    }

    @Test
    fun `applyToggles sets shouldSendInAppShowError to true when featureToggles contains true`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to true)
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles sets shouldSendInAppShowError to false when featureToggles contains false`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to false)
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(false, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles handles multiple toggles`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf(
                    "shouldSendInAppShowError" to true,
                    "anotherToggle" to false
                )
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
        assertEquals(false, featureToggleManager.isEnabled("anotherToggle"))
    }

    @Test
    fun `applyToggles return true when null values in featureToggles map`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf(
                    "shouldSendInAppShowError" to true,
                    "invalidToggle" to null
                )
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
        assertEquals(true, featureToggleManager.isEnabled("invalidToggle"))
    }

    @Test
    fun `applyToggles returns true when featureToggles is null`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = null
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles returns true when settings is null`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = null,
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles returns true when config is null`() {
        featureToggleManager.applyToggles(null)

        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `isEnabled returns true by default`() {
        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles can change value from true to false`() {
        val configTrue = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to true)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))

        val configFalse = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configFalse)
        assertEquals(false, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles can change value from false to true`() {
        val configFalse = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configFalse)
        assertEquals(false, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))

        val configTrue = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to true)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
    }

    @Test
    fun `applyToggles clears previous toggles when null config is applied`() {
        val configTrue = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("shouldSendInAppShowError" to false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(false, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))

        featureToggleManager.applyToggles(null)
        assertEquals(true, featureToggleManager.isEnabled("shouldSendInAppShowError"))
    }

    @Test
    fun `applyToggles clears previous toggles when new config is applied`() {
        val config1 = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf(
                    "shouldSendInAppShowError" to false,
                    "toggle1" to true
                )
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(config1)
        assertEquals(false, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
        assertEquals(true, featureToggleManager.isEnabled("toggle1"))

        val config2 = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = mapOf("toggle2" to false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(config2)
        assertEquals(true, featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE))
        assertEquals(true, featureToggleManager.isEnabled("toggle1"))
        assertEquals(false, featureToggleManager.isEnabled("toggle2"))
    }
}
