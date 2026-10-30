package cloud.mindbox.mobile_sdk.models

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceKeyTest {

    @Test
    fun `a place key trims and lowercases the raw name`() {
        assertEquals("main-screen-top", PlaceKey.of("  Main-Screen-Top  ").value)
    }

    @Test
    fun `names differing in case and padding make one key`() {
        assertEquals(PlaceKey.of("Main-Screen-Top"), PlaceKey.of(" main-screen-top "))
    }

    @Test
    fun `a place key prints as its value`() {
        assertEquals("main-screen-top", PlaceKey.of("Main-Screen-Top").toString())
    }
}
