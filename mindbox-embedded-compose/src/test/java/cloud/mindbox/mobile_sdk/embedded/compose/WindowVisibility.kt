package cloud.mindbox.mobile_sdk.embedded.compose

import android.view.View
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Robolectric leaves every window at GONE, and the embedded block starts its content only once
 * the window is visible — so without this a unit-tested block would never start. On a device the
 * framework dispatches the same call down the view tree right after the attach.
 *
 * The entry point is hidden from the SDK stubs, so it is reached through Robolectric's own
 * helper for framework internals.
 */
internal fun dispatchWindowVisibility(view: View, visibility: Int) {
    ReflectionHelpers.callInstanceMethod<Unit>(
        view,
        "dispatchWindowVisibilityChanged",
        ClassParameter.from(Int::class.javaPrimitiveType, visibility),
    )
}
