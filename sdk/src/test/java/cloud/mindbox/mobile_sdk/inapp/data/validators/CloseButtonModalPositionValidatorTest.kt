package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

internal class CloseButtonModalPositionValidatorTest {

    @get:Rule
    val rule = MockKRule(this)

    @OverrideMockKs
    private lateinit var closeButtonModalPositionValidator: CloseButtonModalPositionValidator

    @Before
    fun onTestStart() {
        mockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `validate success`() {
        val validBottom = Random.nextDouble(0.0, 1.0)
        val validTop = Random.nextDouble(0.0, 1.0)
        val validLeft = Random.nextDouble(0.0, 1.0)
        val validRight = Random.nextDouble(0.0, 1.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = validBottom,
                kind = "not_null",
                left = validLeft,
                right = validRight,
                top = validTop
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertTrue(result)
        verify(exactly = 0) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `invalid top value`() {
        val invalidTopValue = Random.nextDouble(1.0, Double.MAX_VALUE)
        val validBottom = Random.nextDouble(0.0, 1.0)
        val validLeft = Random.nextDouble(0.0, 1.0)
        val validRight = Random.nextDouble(0.0, 1.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = validBottom,
                kind = "not_null",
                left = validLeft,
                right = validRight,
                top = invalidTopValue
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `invalid bottom value`() {
        val validTop = Random.nextDouble(0.0, 1.0)
        val invalidBottomValue = Random.nextDouble(1.0, Double.MAX_VALUE)
        val validLeft = Random.nextDouble(0.0, 1.0)
        val validRight = Random.nextDouble(0.0, 1.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = invalidBottomValue,
                kind = "not_null",
                left = validLeft,
                right = validRight,
                top = validTop
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `invalid left value`() {
        val validTop = Random.nextDouble(0.0, 1.0)
        val validBottom = Random.nextDouble(0.0, 1.0)
        val invalidLeft = Random.nextDouble(Double.NEGATIVE_INFINITY, 0.0)
        val validRight = Random.nextDouble(0.0, 1.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = validBottom,
                kind = "not_null",
                left = invalidLeft,
                right = validRight,
                top = validTop
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `invalid right value`() {
        val validTop = Random.nextDouble(0.0, 1.0)
        val validBottom = Random.nextDouble(0.0, 1.0)
        val validLeft = Random.nextDouble(0.0, 1.0)
        val invalidRight = Random.nextDouble(Double.NEGATIVE_INFINITY, 0.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = validBottom,
                kind = "not_null",
                left = validLeft,
                right = invalidRight,
                top = validTop
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `kind is null`() {
        val validBottom = Random.nextDouble(0.0, 1.0)
        val validTop = Random.nextDouble(0.0, 1.0)
        val validLeft = Random.nextDouble(0.0, 1.0)
        val validRight = Random.nextDouble(0.0, 1.0)
        val testItem = InAppStub.getElementPositionDto().copy(
            margin = InAppStub.getElementMarginDto().copy(
                bottom = validBottom,
                kind = null,
                left = validLeft,
                right = validRight,
                top = validTop
            )
        )
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `margin is null`() {
        val testItem = InAppStub.getElementPositionDto().copy(margin = null)
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem.margin?.kind}, top = ${testItem.margin?.top}, bottom = ${testItem.margin?.bottom}, left = ${testItem.margin?.left}, right = ${testItem.margin?.right}"
            )
        }
    }

    @Test
    fun `item is null`() {
        val testItem: ElementDto.CloseButtonElementDto.PositionDto? = null
        val result = closeButtonModalPositionValidator.isValid(testItem)
        assertFalse(result)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Close button position margin is not valid. Expected kind != null and top/left/right/bottom in range [0, 1.0]. " +
                        "Actual params : kind =  ${testItem?.margin?.kind}, top = ${testItem?.margin?.top}, bottom = ${testItem?.margin?.bottom}, left = ${testItem?.margin?.left}, right = ${testItem?.margin?.right}"
            )
        }
    }

}