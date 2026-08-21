package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import java.lang.ref.WeakReference

internal fun interface MindboxWebPage {
    fun push(action: WebViewAction, payload: String)
}

internal class MindboxWebPageRegistry {

    private val pages = mutableListOf<WeakReference<MindboxWebPage>>()

    fun register(page: MindboxWebPage) {
        val count = synchronized(pages) {
            sweep()
            if (pages.any { reference -> reference.get() === page }) return
            pages.add(WeakReference(page))
            pages.size
        }
        mindboxLogI("[WebView] Registry: page registered, $count live")
    }

    fun unregister(page: MindboxWebPage) {
        val count = synchronized(pages) {
            sweep()
            if (!pages.removeAll { reference -> reference.get() === page }) return
            pages.size
        }
        mindboxLogI("[WebView] Registry: page released, $count live")
    }

    fun broadcast(action: WebViewAction, payload: String, excludingAuthor: MindboxWebPage?) {
        val receivers = synchronized(pages) {
            sweep()
            pages.mapNotNull { reference -> reference.get() }
                .filter { page -> page !== excludingAuthor }
        }
        if (receivers.isEmpty()) {
            mindboxLogI("[WebView] Registry: nobody to receive '$action'")
            return
        }
        mindboxLogI("[WebView] Registry: broadcasting '$action' to ${receivers.size} page(s)")
        receivers.forEach { page -> page.push(action, payload) }
    }

    private fun sweep() {
        pages.removeAll { reference -> reference.get() == null }
    }
}
