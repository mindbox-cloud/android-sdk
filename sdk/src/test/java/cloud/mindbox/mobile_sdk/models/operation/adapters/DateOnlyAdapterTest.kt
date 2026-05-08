package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DateOnlyAdapterTest {

    private val gson = GsonBuilder().registerTypeAdapter(DateOnly::class.java, DateOnlyAdapter()).create()

    private data class Holder(
        @SerializedName("date")
        val date: DateOnly?
    )

    // region read

    @Test
    fun `read - parses yyyy-MM-dd format`() {
        val holder = gson.fromJson("""{"date":"2023-06-15"}""", Holder::class.java)
        assertNotNull(holder.date)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expected = formatter.parse("2023-06-15")!!
        assertEquals(expected.time, holder.date!!.time)
    }

    @Test
    fun `read - null JSON value returns null`() {
        val holder = gson.fromJson("""{"date":null}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - missing field returns null`() {
        val holder = gson.fromJson("""{}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - invalid date string returns null without throwing`() {
        val holder = gson.fromJson("""{"date":"not-a-date"}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - empty string returns null without throwing`() {
        val holder = gson.fromJson("""{"date":""}""", Holder::class.java)
        assertNull(holder.date)
    }

    @Test
    fun `read - parses start of year`() {
        val holder = gson.fromJson("""{"date":"2023-01-01"}""", Holder::class.java)
        assertNotNull(holder.date)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        assertEquals(formatter.parse("2023-01-01")!!.time, holder.date!!.time)
    }

    @Test
    fun `read - parses end of year`() {
        val holder = gson.fromJson("""{"date":"2023-12-31"}""", Holder::class.java)
        assertNotNull(holder.date)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        assertEquals(formatter.parse("2023-12-31")!!.time, holder.date!!.time)
    }

    // endregion

    // region write

    @Test
    fun `write - null DateOnly field omitted by default Gson serialization`() {
        // GSON skips null fields in POJOs by default (serializeNulls not set).
        val json = gson.toJson(Holder(null))
        assertEquals("{}", json)
    }

    @Test
    fun `write - null DateOnly writes JSON null when adapter called directly`() {
        val sw = java.io.StringWriter()
        val writer = com.google.gson.stream.JsonWriter(sw)
        DateOnlyAdapter().write(writer, null)
        writer.flush()
        assertEquals("null", sw.toString())
    }

    @Test
    fun `write - DateOnly serialized as yyyy-MM-dd string`() {
        // Pin a known date to avoid locale/TZ ambiguity: use local midnight to avoid rollover.
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = formatter.parse("2023-06-15")!!

        val json = gson.toJson(Holder(DateOnly(date.time)))

        assertTrue(
            "Serialized DateOnly should match yyyy-MM-dd pattern", json.contains(""""2023-06-15"""")
        )
    }

    @Test
    fun `write - two DateOnly with same timestamp produce same output`() {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val time = formatter.parse("2023-06-15")!!.time
        assertEquals(
            gson.toJson(Holder(DateOnly(time))), gson.toJson(Holder(DateOnly(time)))
        )
    }

    // endregion

    // region round-trip

    @Test
    fun `round trip - date string preserved after read then write`() {
        val originalJson = """{"date":"2023-06-15"}"""
        val holder = gson.fromJson(originalJson, Holder::class.java)
        assertNotNull(holder.date)

        val serialized = gson.toJson(holder)
        assertTrue(
            "Round-tripped JSON should still contain the date string", serialized.contains(""""2023-06-15"""")
        )
    }

    @Test
    fun `read - preserves date independent of UTC offset`() {
        // Documents that DateOnly uses local formatter without timezone handling.
        // This test pins the expected value using the same local formatter the adapter uses.
        val localFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val knownDate = "2023-06-15"
        val holder = gson.fromJson("""{"date":"$knownDate"}""", Holder::class.java)
        assertNotNull(holder.date)
        assertEquals(knownDate, localFormatter.format(holder.date!!))
    }

    // endregion
}
