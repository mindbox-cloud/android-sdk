package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal fun interface BackRegistration {
    fun unregister()
}

internal interface BackPressRegistrar {
    fun register(layout: BackButtonLayout, onBackPress: () -> Unit): BackRegistration
}

internal class ActivityBackPressRegistrar(
    private val activityProvider: () -> Activity?,
) : BackPressRegistrar {

    override fun register(layout: BackButtonLayout, onBackPress: () -> Unit): BackRegistration {
        layout.setBackListener(onBackPress)
        val systemBackRegistration: BackRegistration = registerSystemBackCallback(onBackPress)
        return BackRegistration {
            layout.setBackListener(null)
            systemBackRegistration.unregister()
        }
    }

    private fun registerSystemBackCallback(onBackPress: () -> Unit): BackRegistration {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return BackRegistration {}
        }
        val activity: Activity = activityProvider() ?: return BackRegistration {}
        val callback = OnBackInvokedCallback {
            mindboxLogI("OnBackInvokedCallback fired")
            onBackPress()
        }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        return BackRegistration {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}
