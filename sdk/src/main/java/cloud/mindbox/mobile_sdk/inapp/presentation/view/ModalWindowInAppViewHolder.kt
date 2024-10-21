package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.Element
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.removeChildById

internal class ModalWindowInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.ModalWindow>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.ModalWindow>() {

    private var currentBackground: ViewGroup? = null

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(
                        currentDialog.context,
                        element
                    ).apply {
                        setOnClickListener {
                            mindboxLogI("In-app dismissed by close click")
                            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                            hide()
                        }
                    }
                    currentDialog.addView(inAppCrossView)
                    inAppCrossView.prepareViewForModalWindow(currentDialog)
                }
            }
        }
        currentBackground?.setOnClickListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by background click")
            hide()
        }
        currentBackground?.isVisible = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun addUrlSource(layer: Layer.ImageLayer, inAppCallback: InAppCallback) {
        super.addUrlSource(layer, inAppCallback)
        when (layer.source) {
            is Layer.ImageLayer.Source.UrlSource -> {
                val webView = WebView(currentDialog.context).apply {
                    setWebChromeClient(WebChromeClient())
                    setWebViewClient(object : WebViewClient() {
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                        }

                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            if (url == "https://personalization-web-staging.mindbox.ru/web/contacts/28553/") {
                                mindboxLogI("onCompleted script. Close inapp")
                                hide()
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                        }

                        override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
                            return super.shouldOverrideKeyEvent(view, event)
                        }
                    })
                    layoutParams = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.defaultTextEncodingName = "utf-8"
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    setBackgroundColor(Color.TRANSPARENT)
                }
                val unEncodedHtml = currentDialog.context.assets
                    .open("webview.html")
                    .bufferedReader()
                    .use { it.readText() }
                val baseUrl = "file:///android_asset/"
                currentDialog.addView(webView)
                webView.loadDataWithBaseURL(baseUrl, unEncodedHtml, "text/html", "UTF-8", null)
            }
        }
    }

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.ImageLayer -> {
                    addUrlSource(layer, inAppCallback)
                }
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        currentDialog.requestFocus()
    }

    override fun hide() {
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentBackground)
        }
        super.hide()
    }

    override fun initView(currentRoot: ViewGroup) {
        currentRoot.removeChildById(R.id.inapp_background_layout)
        currentBackground = LayoutInflater
            .from(currentRoot.context)
            .inflate(R.layout.mindbox_blur_layout, currentRoot, false) as FrameLayout
        currentRoot.addView(currentBackground)
        super.initView(currentRoot)
    }
}
