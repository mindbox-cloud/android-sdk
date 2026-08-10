package cloud.mindbox.mobile_sdk.embedded.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockListener
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView

/**
 * An embedded Mindbox block as a composable.
 *
 * The caller marks a *place* by its [placeSystemName] — what the place shows is decided by the
 * mobile config, the app never learns it. **The caller owns the size**: give the block an
 * explicit height (e.g. `Modifier.height(120.dp)`) — the block is a fixed frame and the content
 * adapts to it, so the layout never jumps. While the content loads the frame shows a placeholder
 * (the SDK's default placeholder or the [placeholder] slot); on failure — the [error] slot, if set.
 *
 * The behavior mirrors the View one and belongs to the block itself: it is visible while
 * loading and showing content, and collapses to zero height when the place ends up without
 * content — unless the [error] slot is set: a custom error view is a request to keep the
 * place, so the block stays and shows it. The callbacks only report the outcome.
 *
 * ```kotlin
 * MindboxEmbeddedBlock(
 *     placeSystemName = "main-screen-top",
 *     modifier = Modifier.height(120.dp),
 *     onLoad = { /* the block is shown */ },
 * )
 * ```
 *
 * @param placeSystemName The place identifier matched against the config's `inlineBlocks`
 * section. Changing it recreates the block for the new place. Blocks with the same name work
 * independently, each with its own content.
 * @param onLoad The block is shown and visible. Main thread.
 * @param onFail The place ends up without content — the load failed or timed out, or the
 * config had nothing to put here. The block collapsed, or — if the [error] slot is set —
 * stayed in place showing it. Not necessarily a breakage: an empty place is a normal outcome.
 * Main thread.
 * @param placeholder Replaces the SDK's default loading placeholder. Fills the whole block frame.
 * @param error The view for a place without content. Setting it also keeps the block visible
 * instead of the default collapse. Fills the whole block frame.
 */
@OptIn(InternalMindboxApi::class)
@Composable
public fun MindboxEmbeddedBlock(
    placeSystemName: String,
    modifier: Modifier = Modifier,
    onLoad: () -> Unit = {},
    onFail: () -> Unit = {},
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    val currentOnLoad by rememberUpdatedState(onLoad)
    val currentOnFail by rememberUpdatedState(onFail)
    val currentPlaceholder by rememberUpdatedState(placeholder)
    val currentError by rememberUpdatedState(error)

    key(placeSystemName) {
        var isCollapsed by remember { mutableStateOf(false) }

        AndroidView(
            modifier = (if (isCollapsed) Modifier.height(0.dp).then(modifier) else modifier).fillMaxWidth(),
            factory = { context ->
                MindboxEmbeddedBlockView(context, placeSystemName).apply {
                    setVisibilityObserver { isVisible -> isCollapsed = !isVisible }
                    setListener(
                        object : MindboxEmbeddedBlockListener {
                            override fun onLoad(view: MindboxEmbeddedBlockView) {
                                currentOnLoad()
                            }

                            override fun onFail(view: MindboxEmbeddedBlockView) {
                                currentOnFail()
                            }
                        },
                    )
                    if (placeholder != null) {
                        setPlaceholderView(
                            ComposeView(context).apply { setContent { currentPlaceholder?.invoke() } },
                        )
                    }
                    if (error != null) {
                        setErrorView(
                            ComposeView(context).apply { setContent { currentError?.invoke() } },
                        )
                    }
                }
            },
            onRelease = { view -> view.release() },
        )
    }
}
