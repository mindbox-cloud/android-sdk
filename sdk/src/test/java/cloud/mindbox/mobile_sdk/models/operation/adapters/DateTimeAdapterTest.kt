package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DateTimeAdapterTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(DateTime::class.java, DateTimeAdapter())
        .create()

    private data class Holder(
        @SerializedName("date")
        val date: DateTime?
    )

    // region read

    @Test
    fun `read - ISO8601 with positive timezone offset`() {
        val holder = gson.fromJson("""{"date":"2023-06-15T10:30:00.000+03:00"}""", Holder::class.java)
        assertNotNull(holder.date)
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            .parse("2023-06-15T10:30:00.000+03:00")!!
        assertEquals(expected.time, holder.date!!.time)
    }

    @Test
    fun `read - ISO8601 UTC Z suffix`() {
        val holder = gson.fromJson("""{"date":"2023-01-01T00:00:00.000Z"}""", Holder::class.java)
        assertNotNull(holder.date)
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            .parse("2023-01-01T00:00:00.000+00:00")!!
        assertEquals(expected.time, holder.date!!.time)
    }

    @Test
    fun `read - ISO8601 without milliseconds`() {
        val holder = gson.fromJson("""{"date":"2023-06-15T10:30:00+00:00"}""", Holder::class.java)
        assertNotNull(holder.date)
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .parse("2023-06-15T10:30:00+00:00")!!
        assertEquals(expected.time, holder.date!!.time)
    }

    @Test
    fun `read - ISO8601 negative timezone offset`() {
        val holder = gson.fromJson("""{"date":"2023-06-15T10:30:00.000-05:00"}""", Holder::class.java)
        assertNotNull(holder.date)
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            .parse("2023-06-15T10:30:00.000-05:00")!!
        assertEquals(expected.time, holder.date!!.time)
    }

    @Test
    fun `read - null JSON value returns null DateTime`() {
        val holder = gson.fromJson("""{"date":null}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - missing field returns null DateTime`() {
        val holder = gson.fromJson("""{}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - invalid date string returns null without throwing`() {
        // LoggingExceptionHandler swallows the parse failure
        val holder = gson.fromJson("""{"date":"not-a-date"}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - empty string returns null without throwing`() {
        val holder = gson.fromJson("""{"date":""}""", Holder::class.java)
        assertNull(holder.date)
    }

    // endregion

    // region write

    @Test
    fun `write - null DateTime field omitted by default Gson serialization`() {
        // GSON skips null fields in POJOs by default (serializeNulls not set),
        // so the adapter's nullValue() path is not reached via reflection.
        val json = gson.toJson(Holder(null))
        assertEquals("{}", json)
    }

    @Test
    fun `write - null DateTime writes JSON null when adapter called directly`() {
        val sw = java.io.StringWriter()
        val writer = com.google.gson.stream.JsonWriter(sw)
        DateTimeAdapter().write(writer, null)
        writer.flush()
        assertEquals("null", sw.toString())
    }

    @Test
    fun `write - DateTime serialized as non-null string`() {
        val json = gson.toJson(Holder(DateTime(0L)))
        assertTrue("JSON should contain string date", json.contains(""""date":""""))
    }

    @Test
    fun `write - uses dd_MM_yyyy_HH_mm_ss_FFF pattern`() {
        // Documents the exact write format so migration doesn't accidentally change it.
        // NOTE: FFF in SimpleDateFormat is "Day of week in month", NOT milliseconds.
        // This is a known quirk of the current implementation.
        val knownTime = 1_700_000_000_000L
        val dateTime = DateTime(knownTime)
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.FFF", Locale.getDefault())
        val expectedDateString = formatter.format(dateTime)

        val json = gson.toJson(Holder(dateTime))
        assertTrue(
            "Serialized date should match 'dd.MM.yyyy HH:mm:ss.FFF' pattern",
            json.contains(""""$expectedDateString"""")
        )
    }

    @Test
    fun `write - two DateTimes with same timestamp produce same output`() {
        val time = 1_686_825_000_000L
        val json1 = gson.toJson(Holder(DateTime(time)))
        val json2 = gson.toJson(Holder(DateTime(time)))
        assertEquals(json1, json2)
    }

    // endregion

    // region timestamp preservation

    @Test
    fun `read - preserves epoch milliseconds from ISO8601 input`() {
        // The server sends ISO8601; we must preserve the exact timestamp.
        // This is the key regression test for the ISO8601Utils → alternative migration.
        val utcSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }
        val expectedMillis = utcSdf.parse("2023-06-15T07:30:00.000+00:00")!!.time

        val holder = gson.fromJson("""{"date":"2023-06-15T07:30:00.000+00:00"}""", Holder::class.java)

        assertNotNull(holder.date)
        assertEquals(expectedMillis, holder.date!!.time)
    }

    // endregion
}
