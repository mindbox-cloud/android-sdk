package cloud.mindbox.mobile_sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleInitDelegateTest {

    @Test
    fun initialValue_isEmtpyList_whenGetValue() {
        val testedValue: List<String> by SingleInitDelegate()
        assertTrue(testedValue.isEmpty())
    }

    @Test
    fun setValue_isWorking_whenCurrentValueIsEmpty() {
        var testedValue: List<String> by SingleInitDelegate()
        testedValue = listOf("first", "second")

        assertEquals(listOf("first", "second"), testedValue)
    }

    @Test
    fun setValue_isNotWorking_whenCurrentValueIsNotEmpty() {
        var testedValue: List<String> by SingleInitDelegate()
        testedValue = listOf("first", "second")
        testedValue = listOf("third", "fourth")

        assertEquals(listOf("first", "second"), testedValue)
    }

    @Test
    fun assigned_isNull_beforeAnyAssignment() {
        val delegate = SingleInitDelegate<String>()

        assertNull(delegate.assigned)
    }

    @Test
    fun assigned_isEmptyList_afterEmptyListAssigned() {
        val delegate = SingleInitDelegate<String>()
        var testedValue: List<String> by delegate
        testedValue = emptyList()

        assertEquals(emptyList<String>(), delegate.assigned)
        assertTrue(testedValue.isEmpty())
    }

    @Test
    fun assigned_mirrorsTheFirstNonEmptyList() {
        val delegate = SingleInitDelegate<String>()
        var testedValue: List<String> by delegate
        testedValue = emptyList()
        testedValue = listOf("first")
        testedValue = listOf("second")

        assertEquals(listOf("first"), delegate.assigned)
        assertEquals(listOf("first"), testedValue)
    }

    @Test
    fun setValue_isNotWorking_whenNewValueIsEmpty() {
        var testedValue: List<Any> by SingleInitDelegate()
        testedValue = listOf("first", "second")
        testedValue = emptyList()

        assertEquals(listOf("first", "second"), testedValue)
    }
}
