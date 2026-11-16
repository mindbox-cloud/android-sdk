package cloud.mindbox.mobile_sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun isAssigned_isFalse_beforeAnyAssignment() {
        val delegate = SingleInitDelegate<String>()

        assertFalse(delegate.isAssigned)
        assertTrue(delegate.value.isEmpty())
    }

    @Test
    fun isAssigned_isTrue_afterEmptyListAssigned() {
        val delegate = SingleInitDelegate<String>()
        var testedValue: List<String> by delegate
        testedValue = emptyList()

        assertTrue(delegate.isAssigned)
        assertTrue(testedValue.isEmpty())
    }

    @Test
    fun value_mirrorsTheAssignedList() {
        val delegate = SingleInitDelegate<String>()
        var testedValue: List<String> by delegate
        testedValue = listOf("first")

        assertTrue(delegate.isAssigned)
        assertEquals(testedValue, delegate.value)
    }

    @Test
    fun setValue_isNotWorking_whenNewValueIsEmpty() {
        var testedValue: List<Any> by SingleInitDelegate()
        testedValue = listOf("first", "second")
        testedValue = emptyList()

        assertEquals(listOf("first", "second"), testedValue)
    }
}
