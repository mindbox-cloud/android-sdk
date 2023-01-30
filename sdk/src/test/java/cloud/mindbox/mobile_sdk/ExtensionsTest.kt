package cloud.mindbox.mobile_sdk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class ExtensionsTest {

    @Test
    fun `converting unix time to string`() {
        val time: Long = 1674810809326
        val expectedResult = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val actualResult = time.convertToStringDate()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `converting string to unix time`() {
        val time = "2023-01-27T14:13:29"
        val expectedResult: Long =
            LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(
                ZoneId.systemDefault()
            ).toEpochSecond() * 1000
        val actualResult = time.convertToLongDateMilliSeconds()
        assertEquals(expectedResult, actualResult)
    }


}