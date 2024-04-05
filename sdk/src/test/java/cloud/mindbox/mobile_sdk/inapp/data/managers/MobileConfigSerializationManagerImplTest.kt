package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileConfigSerializationManagerImplTest {

    private val manager by lazy {
        MobileConfigSerializationManagerImpl(Gson())
    }

    @Test
    fun `deserialize config abtests`() {
        val json = getJson()

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


        val config = manager.deserializeToConfigDtoBlank(json)!!

        assertNull(config.inApps)
        assertNull(config.monitoring)
        assertNull(config.settings)

        assertEquals(expected, config.abtests)
    }

    private fun getJson(): String {
        return javaClass.classLoader!!.getResourceAsStream("abtests.json")!!.bufferedReader()
            .use { it.readText() }
    }
}