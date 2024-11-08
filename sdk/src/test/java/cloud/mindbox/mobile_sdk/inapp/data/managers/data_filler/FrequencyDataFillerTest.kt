package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyDataFillerTest {

    private val frequencyDataFiller = FrequencyDataFiller()

    @Test
    fun `fill data returns original object`() {
        val expectedFrequency = InAppStub.getFrequencyOnceDto().copy("random", "value")
        val actualFrequency = frequencyDataFiller.fillData(expectedFrequency)

        assertEquals(expectedFrequency, actualFrequency)
    }

    @Test
    fun `fill data returns filled object for null`() {
        val expectedResult = InAppStub.getFrequencyOnceDto().copy("once", "lifetime")
        val actualResult = frequencyDataFiller.fillData(null)

        assertEquals(expectedResult, actualResult)
    }
}
