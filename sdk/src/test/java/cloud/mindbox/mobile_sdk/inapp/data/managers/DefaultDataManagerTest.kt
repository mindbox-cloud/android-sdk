package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.DataManager
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.FrequencyDataFiller
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.ModalWindowDtoDataFiller
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.SnackBarDtoDataFiller
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class DefaultDataManagerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var snackbarDtoDataFiller: SnackBarDtoDataFiller

    @MockK
    private lateinit var modalWindowDtoDataFiller: ModalWindowDtoDataFiller

    @MockK
    private lateinit var frequencyDtoDataFiller: FrequencyDataFiller

    @OverrideMockKs
    private lateinit var defaultDataManager: DataManager

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

        val result = defaultDataManager.fillFormData(testData)

        assertEquals(result?.variants, listOf(mw, snackbar))
    }
}
