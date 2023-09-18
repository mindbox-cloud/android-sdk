package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class DefaultDataManagerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var snackbarDtoDataFiller: SnackBarDtoDataFiller

    @MockK
    private lateinit var modalWindowDtoDataFiller: ModalWindowDtoDataFiller

    @OverrideMockKs
    private lateinit var defaultDataManager: DefaultDataManager

    @Test
    fun `test fill data modal window and snackbar`() {
        val mw = InAppStub.getModalWindowDto()

        val snackbar = InAppStub.getSnackbarDto()

        val testData = InAppStub.getInAppDto().form?.copy(variants = listOf(mw, snackbar, null))


        every {
            modalWindowDtoDataFiller.fillData(any())
        } returns mw

        every {
            snackbarDtoDataFiller.fillData(any())
        } returns snackbar

        val result = defaultDataManager.fillData(testData)

        assertEquals(result?.variants, listOf(mw, snackbar))
    }
}