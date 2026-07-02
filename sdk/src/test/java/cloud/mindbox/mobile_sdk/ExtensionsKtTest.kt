package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.*
import org.junit.Test

class ExtensionsKtTest {

    @Suppress("ktlint:standard:enum-entry-name-case")
    enum class TestEnum {
        FIRST,
        S_E_C_O_N_D,
        Third,
        fourth,
        UNKNOWN
    }

    @Test
    fun `test enumValue`() {
        assertEquals(TestEnum.UNKNOWN, "UNKNOWn".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.UNKNOWN, "".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.UNKNOWN, (null as String?).enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.UNKNOWN, (null as String?).enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.UNKNOWN, "sdfsdfsfd".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.fourth, "FOURTH".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.Third, "Third".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.S_E_C_O_N_D, "SECOND".enumValue(TestEnum.UNKNOWN))
        assertEquals(TestEnum.FIRST, "f_i_r_s_t".enumValue(TestEnum.UNKNOWN))

        assertThrows(IllegalArgumentException::class.java) {
            "sixth".enumValue<TestEnum>()
        }
    }

    @Test
    fun `test equalsAny`() {
        assertTrue("".equalsAny("", " ", "sdhjfgsdhjf"))
        assertTrue("sdhjfgsdhjf".equalsAny("", " ", "sdhjfgsdhjf"))
        assertTrue(" ".equalsAny("", " ", "sdhjfgsdhjf"))
        assertTrue("sdhjfgsdhjf".equalsAny("sdhjfgsdhjf"))
        assertTrue("sdhjfgsdhjf".equalsAny("sdhjfgsdhjf", "sdhjfgsdhjf", "sdhjfgsdhjf"))

        assertFalse(" Test".equalsAny("TEST", "test", " Test "))
        assertFalse("sdhjfgsdhjf".equalsAny("null"))
        assertFalse((null as String?).equalsAny())
        assertFalse((null as String?).equalsAny(""))
        assertFalse((null as String?).equalsAny("null"))
    }

    @Test
    fun `gatedTags returns tags when present and feature enabled`() {
        val tags = mapOf("templateType" to "Popup")
        val inApp = InAppStub.getInApp().copy(tags = tags)
        assertEquals(tags, inApp.gatedTags(isTagsFeatureEnabled = true))
    }

    @Test
    fun `gatedTags returns null when feature disabled`() {
        val inApp = InAppStub.getInApp().copy(tags = mapOf("templateType" to "Popup"))
        assertNull(inApp.gatedTags(isTagsFeatureEnabled = false))
    }

    @Test
    fun `gatedTags returns null when tags empty`() {
        val inApp = InAppStub.getInApp().copy(tags = emptyMap())
        assertNull(inApp.gatedTags(isTagsFeatureEnabled = true))
    }

    @Test
    fun `gatedTags returns null when tags null`() {
        val inApp = InAppStub.getInApp().copy(tags = null)
        assertNull(inApp.gatedTags(isTagsFeatureEnabled = true))
    }
}
