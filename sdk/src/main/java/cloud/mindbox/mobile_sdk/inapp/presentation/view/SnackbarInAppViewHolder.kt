package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.View
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.SnackbarPosition
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.px

internal class SnackbarInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.Snackbar>,
    private val inAppCallback: InAppCallback,
    private val inAppImageSizeStorage: InAppImageSizeStorage,
    private val isFirstShow: Boolean = true,
) : AbstractInAppViewHolder<InAppType.Snackbar>() {

    override val isActive: Boolean
        get() = isInAppMessageActive

    private var requiredSizes: HashMap<String, Size> = HashMap()

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.ImageLayer -> {
                    addUrlSource(layer, inAppCallback)
                }

                else -> {}
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        currentDialog.requestFocus()
    }

    override fun initView(currentRoot: ViewGroup) {
        super.initView(currentRoot)
        currentDialog.setSwipeToDismissCallback {
            mindboxLogI("In-app dismissed by swipe")
            hideWithAnimation()
        }
    }

    override fun addUrlSource(
        layer: Layer.ImageLayer,
        inAppCallback: InAppCallback
    ) {
        super.addUrlSource(layer, inAppCallback)
        when (layer.source) {
            is Layer.ImageLayer.Source.UrlSource -> {
                InAppImageView(currentDialog.context).also { inAppImageView ->
                    inAppImageView.visibility = View.INVISIBLE
                    currentDialog.addView(inAppImageView)
                    when (wrapper.inAppType.position.margin.kind) {
                        InAppType.Snackbar.Position.Margin.MarginKind.DP -> {
                            if (!requiredSizes.containsKey(wrapper.inAppType.inAppId)) {
                                requiredSizes[wrapper.inAppType.inAppId] = inAppImageSizeStorage.getSizeByIdAndUrl(
                                    wrapper.inAppType.inAppId,
                                    layer.source.url
                                )
                            }
                            inAppImageView.prepareViewForSnackBar(
                                requiredSizes[wrapper.inAppType.inAppId]!!,
                                wrapper.inAppType.position.margin.left.px,
                                wrapper.inAppType.position.margin.right.px
                            )
                            preparedImages[inAppImageView] = false
                        }
                    }
                    getImageFromCache(layer.source.url, inAppImageView)
                }
            }
        }
    }

    override fun bind() {
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(currentDialog.context, element).apply {
                        setOnClickListener {
                            mindboxLogI("In-app dismissed by close click")
                            hideWithAnimation()
                        }
                    }
                    currentDialog.addView(inAppCrossView)
                    inAppCrossView.prepareViewForSnackbar(currentDialog)
                }
            }
        }
        if (isFirstShow) {
            when (wrapper.inAppType.position.gravity.vertical) {
                SnackbarPosition.TOP -> currentDialog.slideDown()
                SnackbarPosition.BOTTOM -> currentDialog.slideUp()
            }
        }
    }

    private fun hideWithAnimation() {
        inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
        when (wrapper.inAppType.position.gravity.vertical) {
            SnackbarPosition.TOP -> currentDialog.slideDown(
                isReverse = true,
                onAnimationEnd = ::hide
            )

            SnackbarPosition.BOTTOM -> currentDialog.slideUp(
                isReverse = true,
                onAnimationEnd = ::hide
            )
        }
    }
}
