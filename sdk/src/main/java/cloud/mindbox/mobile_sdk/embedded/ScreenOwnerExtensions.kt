package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

internal fun View.findScreenOwner(
    destroyedOwner: LifecycleOwner? = null,
    explicitOwner: LifecycleOwner? = null,
): LifecycleOwner? {
    val fragment = findHostFragment()
    val candidate = when {
        explicitOwner != null && fragment != null && explicitOwner === fragment.viewLifecycleOwnerOrNull() -> fragment
        explicitOwner != null -> explicitOwner
        else -> fragment ?: findViewTreeLifecycleOwner()
    }
    return candidate?.takeIf { owner -> owner.isScreenOwnerFor(destroyedOwner) }
}

internal fun LifecycleOwner.isScreenOwnerFor(destroyedOwner: LifecycleOwner?): Boolean =
    destroyedOwner !is Activity && this !is Activity && this !== destroyedOwner &&
        lifecycle.currentState != Lifecycle.State.DESTROYED

private fun View.findHostFragment(): Fragment? =
    runCatching { FragmentManager.findFragment<Fragment>(this) }.getOrNull()

private fun Fragment.viewLifecycleOwnerOrNull(): LifecycleOwner? =
    runCatching { viewLifecycleOwner }.getOrNull()
