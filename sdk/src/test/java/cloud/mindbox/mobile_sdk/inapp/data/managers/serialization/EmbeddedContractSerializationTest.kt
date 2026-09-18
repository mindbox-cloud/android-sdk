package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.models.operation.response.DisplayConditionsDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import com.google.gson.JsonParser
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
        val dto = manager.deserializeToDisplayConditionsDto(json("""{"${'$'}type":"directCall"}"""), "inapp-id")

        assertTrue(dto is DisplayConditionsDto.DirectCallDto)
    }

    @Test
    fun `displayConditions null reads as show by trigger`() {
        assertNull(manager.deserializeToDisplayConditionsDto(null, "inapp-id"))
    }

    @Test
    fun `displayConditions with unknown type reads as show by trigger`() {
        assertNull(manager.deserializeToDisplayConditionsDto(json("""{"${'$'}type":"pushOnly"}"""), "inapp-id"))
    }

    // frequency unlimited

    @Test
    fun `frequency unlimited is parsed`() {
        val dto = manager.deserializeToFrequencyDto(json("""{"${'$'}type":"unlimited"}"""), "inapp-id")

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
            ),
            "inapp-id"
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
            ),
            "inapp-id"
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
            ),
            "inapp-id"
        )

        assertNull(form)
    }

    @Test
    fun `unknown variant type is a warning line, not an error with a stack trace`() {
        mockkObject(MindboxLoggerImpl)
        try {
            manager.deserializeToInAppFormDto(
                json(
                    """
                    {"variants":[
                        {"${'$'}type":"hologram"},
                        {"${'$'}type":"embedded","placeSystemName":"main-screen-top",
                         "content":{"background":{"layers":[]}}}
                    ]}
                    """.trimIndent()
                ),
                "inapp-id"
            )

            verify(exactly = 1) {
                MindboxLoggerImpl.w(
                    any(),
                    match { message ->
                        message.startsWith("In-app inapp-id: unknown ${'$'}type 'hologram', skipping it")
                    }
                )
            }
            verify(exactly = 0) { MindboxLoggerImpl.e(any(), any(), any()) }
            verify(exactly = 0) { MindboxLoggerImpl.e(any(), any()) }
        } finally {
            unmockkObject(MindboxLoggerImpl)
        }
    }

    @Test
    fun `unknown layer type is a warning line and only that layer is skipped`() {
        mockkObject(MindboxLoggerImpl)
        try {
            val form = manager.deserializeToInAppFormDto(
                json(
                    """
                    {"variants":[
                        {"${'$'}type":"embedded","placeSystemName":"main-screen-top",
                         "content":{"background":{"layers":[
                            {"${'$'}type":"video"},
                            {"${'$'}type":"webview","baseUrl":"https://cdn.example/",
                             "contentUrl":"https://cdn.example/stories.html"}
                         ]}}}
                    ]}
                    """.trimIndent()
                ),
                "inapp-id"
            )

            val variant = form?.variants?.single() as PayloadDto.EmbeddedDto
            assertEquals(1, variant.content?.background?.layers?.size)
            verify(exactly = 1) {
                MindboxLoggerImpl.w(
                    any(),
                    match { message -> message.startsWith("In-app inapp-id: unknown ${'$'}type 'video', skipping it") }
                )
            }
            verify(exactly = 0) { MindboxLoggerImpl.e(any(), any(), any()) }
        } finally {
            unmockkObject(MindboxLoggerImpl)
        }
    }

    @Test
    fun `a form broken for another reason still logs an error with the cause`() {
        mockkObject(MindboxLoggerImpl)
        try {
            val form = manager.deserializeToInAppFormDto(
                json("""{"variants":"not-an-array"}"""),
                "inapp-id"
            )

            assertNull(form)
            verify(exactly = 1) { MindboxLoggerImpl.e(any(), match { it.startsWith("Failed to parse JsonObject for in-app inapp-id") }, any()) }
            verify(exactly = 0) { MindboxLoggerImpl.w(any(), any()) }
        } finally {
            unmockkObject(MindboxLoggerImpl)
        }
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
