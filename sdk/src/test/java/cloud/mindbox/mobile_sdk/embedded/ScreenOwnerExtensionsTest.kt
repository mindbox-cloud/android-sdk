package cloud.mindbox.mobile_sdk.embedded

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Who a block belongs to: the fragment when there is one, the owner a wrapper names, never an
 * Activity or an owner that is itself going away.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenOwnerExtensionsTest {

    class HostFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View = FrameLayout(requireContext()).apply { addView(View(requireContext())) }

        val innerView: View
            get() = (requireView() as FrameLayout).getChildAt(0)
    }

    private class Screen(state: Lifecycle.State = Lifecycle.State.RESUMED) : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.CREATED
            currentState = state
        }

        override val lifecycle: Lifecycle
            get() = registry
    }

    private val activity: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Test
    fun `a view inside a fragment belongs to the fragment`() {
        val fragment = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

        assertSame(fragment, fragment.innerView.findScreenOwner())
        assertSame(fragment, fragment.innerView.findScreenOwner(destroyedOwner = fragment.viewLifecycleOwner))
    }

    @Test
    fun `a wrapper naming the fragment's view lifecycle means the fragment`() {
        // Compose inside a fragment sees the view lifecycle as LocalLifecycleOwner; it dies with the
        // view, so the screen that outlives it is the fragment.
        val fragment = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

        assertSame(fragment, fragment.innerView.findScreenOwner(explicitOwner = fragment.viewLifecycleOwner))
    }

    @Test
    fun `a view straight in an activity has no screen that outlives it`() {
        val view = View(activity)
        activity.setContentView(view)

        assertNull(view.findScreenOwner())
    }

    @Test
    fun `an activity going away means nothing to keep the content for`() {
        val fragment = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

        assertNull(fragment.innerView.findScreenOwner(destroyedOwner = activity))
    }

    @Test
    fun `a wrapper's own owner wins over the view tree`() {
        val view = View(activity)
        activity.setContentView(view)
        val entry = Screen()

        assertSame(entry, view.findScreenOwner(explicitOwner = entry))
    }

    @Test
    fun `an explicit owner that is an activity, being destroyed or destroyed counts for nothing`() {
        val view = View(activity)
        activity.setContentView(view)
        val destroyed = Screen()

        assertNull(view.findScreenOwner(explicitOwner = activity))
        assertNull(view.findScreenOwner(destroyedOwner = destroyed, explicitOwner = destroyed))
        assertNull(view.findScreenOwner(explicitOwner = Screen(Lifecycle.State.DESTROYED)))
    }

    @Test
    fun `a detached view without any owner around has none`() {
        assertNull(View(activity).findScreenOwner())
    }
}
