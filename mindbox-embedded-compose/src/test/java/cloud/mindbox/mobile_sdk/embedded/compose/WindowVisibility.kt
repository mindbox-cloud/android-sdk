package cloud.mindbox.mobile_sdk.embedded.compose

import android.view.View
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

internal fun dispatchWindowVisibility(view: View, visibility: Int) {
    ReflectionHelpers.callInstanceMethod<Unit>(
        view,
        "dispatchWindowVisibilityChanged",
        ClassParameter.from(Int::class.javaPrimitiveType, visibility),
    )
}
