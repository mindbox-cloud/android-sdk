package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PayloadDtoStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

internal class SnackBarDtoDataFillerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var elementDtoDataFiller: SnackbarElementDtoDataFiller

    @OverrideMockKs
    private lateinit var snackBarDtoDataFiller: SnackBarDtoDataFiller

    @Test
    fun `fillData with null item should return null`() {
        // Arrange
        val item: PayloadDto.SnackbarDto? = null

        // Act
        val result = snackBarDtoDataFiller.fillData(item)

        // Assert
        assert(result == null)
    }

    @Test
    fun `fillData with valid item should return modified item`() {
        // Arrange
        every {
            elementDtoDataFiller.fillData(any())
        } answers
                {
                    callOriginal()
                }
        val item = InAppStub.getSnackbarDto().copy(
            content = InAppStub.getSnackbarContentDto().copy(
                background = InAppStub.getBackgroundDto(),
                elements = listOf(),
                position = PayloadDtoStub.getSnackbarPositionDto().copy(
                    gravity = null,
                    margin = PayloadDtoStub.getSnackbarMarginDto().copy(
                        bottom = 10.0, kind = "dp", left = 10.0, right = 10.0, top = 10.0

                    )
                )

            ),
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME
        )

        // Act
        val result = snackBarDtoDataFiller.fillData(item)

        assert(result?.content?.position?.gravity != null)

        // Assert
        verify(exactly = 1) { elementDtoDataFiller.fillData(item.content?.elements) }
    }

}
