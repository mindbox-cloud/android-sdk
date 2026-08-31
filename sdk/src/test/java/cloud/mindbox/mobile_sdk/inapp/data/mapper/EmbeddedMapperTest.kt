package cloud.mindbox.mobile_sdk.inapp.data.mapper

import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.DisplayConditionsDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The new fields cross the DTO → domain border here. An unknown restriction resolves in
 * favour of showing (`null` = no restriction) instead of dropping the in-app.
 */
class EmbeddedMapperTest {

    private val mapper = InAppMapper()

    private fun config(inAppDto: cloud.mindbox.mobile_sdk.models.operation.response.InAppDto) =
        InAppConfigResponse(
            inApps = listOf(inAppDto),
            monitoring = null,
            settings = null,
            abtests = null,
        )

    private val baseDto = InAppStub.getInAppDto().copy(
        id = "in-app-id",
        targeting = cloud.mindbox.mobile_sdk.models.TreeTargetingDto.TrueNodeDto("true"),
        frequency = FrequencyDto.FrequencyOnceDto(type = "once", kind = "lifetime"),
        // The stub modal carries elements the mapper cannot map without the validator step —
        // the embedded variant keeps these tests about the new fields only.
        form = FormDto(variants = listOf(InAppStub.getEmbeddedDto())),
    )

    @Test
    fun `embedded variant maps to the fourth in-app type`() {
        val dto = baseDto.copy(
            form = FormDto(variants = listOf(InAppStub.getEmbeddedDto()))
        )

        val inApp = mapper.mapToInAppConfig(config(dto)).inApps.single()
        val variant = inApp.form.variants.single() as InAppType.Embedded

        assertEquals("in-app-id", variant.inAppId)
        assertEquals("main-screen-top", variant.placeSystemName)
        assertEquals(1, variant.layers.size)
    }

    @Test
    fun `extra non-webview layers are dropped on mapping`() {
        // An unexpected image layer next to the webview one (even with a broken action) must
        // not fail the mapping: the block only ever uses webview layers.
        val embedded = InAppStub.getEmbeddedDto()
        val withExtraLayer = embedded.copy(
            content = embedded.content!!.copy(
                background = embedded.content!!.background!!.copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto(),
                        embedded.content!!.background!!.layers!!.single(),
                    )
                )
            )
        )
        val dto = baseDto.copy(form = FormDto(variants = listOf(withExtraLayer)))

        val inApp = mapper.mapToInAppConfig(config(dto)).inApps.single()
        val variant = inApp.form.variants.single() as InAppType.Embedded

        assertEquals(1, variant.layers.size)
        assertTrue(variant.layers.single() is Layer.WebViewLayer)
    }

    @Test
    fun `place system name is mapped as it is`() {
        val dto = baseDto.copy(
            form = FormDto(variants = listOf(InAppStub.getEmbeddedDto().copy(placeSystemName = "  main-screen-top  ")))
        )

        val variant = mapper.mapToInAppConfig(config(dto)).inApps.single()
            .form.variants.single() as InAppType.Embedded

        assertEquals("  main-screen-top  ", variant.placeSystemName)
    }

    @Test
    fun `display conditions directCall maps to the enum`() {
        val dto = baseDto.copy(displayConditions = DisplayConditionsDto.DirectCallDto(type = "directCall"))

        assertEquals(
            DisplayConditions.DIRECT_CALL,
            mapper.mapToInAppConfig(config(dto)).inApps.single().displayConditions
        )
    }

    @Test
    fun `absent new fields keep the previous domain shape`() {
        val inApp = mapper.mapToInAppConfig(config(baseDto)).inApps.single()

        assertNull(inApp.displayConditions)
    }

    @Test
    fun `frequency unlimited maps to the unlimited delay`() {
        val dto = baseDto.copy(frequency = FrequencyDto.FrequencyUnlimitedDto(type = "unlimited"))

        assertEquals(
            Frequency(Frequency.Delay.Unlimited),
            mapper.mapToInAppConfig(config(dto)).inApps.single().frequency
        )
    }
}
