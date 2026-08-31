package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural rules only: a place to bind to and at least one valid webview layer — the first
 * webview layer is used, extra layers are ignored. `params` are deliberately not validated —
 * they travel to the page as they came.
 */
class EmbeddedVariantValidatorTest {

    private val webViewLayerValidator: WebViewLayerValidator = mockk {
        every { isValid(any()) } returns true
    }
    private val validator = EmbeddedVariantValidator(webViewLayerValidator)

    private val valid = InAppStub.getEmbeddedDto()

    @Test
    fun `valid embedded variant passes`() {
        assertTrue(validator.isValid(valid))
    }

    @Test
    fun `null variant is invalid`() {
        assertFalse(validator.isValid(null))
    }

    @Test
    fun `wrong type is invalid`() {
        assertFalse(validator.isValid(valid.copy(type = "modal")))
    }

    @Test
    fun `missing place system name is invalid`() {
        assertFalse(validator.isValid(valid.copy(placeSystemName = null)))
        assertFalse(validator.isValid(valid.copy(placeSystemName = "")))
    }

    @Test
    fun `a place system name of spaces is not a name`() {
        assertFalse(validator.isValid(valid.copy(placeSystemName = "   ")))
    }

    @Test
    fun `zero layers are invalid`() {
        val variant = valid.copy(
            content = PayloadDto.EmbeddedDto.ContentDto(background = BackgroundDto(layers = emptyList()))
        )

        assertFalse(validator.isValid(variant))
    }

    @Test
    fun `extra layers are ignored — the first webview layer wins`() {
        val layer = valid.content!!.background!!.layers!!.single()
        val variant = valid.copy(
            content = PayloadDto.EmbeddedDto.ContentDto(background = BackgroundDto(layers = listOf(layer, layer)))
        )

        assertTrue(validator.isValid(variant))
    }

    @Test
    fun `webview layer after a non-webview one still validates`() {
        val webViewLayer = valid.content!!.background!!.layers!!.single()
        val variant = valid.copy(
            content = PayloadDto.EmbeddedDto.ContentDto(
                background = BackgroundDto(layers = listOf(InAppStub.getImageLayerDto(), webViewLayer))
            )
        )

        assertTrue(validator.isValid(variant))
    }

    @Test
    fun `non webview layer is invalid`() {
        val variant = valid.copy(
            content = PayloadDto.EmbeddedDto.ContentDto(
                background = BackgroundDto(layers = listOf(InAppStub.getImageLayerDto()))
            )
        )

        assertFalse(validator.isValid(variant))
    }

    @Test
    fun `invalid webview layer fails the variant`() {
        every { webViewLayerValidator.isValid(any()) } returns false

        assertFalse(validator.isValid(valid))
    }

    @Test
    fun `params are not validated`() {
        // Whatever is inside params — missing keys, junk values — the variant stays valid.
        val layer = valid.content!!.background!!.layers!!
            .single() as BackgroundDto.LayerDto.WebViewLayerDto
        val variant = valid.copy(
            content = PayloadDto.EmbeddedDto.ContentDto(
                background = BackgroundDto(layers = listOf(layer.copy(params = null)))
            )
        )

        assertTrue(validator.isValid(variant))
    }
}
