package cloud.mindbox.mobile_sdk.inapp.data.dto

import android.graphics.Color
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto.CloseButtonElementDto
import io.mockk.every
import io.mockk.mockkStatic
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

internal class CloseButtonElementDtoTest {
    @Test
    fun `test updateWithDefaults`() {
        // Create an instance of CloseButtonElementDto
        val closeButtonElement = CloseButtonElementDto(
            color = null,
            lineWidth = null,
            position = null,
            size = null,
            type = null
        )

        // Mock Color.parseColor() to always return a valid color
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 123 // A sample color int

        val updatedElement = closeButtonElement.updateWithDefaults() as CloseButtonElementDto

        // Assert that the updated values are as expected
        // You'll need to adjust the expected values based on your logic
        assertTrue(updatedElement.color == "#000000")
        assertTrue(updatedElement.lineWidth == 1)
        // Check other properties as well
    }

    @Test
    fun `test validateValues`() {
        // Create a valid instance of SizeDto and PositionDto
        val validSize = CloseButtonElementDto.SizeDto(height = 30.0, kind = "dp", width = 30.0)
        val validPosition =
            CloseButtonElementDto.PositionDto(margin = CloseButtonElementDto.PositionDto.MarginDto.default())

        // Create an instance of CloseButtonElementDto with valid size and position
        val closeButtonElement = CloseButtonElementDto(
            color = "#FFFFFF",
            lineWidth = 2,
            position = validPosition,
            size = validSize,
            type = "someType"
        )

        assertTrue(closeButtonElement.validateValues())

        // Create an instance of CloseButtonElementDto with invalid size and position
        val invalidSize =
            CloseButtonElementDto.SizeDto(height = -1.0, kind = "invalidKind", width = 30.0)
        val invalidPosition =
            CloseButtonElementDto.PositionDto(margin = CloseButtonElementDto.PositionDto.MarginDto.default())

        val invalidCloseButtonElement = CloseButtonElementDto(
            color = "#FFFFFF",
            lineWidth = 2,
            position = invalidPosition,
            size = invalidSize,
            type = "someType"
        )

        assertFalse(invalidCloseButtonElement.validateValues())
    }
}