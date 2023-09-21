package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class CloseButtonSnackbarPositionValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @OverrideMockKs
    private lateinit var closeButtonSnackbarPositionValidator: CloseButtonSnackbarPositionValidator

    @Test
    fun `should return true when position is valid`() {
        // Arrange
        val positionDto = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = 0.1,
                kind = "non_null",
                left = 0.2,
                right = 0.3,
                top = 0.4
            )
        )

        // Act
        val isValid = closeButtonSnackbarPositionValidator.isValid(positionDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `should return false when position is invalid`() {
        // Arrange
        val positionDto = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = 0.1,
                kind = "non_null",
                left = 0.2,
                right = 1.5,
                top = 0.4
            )
        )

        // Act
        val isValid = closeButtonSnackbarPositionValidator.isValid(positionDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `should log when position is invalid`() {
        // Arrange
        mockkObject(MindboxLoggerImpl)
        val positionDto = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = 0.1,
                kind = null,
                left = 0.2,
                right = 0.3,
                top = 0.4
            )
        )

        // Act
        val result = closeButtonSnackbarPositionValidator.isValid(positionDto)

        // Assert
        assertFalse(result)

        verify(exactly = 1) {
            MindboxLoggerImpl.i(any(), "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                    "Actual params : kind =  ${positionDto.margin?.kind}, top = ${positionDto.margin?.top}, bottom = ${positionDto.margin?.bottom}, left = ${positionDto.margin?.left}, right = ${positionDto.margin?.right}")
        }
    }
}