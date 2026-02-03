package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggle
import cloud.mindbox.mobile_sdk.models.operation.response.FeatureTogglesDto
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
    fun `applyToggles sets SEND_INAPP_SHOW_ERROR to true when featureToggles is true`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = true)
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(true, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `applyToggles sets SEND_INAPP_SHOW_ERROR to false when featureToggles is false`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = false)
            ),
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `applyToggles sets SEND_INAPP_SHOW_ERROR to false when featureToggles is null`() {
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

        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `applyToggles sets SEND_INAPP_SHOW_ERROR to false when settings is null`() {
        val config = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = null,
            abtests = null
        )

        featureToggleManager.applyToggles(config)

        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `applyToggles sets SEND_INAPP_SHOW_ERROR to false when config is null`() {
        featureToggleManager.applyToggles(null)

        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `isEnabled returns false by default`() {
        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
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
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = true)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(true, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))

        val configFalse = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configFalse)
        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
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
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = false)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configFalse)
        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))

        val configTrue = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = true)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(true, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }

    @Test
    fun `applyToggles resets to false when null config is applied after true`() {
        val configTrue = InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = null,
                ttl = null,
                slidingExpiration = null,
                inapp = null,
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = true)
            ),
            abtests = null
        )
        featureToggleManager.applyToggles(configTrue)
        assertEquals(true, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))

        featureToggleManager.applyToggles(null)
        assertEquals(false, featureToggleManager.isEnabled(FeatureToggle.SEND_INAPP_SHOW_ERROR))
    }
}
