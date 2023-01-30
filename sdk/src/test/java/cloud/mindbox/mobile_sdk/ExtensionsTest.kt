package cloud.mindbox.mobile_sdk

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExtensionsTest {

    @Test
    fun `converting unix time to string`() {
        val time: Long = 1674810809326
        val actualResult = time.convertToStringDate()
        val expectedResult = "2023-01-27T14:13:29"
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `converting string to unix time`() {
        val expectedResult: Long = 1674810809326 / 1000
        val time = "2023-01-27T14:13:29"
        val actualResult = time.convertToLongDateMilliSeconds() / 1000
        assertEquals(expectedResult, actualResult)
    }


}