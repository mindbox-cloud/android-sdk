package cloud.mindbox.mobile_sdk

import org.junit.Assert.*
import org.junit.Test

class ExtensionsKtTest {

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
}