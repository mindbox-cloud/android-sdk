package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.safeAs
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class InAppPositionController {
    private var inAppView: View? = null
    private var originalParent: ViewGroup? = null
    private var inAppOriginalIndex: Int = -1

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) {
                repositionInApp()
            }
        }

        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) {
                repositionInApp()
            }
        }
    }

    fun start(entryView: View): Unit =
        loggingRunCatching {
            entryView.parent.safeAs<ViewGroup>()?.let { parent ->
                this.originalParent = parent
                this.inAppView = parent.findViewById(R.id.inapp_layout_container)
                this.inAppOriginalIndex = parent.indexOfChild(inAppView)
            }

            entryView.findActivity().safeAs<FragmentActivity>()
                ?.supportFragmentManager
                ?.registerFragmentLifecycleCallbacks(
                    fragmentLifecycleCallbacks,
                    true
                )
            repositionInApp()
        }

    fun stop(): Unit = loggingRunCatching {
        originalParent?.findActivity().safeAs<FragmentActivity>()
            ?.supportFragmentManager
            ?.unregisterFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks
            )
        inAppView = null
        originalParent = null
    }

    private fun repositionInApp(): Unit = loggingRunCatching {
        val activity = inAppView?.findActivity().safeAs<FragmentActivity>() ?: return@loggingRunCatching
        val topDialog = findTopDialogFragment(activity.supportFragmentManager)
        val targetParent = topDialog?.dialog?.window?.decorView.safeAs<ViewGroup>()
        if (targetParent != null) {
            if (inAppView?.parent != targetParent) {
                moveViewToTarget(inAppView, targetParent)
                val currentFocus = originalParent?.findActivity()?.currentFocus
                if (currentFocus != null && currentFocus != inAppView) {
                    currentFocus.clearFocus()
                }
                inAppView?.requestFocus()
            }
        } else {
            repositionInappToOriginal()
        }
    }

    private fun repositionInappToOriginal() {
        val original = originalParent ?: return
        if (inAppView?.parent == original) return
        moveViewToTarget(inAppView, original, inAppOriginalIndex)
        inAppView?.requestFocus()
    }

    private fun moveViewToTarget(view: View?, target: ViewGroup, index: Int = -1) {
        if (view == null) return
        view.parent.safeAs<ViewGroup>()?.removeView(view)
        if (index != -1) {
            target.addView(view, index)
        } else {
            target.addView(view)
        }
    }

    private fun findTopDialogFragment(fragmentManager: FragmentManager): DialogFragment? {
        return fragmentManager.fragments.filterIsInstance<DialogFragment>().lastOrNull { it.isAdded }
    }

    private fun View.findActivity(): Activity? {
        var context = this.context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
