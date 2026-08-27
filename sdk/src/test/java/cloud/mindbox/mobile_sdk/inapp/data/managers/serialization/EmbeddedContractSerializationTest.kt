package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.models.operation.response.DisplayConditionsDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import com.google.gson.JsonParser
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MOBILE-333 additions to the config contract. Restriction fields resolve in favour of
 * showing: `null`, an unknown `$type` or broken content read as "no restriction" and never
 * drop the in-app.
 */
class EmbeddedContractSerializationTest {

    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson
    private val manager = MobileConfigSerializationManagerImpl(gson)

    private fun json(raw: String) = JsonParser.parseString(raw).asJsonObject

    // displayConditions

    @Test
    fun `displayConditions directCall is parsed`() {
        val dto = manager.deserializeToDisplayConditionsDto(json("""{"${'$'}type":"directCall"}"""))

        assertTrue(dto is DisplayConditionsDto.DirectCallDto)
    }

    @Test
    fun `displayConditions null reads as show by trigger`() {
        assertNull(manager.deserializeToDisplayConditionsDto(null))
    }

    @Test
    fun `displayConditions with unknown type reads as show by trigger`() {
        assertNull(manager.deserializeToDisplayConditionsDto(json("""{"${'$'}type":"pushOnly"}""")))
    }

    // frequency unlimited

    @Test
    fun `frequency unlimited is parsed`() {
        val dto = manager.deserializeToFrequencyDto(json("""{"${'$'}type":"unlimited"}"""))

        assertTrue(dto is FrequencyDto.FrequencyUnlimitedDto)
    }

    // embedded form variant

    @Test
    fun `embedded variant with webview layer is parsed`() {
        val form = manager.deserializeToInAppFormDto(
            json(
                """
                {"variants":[{
                    "${'$'}type":"embedded",
                    "placeSystemName":"main-screen-top",
                    "content":{"background":{"layers":[{
                        "${'$'}type":"webview",
                        "baseUrl":"https://blocks.local/base",
                        "contentUrl":"https://blocks.local/items.html",
                        "params":{"items":[{"inAppId":"inapp-1"}]}
                    }]}}
                }]}
                """.trimIndent()
            )
        )

        val variant = form?.variants?.single() as PayloadDto.EmbeddedDto
        assertEquals("embedded", variant.type)
        assertEquals("main-screen-top", variant.placeSystemName)
        assertEquals(1, variant.content?.background?.layers?.size)
        // Structured param values survive as JSON strings and are re-hydrated on the way to
        // the page — it must receive `items` as an array.
        assertEquals(
            """[{"inAppId":"inapp-1"}]""",
            variant.content?.background?.layers?.filterIsInstance<cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto.LayerDto.WebViewLayerDto>()
                ?.single()?.params?.get("items")
        )
    }

    @Test
    fun `embedded variant ignores content elements`() {
        val form = manager.deserializeToInAppFormDto(
            json(
                """
                {"variants":[{
                    "${'$'}type":"embedded",
                    "placeSystemName":"main-screen-top",
                    "content":{
                        "background":{"layers":[]},
                        "elements":[{"${'$'}type":"closeButton"}]
                    }
                }]}
                """.trimIndent()
            )
        )

        val variant = form?.variants?.single() as PayloadDto.EmbeddedDto
        assertEquals("main-screen-top", variant.placeSystemName)
    }

    @Test
    fun `form with unknown variant type is dropped whole`() {
        // An unknown variant ${'$'}type has no class to land in: the form fails to parse and the
        // in-app is silently dropped — exactly how an old SDK survives a new variant type
        // (the contract guards this with sdkVersion.min = 13).
        val form = manager.deserializeToInAppFormDto(
            json(
                """
                {"variants":[
                    {"${'$'}type":"hologram"},
                    {"${'$'}type":"embedded","placeSystemName":"main-screen-top",
                     "content":{"background":{"layers":[]}}}
                ]}
                """.trimIndent()
            )
        )

        assertNull(form)
    }

    @Test
    fun `explicit null restriction fields do not fail the inapps block`() {
        // A real backend may send "displayConditions": null explicitly. Gson's own JsonObject
        // adapter throws on JsonNull, and one broken in-app fails the whole inapps block —
        // found live on the emulator (13.08), guarded here. An unknown key (validityPeriod is
        // one now) is skipped by the parser, whatever it holds.
        val config = manager.deserializeToConfigDtoBlank(
            """
            {"inapps":[{"id":"a","isPriority":false,"delayTime":null,
              "validityPeriod":null,"displayConditions":null,
              "sdkVersion":{"min":13,"max":null},
              "targeting":{"${'$'}type":"true"},
              "form":{"variants":[]}}]}
            """.trimIndent()
        )

        assertEquals(1, config?.inApps?.size)
        assertNull(config?.inApps?.first()?.displayConditions)
    }

    @Test
    fun `garbage restriction fields read as no restriction`() {
        val config = manager.deserializeToConfigDtoBlank(
            """
            {"inapps":[{"id":"a","isPriority":false,
              "validityPeriod":"tomorrow","displayConditions":42,
              "targeting":{"${'$'}type":"true"},
              "form":{"variants":[]}}]}
            """.trimIndent()
        )

        assertEquals(1, config?.inApps?.size)
        assertNull(config?.inApps?.first()?.displayConditions)
    }
}
