package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class InAppPositionController {

    private var activity: Activity? = null
    private var inAppView: View? = null
    private var backgroundView: View? = null
    private var originalParent: ViewGroup? = null
    private var inAppOriginalIndex: Int = -1
    private var backgroundOriginalIndex: Int = -1

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) {
                repositionToTop()
            }
        }

        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) {
                repositionToTop()
            }
        }
    }

    fun start(entryView: View): Unit =
        loggingRunCatching {
            this.activity = entryView.context.findActivity()
            if (activity == null) {
                return@loggingRunCatching
            }
            (entryView.parent as? ViewGroup)?.let { parent ->
                this.originalParent = parent
                this.inAppView = parent.findViewById(R.id.inapp_layout)
                this.backgroundView = parent.findViewById(R.id.inapp_background_layout)
                this.inAppOriginalIndex = parent.indexOfChild(inAppView)
                this.backgroundOriginalIndex = backgroundView?.let { parent.indexOfChild(it) } ?: -1
            }

            (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks,
                true
            )
            repositionToTop()
        }

    fun stop(): Unit = loggingRunCatching {
        if (activity == null) return@loggingRunCatching

        (activity as? FragmentActivity)?.supportFragmentManager?.unregisterFragmentLifecycleCallbacks(
            fragmentLifecycleCallbacks
        )
        activity = null
        inAppView = null
        backgroundView = null
        originalParent = null
    }

    private fun repositionToTop() {
        val (inApp, background, _) = retrieveViews() ?: return
        inApp?.post {
            val topDialog = findTopDialogFragment()
            val targetParent = topDialog?.dialog?.window?.decorView as? ViewGroup
            if (targetParent != null) {
                if (inApp.parent != targetParent) {
                    moveViewToTarget(background, targetParent)
                    moveViewToTarget(inApp, targetParent)
                    val currentFocus = activity?.currentFocus
                    if (currentFocus != null && currentFocus != inApp) {
                        currentFocus.clearFocus()
                    }
                    inApp.requestFocus()
                }
            } else {
                repositionToOriginal()
            }
        }
    }

    private fun repositionToOriginal() {
        val (inApp, background, original) = retrieveViews() ?: return

        if (original != null && inApp?.parent != original) {
            background?.let { moveViewToTarget(background, original, backgroundOriginalIndex) }
            moveViewToTarget(inApp, original, inAppOriginalIndex)
            inApp?.requestFocus()
        }
    }

    private fun retrieveViews(): Triple<View?, View?, ViewGroup?>? {
        val parent = originalParent ?: return null
        return Triple(inAppView, backgroundView, parent)
    }

    private fun moveViewToTarget(view: View?, target: ViewGroup, index: Int = -1) {
        if (view == null) return
        (view.parent as? ViewGroup)?.removeView(view)
        if (index != -1) {
            target.addView(view, index)
        } else {
            target.addView(view)
        }
    }

    private fun findTopDialogFragment(): DialogFragment? {
        val fragmentManager = (activity as? FragmentActivity)?.supportFragmentManager ?: return null
        return fragmentManager.fragments.filterIsInstance<DialogFragment>().lastOrNull { it.isAdded }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
