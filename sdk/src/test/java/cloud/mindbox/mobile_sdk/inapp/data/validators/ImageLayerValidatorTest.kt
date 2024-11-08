package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class ImageLayerValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var sourceValidator: ImageLayerValidator.SourceValidator

    @MockK
    private lateinit var actionValidator: ImageLayerValidator.ActionValidator

    @OverrideMockKs
    private lateinit var imageLayerValidator: ImageLayerValidator

    @Test
    fun `isValid should return true for valid ImageLayerDto`() {
        every { actionValidator.isValid(any()) } returns true

        every { sourceValidator.isValid(any()) } returns true
        // Arrange
        val imageLayerDto = BackgroundDto.LayerDto.ImageLayerDto(
            type = BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME,
            action = mockk(relaxed = true),
            source = mockk(relaxed = true)
        )

        // Act
        val isValid = imageLayerValidator.isValid(imageLayerDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `isValid should return false for invalid ImageLayerDto`() {
        every { actionValidator.isValid(any()) } returns true

        every { sourceValidator.isValid(any()) } returns true
        // Arrange
        val imageLayerDto = BackgroundDto.LayerDto.ImageLayerDto(
            type = "InvalidType",
            action = mockk(relaxed = true),
            source = mockk(relaxed = true)
        )

        // Act
        val isValid = imageLayerValidator.isValid(imageLayerDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `SourceValidator isValid should return true for valid UrlSourceDto`() {
        // Arrange
        val urlSourceDto = BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto(
            type = BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME,
            value = "https://example.com"
        )

        // Act
        val sourceValidator = ImageLayerValidator.SourceValidator()
        val isValid = sourceValidator.isValid(urlSourceDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `SourceValidator isValid should return false for invalid UrlSourceDto`() {
        // Arrange
        val urlSourceDto = BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto(
            type = "InvalidType",
            value = null
        )

        // Act
        val sourceValidator = ImageLayerValidator.SourceValidator()
        val isValid = sourceValidator.isValid(urlSourceDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `ActionValidator isValid should return true for valid RedirectUrlActionDto`() {
        // Arrange
        val redirectUrlActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto(
                type = BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME,
                value = "https://example.com",
                intentPayload = "payload"
            )

        // Act
        val actionValidator = ImageLayerValidator.ActionValidator()
        val isValid = actionValidator.isValid(redirectUrlActionDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `ActionValidator isValid should return false for invalid RedirectUrlActionDto`() {
        // Arrange
        val redirectUrlActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto(
                type = "InvalidType",
                value = null,
                intentPayload = null
            )

        // Act
        val actionValidator = ImageLayerValidator.ActionValidator()
        val isValid = actionValidator.isValid(redirectUrlActionDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `ActionValidator isValid should return true for valid PushPermissionActionDto`() {
        // Arrange
        val pushPermissionActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto(
                type = BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto.PUSH_PERMISSION_TYPE_JSON_NAME,
                intentPayload = "payload"
            )

        // Act
        val actionValidator = ImageLayerValidator.ActionValidator()
        val isValid = actionValidator.isValid(pushPermissionActionDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `ActionValidator isValid should return false for invalid PushPermissionActionDto`() {
        // Arrange
        val pushPermissionActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto(
                type = BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto.PUSH_PERMISSION_TYPE_JSON_NAME,
                intentPayload = null
            )

        // Act
        val actionValidator = ImageLayerValidator.ActionValidator()
        val isValid = actionValidator.isValid(pushPermissionActionDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `ActionValidator isValid should return false for invalid Action Type in PushPermissionActionDto `() {
        // Arrange
        val pushPermissionActionDto =
            BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto(
                type = "invalidType",
                intentPayload = "test_payload"
            )

        // Act
        val actionValidator = ImageLayerValidator.ActionValidator()
        val isValid = actionValidator.isValid(pushPermissionActionDto)

        // Assert
        assertFalse(isValid)
    }
}
