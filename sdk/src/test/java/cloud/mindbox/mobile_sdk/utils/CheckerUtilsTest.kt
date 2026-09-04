package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.models.Timestamp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckerUtilsTest {
    private lateinit var checker1: Checker
    private lateinit var checker2: Checker

    @Before
    fun setup() {
        checker1 = mockk()
        checker2 = mockk()
    }

    @Test
    fun `returns true when all checkers return true`() {
        every { checker1.check(any()) } returns true
        every { checker2.check(any()) } returns true

        val result = allAllow(emptyList(), checker1, checker2)

        assertTrue(result)
    }

    @Test
    fun `returns false when first checker returns false`() {
        every { checker1.check(any()) } returns false
        every { checker2.check(any()) } returns true

        val result = allAllow(emptyList(), checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns false when second checker returns false`() {
        every { checker1.check(any()) } returns true
        every { checker2.check(any()) } returns false

        val result = allAllow(emptyList(), checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns false when all checkers return false`() {
        every { checker1.check(any()) } returns false
        every { checker2.check(any()) } returns false

        val result = allAllow(emptyList(), checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns true when no checkers are provided`() {
        val result = allAllow(emptyList())

        assertTrue(result)
    }

    @Test
    fun `returns true when any checker throws exception`() {
        every { checker1.check(any()) } throws RuntimeException("Test exception")
        every { checker2.check(any()) } returns true

        val result = allAllow(emptyList(), checker1, checker2)

        assertTrue(result)
    }

    @Test
    fun `hands the same reservations to every checker`() {
        val held = listOf(ShowReservation("place|main", "inapp1", Timestamp(1L)))
        every { checker1.check(held) } returns true
        every { checker2.check(held) } returns true

        assertTrue(allAllow(held, checker1, checker2))

        verify(exactly = 1) { checker1.check(held) }
        verify(exactly = 1) { checker2.check(held) }
    }
}
