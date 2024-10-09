package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class CloseButtonSnackbarSizeValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @OverrideMockKs
    private lateinit var closeButtonSnackbarSizeValidator: CloseButtonSnackbarSizeValidator

    @Test
    fun `should return true when size is valid`() {
        // Arrange
        val sizeDto = InAppStub.getCloseButtonElementDto().copy(size = InAppStub.getElementSizeDto().copy(kind = "someKind", height = 100.0, width = 50.0)).size

        // Act
        val isValid = closeButtonSnackbarSizeValidator.isValid(sizeDto)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `should return false when size is invalid`() {
        // Arrange
        val sizeDto = InAppStub.getCloseButtonElementDto().copy(size = InAppStub.getElementSizeDto().copy(kind = null, height = -10.0, width = 200.0)).size

        // Act
        val isValid = closeButtonSnackbarSizeValidator.isValid(sizeDto)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `should log when size is invalid`() {
        // Arrange
        mockkObject(MindboxLoggerImpl)
        val sizeDto = InAppStub.getCloseButtonElementDto().copy(size = InAppStub.getElementSizeDto().copy(kind = null, height = -10.0, width = 200.0)).size

        // Act
        assertFalse(closeButtonSnackbarSizeValidator.isValid(sizeDto))

        // Assert
        verify {
            MindboxLoggerImpl.i(any(),
                "Close button size is not valid. Expected kind != null and width/height in range [0, inf]. " +
                    "Actual params : kind =  ${sizeDto?.kind}, height = ${sizeDto?.height}, width = ${sizeDto?.width}"
            )
        }
    }
}
