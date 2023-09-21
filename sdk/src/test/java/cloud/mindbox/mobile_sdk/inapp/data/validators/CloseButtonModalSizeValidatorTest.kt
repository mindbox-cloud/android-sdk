package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class CloseButtonModalSizeValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @OverrideMockKs
    private lateinit var closeButtonValidator: CloseButtonModalSizeValidator

    @Test
    fun `valid CloseButtonElementDto SizeDto`() {
        val sizeDto = mockk<ElementDto.CloseButtonElementDto.SizeDto>()
        every { sizeDto.kind } returns "some_kind"
        every { sizeDto.height } returns 10.0
        every { sizeDto.width } returns 20.0

        assertTrue(closeButtonValidator.isValid(sizeDto))
    }

    @Test
    fun `invalid CloseButtonElementDto SizeDto`() {
        val sizeDto = mockk<ElementDto.CloseButtonElementDto.SizeDto>()
        every { sizeDto.kind } returns null
        every { sizeDto.height } returns -5.0  // Invalid height
        every { sizeDto.width } returns 10.0

        assertFalse(closeButtonValidator.isValid(sizeDto))
    }

    @Test
    fun `logs on invalid CloseButtonElementDto SizeDto`() {
        mockkObject(MindboxLoggerImpl)
        val sizeDto = InAppStub.getElementSizeDto().copy(height = -5.0, kind = null, width = 10.0)
        assertFalse(closeButtonValidator.isValid(sizeDto))
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button size is not valid. Expected kind != null and width/height in range [0, inf]. " +
                        "Actual params : kind =  ${sizeDto.kind}, height = ${sizeDto.height}, width = ${sizeDto.width}"
            )
        }
    }
}