package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Dialog
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class InAppPositionControllerTest {

    private val controller = InAppPositionController()

    @Test
    fun `finds dialog nested in child fragment manager`() {
        val nestedDialog = dialogFragment()
        val host = plainFragment(children = listOf(nestedDialog))
        val rootFm = fragmentManager(host)

        assertSame(nestedDialog, controller.findTopDialogFragment(rootFm))
    }

    @Test
    fun `returns the top-most dialog, not the first one`() {
        val first = dialogFragment()
        val last = dialogFragment()
        val rootFm = fragmentManager(first, last)

        assertSame(last, controller.findTopDialogFragment(rootFm))
    }

    @Test
    fun `ignores dialog that is not added`() {
        val added = dialogFragment(isAdded = true)
        val notAdded = dialogFragment(isAdded = false)
        val rootFm = fragmentManager(added, notAdded)

        assertSame(added, controller.findTopDialogFragment(rootFm))
    }

    @Test
    fun `ignores dialog without a window`() {
        val withWindow = dialogFragment(hasWindow = true)
        val withoutWindow = dialogFragment(hasWindow = false)
        val rootFm = fragmentManager(withWindow, withoutWindow)

        assertSame(withWindow, controller.findTopDialogFragment(rootFm))
    }

    @Test
    fun `returns null when there are no dialogs`() {
        val rootFm = fragmentManager(plainFragment(), plainFragment())

        assertNull(controller.findTopDialogFragment(rootFm))
    }

    @Test
    fun `returns null for an empty fragment manager`() {
        assertNull(controller.findTopDialogFragment(fragmentManager()))
    }

    private fun fragmentManager(vararg fragments: Fragment): FragmentManager = mockk {
        every { this@mockk.fragments } returns fragments.toList()
    }

    private fun plainFragment(
        children: List<Fragment> = emptyList(),
        isAdded: Boolean = true,
    ): Fragment = mockk {
        every { this@mockk.isAdded } returns isAdded
        every { childFragmentManager } returns fragmentManager(*children.toTypedArray())
    }

    private fun dialogFragment(
        isAdded: Boolean = true,
        hasWindow: Boolean = true,
    ): DialogFragment = mockk {
        every { this@mockk.isAdded } returns isAdded
        every { childFragmentManager } returns fragmentManager()
        every { dialog } returns mockk<Dialog> {
            every { window } returns if (hasWindow) mockk<Window>() else null
        }
    }
}
