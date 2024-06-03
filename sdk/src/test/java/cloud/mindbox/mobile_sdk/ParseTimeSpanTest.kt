package cloud.mindbox.mobile_sdk

import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParseTimeSpanTest(
    private val str: String,
    private val value: Long?
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: string({0}) parse to {1}")
        fun data(): Iterable<Array<Any?>> = listOf(
            arrayOf("6", null),
            arrayOf("6:12", null),
            arrayOf("1.6:12", null),
            arrayOf("1.6:12.1", null),
            arrayOf("6:12:14:45", null),
            arrayOf("6:24:14:45", null),
            arrayOf("6:99:14:45", null),
            arrayOf("6:00:24:99", null),
            arrayOf("6:00:99:45", null),
            arrayOf("6:00:60:45", null),
            arrayOf("6:00:44:60", null),
            arrayOf("6:00:44:60", null),
            arrayOf("6:99:99:99", null),
            arrayOf("1:1:1:1:1", null),
            arrayOf("qwe", null),
            arrayOf("", null),
            arrayOf("999999999:0:0", null),
            arrayOf("0:0:0.12345678", null),
            arrayOf(".0:0:0.1234567", null),
            arrayOf("0:0:0.", null),
            arrayOf("0:000:0", null),
            arrayOf("00:000:00", null),
            arrayOf("000:00:00", null),
            arrayOf("00:00:000", null),
            arrayOf("+0:0:0", null),
            arrayOf("12345678901234567890.00:00:00.00", null),

            arrayOf("0:0:0.1234567", 123L),
            arrayOf("0:0:0.1", 100L),
            arrayOf("0:0:0.01", 10L),
            arrayOf("0:0:0.001", 1L),
            arrayOf("0:0:0.0001", 0L),
            arrayOf("01.01:01:01.10", 90061100L),
            arrayOf("1.1:1:1.1", 90061100L),
            arrayOf("1.1:1:1", 90061000L),
            arrayOf("99.23:59:59", 8639999000L),
            arrayOf("999.23:59:59", 86399999000L),
            arrayOf("6:12:14", 22334000L),
            arrayOf("6.12:14:45", 562485000L),
            arrayOf("1.00:00:00", 86400000L),
            arrayOf("0.00:00:00.0", 0L),
            arrayOf("00:00:00", 0L),
            arrayOf("0:0:0", 0L),
            arrayOf("-0:0:0", 0L),
            arrayOf("-0:0:0.001", -1L),
            arrayOf("-1.0:0:0", -86400000L),
            arrayOf("10675199.02:48:05.4775807", 922337203685477L),
            arrayOf("-10675199.02:48:05.4775808", -922337203685477L),
        )
    }

    @Test
    fun `parseTimeSpanToMillis is valid`() {
        if (value == null) {
            assertThrows(IllegalArgumentException::class.java) { str.parseTimeSpanToMillis() }
        } else {
            Assert.assertEquals(value, str.parseTimeSpanToMillis())
        }
    }
}
