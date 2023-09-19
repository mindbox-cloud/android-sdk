package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PayloadDtoStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class SnackbarValidatorTest {

    @get:Rule
    val mockRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var snackbarValidator: SnackbarValidator

    @MockK
    private lateinit var imageLayerValidator: ImageLayerValidator

    @MockK
    private lateinit var elementValidator: ElementValidator

    @Test
    fun `validate snackbar invalid type`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = "invalidType",
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto().copy(
                            action = null,
                            source = null,
                            type = null
                        )
                    )
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar layers are empty`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = emptyList()
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar layers are null`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = null
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar has invalid layer`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto().copy(
                            action = null,
                            source = null,
                            type = null
                        )
                    )
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns false
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar has invalid element`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto().copy(
                            action = null,
                            source = null,
                            type = null
                        )
                    )
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns false
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar has invalid position margin`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto().copy(
                            action = null,
                            source = null,
                            type = null
                        )
                    )
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = -1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertFalse(rez)
    }

    @Test
    fun `validate snackbar success`() {
        val testItem = InAppStub.getSnackbarDto().copy(
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(
                        InAppStub.getImageLayerDto().copy(
                            action = null,
                            source = null,
                            type = null
                        )
                    )
                ), elements = listOf(
                    InAppStub.getCloseButtonElementDto().copy(
                        color = null,
                        lineWidth = null,
                        position = null,
                        size = null,
                        type = null
                    )
                ), position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = PayloadDtoStub.getSnackbarGravityDto().copy(
                        horizontal = null,
                        vertical = null
                    ),
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 1.0,
                        kind = "dp",
                        left = 1.0,
                        right = 1.0,
                        top = 1.0
                    )
                )
            )
        )

        every {
            imageLayerValidator.isValid(any())
        } returns true
        every {
            elementValidator.isValid(any())
        } returns true
        val rez = snackbarValidator.isValid(testItem)
        assertTrue(rez)
    }


}