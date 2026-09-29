package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-place memory behind the AUTOMATIC loading strategy: a show writes the place down, an
 * empty answer strikes it out, and the record is one extensible value keyed by the normalized
 * place name.
 */
class EmbeddedBlockPlaceMemoryTest {

    private var stored: String = ""
    private val writes = mutableListOf<String>()

    private fun memory(now: () -> Timestamp = { Timestamp(0L) }): EmbeddedBlockPlaceMemory =
        EmbeddedBlockPlaceMemory(
            readRecordsJson = { stored },
            writeRecordsJson = { json ->
                stored = json
                writes.add(json)
            },
            now = now,
        )

    private val place = PlaceKey.of("Main-Screen-Top")

    @Test
    fun `a place is unknown until content was shown there`() {
        assertFalse(memory().hasShownContent(place))
    }

    @Test
    fun `shown content is remembered and survives through the stored json`() {
        memory().rememberShownContent(place)

        assertTrue(memory().hasShownContent(place))
    }

    @Test
    fun `the record is keyed by the normalized place name`() {
        memory().rememberShownContent(PlaceKey.of("  Main-Screen-Top "))

        assertTrue(memory().hasShownContent(PlaceKey.of("main-screen-top")))
    }

    @Test
    fun `the record carries when the place was written down`() {
        memory(now = { Timestamp(1_726_000_000_000L) }).rememberShownContent(place)

        assertTrue(stored.contains("rememberedAt"))
        assertTrue(stored.contains("2024-09-10T20:26:40Z"))
    }

    @Test
    fun `remembering is written once - a block shows its content on every return`() {
        val memory = memory()
        memory.rememberShownContent(place)
        memory.rememberShownContent(place)

        assertEquals(1, writes.size)
    }

    @Test
    fun `a forgotten place starts as if it were new`() {
        val memory = memory()
        memory.rememberShownContent(place)

        memory.forgetPlace(place)

        assertFalse(memory.hasShownContent(place))
    }

    @Test
    fun `forgetting an unknown place writes nothing`() {
        memory().forgetPlace(place)

        assertEquals(0, writes.size)
    }

    @Test
    fun `forgetting one place leaves the others alone`() {
        val memory = memory()
        memory.rememberShownContent(place)
        memory.rememberShownContent(PlaceKey.of("second-place"))

        memory.forgetPlace(place)

        assertFalse(memory.hasShownContent(place))
        assertTrue(memory.hasShownContent(PlaceKey.of("second-place")))
    }

    @Test
    fun `a corrupted store reads as empty and is overwritten by the next show`() {
        stored = "{not json"

        val memory = memory()
        assertFalse(memory.hasShownContent(place))

        memory.rememberShownContent(place)
        assertTrue(memory.hasShownContent(place))
    }

    @Test
    fun `a store write failure is survived`() {
        val failing = EmbeddedBlockPlaceMemory(
            readRecordsJson = { "" },
            writeRecordsJson = { throw IllegalStateException("disk full") },
            now = { Timestamp(0L) },
        )

        failing.rememberShownContent(place)

        assertFalse(failing.hasShownContent(place))
    }
}
