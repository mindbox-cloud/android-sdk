package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import android.app.Application
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsMobileConfigSerializationManagerTest {

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
    fun `deserialize config abtests`() {
        val json = getJson("abtests.json")

        val expected = listOf(
            ABTestDto(
                id = "0ec6be6b-421f-464b-9ee4-348a5292a5fd",
                salt = "c0e2682c-3d0f-4291-9308-9e48a16eb3c8",
                sdkVersion = SdkVersion(
                    minVersion = 6,
                    maxVersion = null,
                ),
                variants = listOf(
                    ABTestDto.VariantDto(
                        id = "3162b011-b30f-4300-a72b-bd5cac0d6607",
                        modulus = ABTestDto.VariantDto.ModulusDto(
                            lower = 0,
                            upper = 50,
                        ),
                        objects = listOf(
                            ABTestDto.VariantDto.ObjectsDto(
                                type = "inapps",
                                kind = "concrete",
                                inapps = listOf(
                                    "655f5ffa-de86-4224-a0bf-229fe208ed0d",
                                    "b33ca779-3c99-481f-ad46-91282b0caf04"
                                )
                            )
                        ),
                    ),
                    ABTestDto.VariantDto(
                        id = "dbc39dce-db4f-4dc9-9133-378df018233b",
                        modulus = ABTestDto.VariantDto.ModulusDto(
                            lower = 50,
                            upper = 100,
                        ),
                        objects = listOf(
                            ABTestDto.VariantDto.ObjectsDto(
                                type = "inapps",
                                kind = "all",
                                inapps = null
                            )
                        )
                    )
                )
            )
        )

        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNull(config.inApps)
        assertNull(config.monitoring)
        assertNull(config.settings)

        assertEquals(expected, config.abtests)
    }

    @Test
    fun `deserialize config all`() {
        val json = getJson("ConfigParsing/ConfigWithSettingsABTestsMonitoringInapps.json")
        val config = manager.deserializeToConfigDtoBlank(json.toString())!!

        assertNotNull(config.inApps)
        assertEquals(4, config.inApps!!.size)

        assertNotNull(config.monitoring)
        assertEquals(2, config.monitoring!!.logs!!.size)

        assertNotNull(config.settings)
        assertEquals(3, config.settings!!.operations!!.size)
        assertNotNull(config.settings.ttl?.inApps)
        assertNotNull(config.settings.slidingExpiration?.config)

        assertNotNull(config.abtests)
        assertEquals(2, config.abtests!!.size)
    }

    @Test
    fun settings_config_shouldParseSuccessfully() {
        val json = getJson("ConfigParsing/Settings/SettingsConfig.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull(config.operations)
        assertNotNull(config.operations!!["viewProduct"])
        assertNotNull(config.operations["viewCategory"])
        assertNotNull(config.operations["setCart"])

        assertNotNull(config.ttl)
        assertNotNull(config.ttl?.inApps)

        assertNotNull(config.slidingExpiration)
        assertNotNull(config.slidingExpiration?.config)
    }

    // MARK: - Operations

    @Test
    fun settings_config_withOperationsError_shouldSetOperationsToNull() {
        val json = getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsError.json")
        val config = manager.deserializeSettings(json)!!

        assertNull(
            "Operations must be `null` if the key `operations` is not found",
            config.operations,
        )
        assertNull(config.operations?.get("viewProduct"))
        assertNull(config.operations?.get("viewCategory"))
        assertNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsTypeError_shouldSetOperationsToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNull(
            "Operations must be `null` if the type of `operations` is not a `SettingsOperations`",
            config.operations,
        )
        assertNull(config.operations?.get("viewProduct"))
        assertNull(config.operations?.get("viewCategory"))
        assertNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewProductError_shouldSetViewProductToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewProductError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNull(
            "ViewProduct must be `null` if the key `viewProduct` is not found",
            config.operations?.get("viewProduct"),
        )
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewProductTypeError_shouldSetViewProductToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewProductTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNull(
            "ViewProduct must be `null` if the type of `viewProduct` is not an `Operation`",
            config.operations?.get("viewProduct")
        )
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewProductSystemNameError_shouldSetViewProductToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewProductSystemNameError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNull(
            "ViewProduct must be `null` if the key `systemName` is not found",
            config.operations?.get("viewProduct")
        )
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewProductSystemNameTypeError_shouldSetViewProductToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewProductSystemNameTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNull(
            "ViewProduct must be `null` if the type of `systemName` is not a `String`",
            config.operations?.get("viewProduct"),
        )
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withAllOperationsWithErrors_shouldSetOperationsToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsAllOperationsWithErrors.json")
        val config = manager.deserializeSettings(json)!!

        assertNull(config.operations?.get("viewProduct"))
        assertNull(config.operations?.get("viewCategory"))
        assertNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withAllOperationsWithTypeErrors_shouldSetOperationsToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsAllOperationsWithTypeErrors.json")
        val config = manager.deserializeSettings(json)!!

        assertEquals(
            "Operations must be `null` if all three operations are `null`",
            true,
            config.operations?.isEmpty(),
        )
        assertNull(config.operations?.get("viewProduct"))
        assertNull(config.operations?.get("viewCategory"))
        assertNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewCategoryAndSetCartError_shouldSetViewCategoryAndSetCartToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewCategoryAndSetCartError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(
            "ViewProduct must be successfully parsed",
            config.operations?.get("viewProduct"),
        )
        assertNull(
            "ViewCategory must be `null` if the key `viewCategory` is not found",
            config.operations?.get("viewCategory"),
        )
        assertNull(
            "setCart must be `null` if the key `setCart` is not found",
            config.operations?.get("setCart"),
        )

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewCategoryAndSetCartTypeError_shouldSetViewCategoryAndSetCartToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewCategoryAndSetCartTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(
            "ViewProduct must be successfully parsed",
            config.operations?.get("viewProduct")
        )
        assertNull(
            "ViewCategory must be `null` if the type of `viewCategory` is not a `SettingsOperations`",
            config.operations?.get("viewCategory"),
        )
        assertNull(
            "setCart must be `null` if the type `setCart` is not a `SettingsOperations`",
            config.operations?.get("setCart"),
        )

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewCategoryAndSetCartSystemNameError_shouldSetViewCategoryAndSetCartToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewCategoryAndSetCartSystemNameError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(
            "ViewProduct must be successfully parsed",
            config.operations?.get("viewProduct"),
        )
        assertNull(
            "ViewCategory must be `null` if the key `systemName` is not found",
            config.operations?.get("viewCategory"),
        )
        assertNull(
            "setCart must be `null` if the key `systemName` is not found",
            config.operations?.get("setCart"),
        )

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewCategoryAndSetCartSystemNameTypeError_shouldSetViewCategoryAndSetCartToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewCategoryAndSetCartSystemNameTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(
            "ViewProduct must be successfully parsed",
            config.operations?.get("viewProduct"),
        )
        assertNull(
            "ViewCategory must be `null` if the type of `systemName` is not a `String`",
            config.operations?.get("viewCategory"),
        )
        assertNull(
            "setCart must be `null` if the type `systemName` is not a `String`",
            config.operations?.get("setCart"),
        )

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withOperationsViewCategoryAndSetCartSystemNameMixedError_shouldSetViewCategoryAndSetCartToNull() {
        val json =
            getJson("ConfigParsing/Settings/OperationsErrors/SettingsOperationsViewCategoryAndSetCartSystemNameMixedError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(
            "ViewProduct must be successfully parsed",
            config.operations?.get("viewProduct"),
        )
        assertNull(
            "ViewCategory must be `null` if the key `systemName` is not found",
            config.operations?.get("viewCategory"),
        )
        assertNull(
            "setCart must be `null` if the type `systemName` is not a `String`",
            config.operations?.get("setCart"),
        )

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)
    }

    // MARK: - TTL

    @Test
    fun settings_config_withTtlError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/TtlErrors/SettingsTtlError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNull("TTL must be `null` if the key `ttl` is not found", config.ttl)
        assertNull("TTL must be `null`", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withTtlTypeError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/TtlErrors/SettingsTtlTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNull(
            "TTL must be `null` if the type of `inapps` is not a `TimeToLive`",
            config.ttl,
        )
        assertNull("TTL must be `null`", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withTtlInappsError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/TtlErrors/SettingsTtlInappsError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNull("TTL must be `null` if the key `inapps` is not found", config.ttl)
        assertNull("TTL must be `null`", config.ttl?.inApps)
    }

    @Test
    fun settings_config_withTtlInappsTypeError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/TtlErrors/SettingsTtlInappsTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNull("TTL must be `null` if the key `inapps` is not a `String`", config.ttl)
        assertNull(
            "TTL must be `null` if the key `inapps` is not a `String`",
            config.ttl?.inApps,
        )
    }

    // MARK: - SlidingExpiration

    @Test
    fun settings_config_withSlidingExpirationError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/SlidingExpirationErrors/SettingsSlidingExpirationError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)

        assertNull("SlidingExpiration must be `null` if the key `slidingExpiration` is not found", config.slidingExpiration)
        assertNull("Config session time must be `null`", config.slidingExpiration?.config)
    }

    @Test
    fun settings_config_withSlidingExpirationTypeError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/SlidingExpirationErrors/SettingsSlidingExpirationTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)

        assertNull(
            "SlidingExpiration must be `null` if the type of `config` is not a `slidingExpiration`",
            config.slidingExpiration,
        )
        assertNull("Config session time must be `null`", config.slidingExpiration?.config)
    }

    @Test
    fun settings_config_withSlidingExpirationConfigError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/SlidingExpirationErrors/SettingsSlidingExpirationConfigsError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)

        assertNull("SlidingExpiration must be `null` if the key `config` is not found", config.slidingExpiration)
        assertNull("Config session time must be `null`", config.slidingExpiration?.config)
    }

    @Test
    fun settings_config_withSlidingExpirationConfigTypeError_shouldSetTtlToNull() {
        val json = getJson("ConfigParsing/Settings/SlidingExpirationErrors/SettingsSlidingExpirationConfigTypeError.json")
        val config = manager.deserializeSettings(json)!!

        assertNotNull("Operations must be successfully parsed", config.operations)
        assertNotNull(config.operations?.get("viewProduct"))
        assertNotNull(config.operations?.get("viewCategory"))
        assertNotNull(config.operations?.get("setCart"))

        assertNotNull("TTL must be successfully parsed", config.ttl)
        assertNotNull("TTL must be successfully parsed", config.ttl?.inApps)

        assertNull("SlidingExpiration must be `null` if the key `config` is not a `String`", config.slidingExpiration)
        assertNull(
            "Config session time must be `null` if the key `config` is not a `String`",
            config.slidingExpiration?.config,
        )
    }
}
