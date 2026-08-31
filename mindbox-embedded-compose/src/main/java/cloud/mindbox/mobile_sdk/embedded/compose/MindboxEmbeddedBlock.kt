package cloud.mindbox.mobile_sdk.embedded.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockAppearance
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockListener
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
import cloud.mindbox.mobile_sdk.logger.Level

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
 * content — unless it failed and the [error] slot is set: that slot is a request to show a
 * failure, so the block stays and shows it. An empty place collapses either way. The callbacks
 * only report the outcome.
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
 * @param timeoutMs How long the block waits to learn what it shows before collapsing as empty, in
 * milliseconds. `null` means the SDK default of 30 s. Fixed when the block is created, as the
 * place is: a new value given to a block already on screen is ignored, and the block says so in
 * the log. Wrap the block in a `key()` of your own to build one on a different budget.
 * @param onLoad The block is shown and visible. Main thread.
 * @param onFail The place ends up without content — the load failed or timed out, or the
 * config had nothing to put here. The block collapsed, or — if the [error] slot is set —
 * stayed in place showing it. Not necessarily a breakage: an empty place is a normal outcome.
 * Main thread.
 * @param placeholder Replaces the SDK's default loading placeholder. Fills the whole block frame.
 * @param error The view for a block that failed. Setting it keeps the block visible instead of
 * the default collapse; an empty place collapses regardless. Fills the whole block frame.
 */
@OptIn(InternalMindboxApi::class)
@Composable
public fun MindboxEmbeddedBlock(
    placeSystemName: String,
    modifier: Modifier = Modifier,
    timeoutMs: Long? = null,
    onLoad: () -> Unit = {},
    onFail: () -> Unit = {},
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    val currentOnLoad by rememberUpdatedState(onLoad)
    val currentOnFail by rememberUpdatedState(onFail)
    val currentPlaceholder by rememberUpdatedState(placeholder)
    val currentError by rememberUpdatedState(error)

    val context = LocalContext.current

    key(placeSystemName) {
        var appearance by remember {
            mutableStateOf(MindboxEmbeddedBlockAppearance.PLACEHOLDER)
        }

        val creationTimeoutMs = remember { timeoutMs }
        if (timeoutMs != creationTimeoutMs) {
            LaunchedEffect(timeoutMs) {
                Mindbox.writeLog(
                    "[EmbeddedBlock] Block '$placeSystemName' was given timeoutMs=$timeoutMs after " +
                        "creation and keeps $creationTimeoutMs: the timeout is fixed when the block " +
                        "is created. Wrap the block in a key() of your own to build one on a " +
                        "different budget.",
                    Level.WARN,
                )
            }
        }

        val placeholderHost = remember(context) {
            lazy(LazyThreadSafetyMode.NONE) {
                ComposeView(context).apply { setContent { currentPlaceholder?.invoke() } }
            }
        }
        val errorHost = remember(context) {
            lazy(LazyThreadSafetyMode.NONE) {
                ComposeView(context).apply { setContent { currentError?.invoke() } }
            }
        }

        AndroidView(
            modifier = (
                if (appearance == MindboxEmbeddedBlockAppearance.COLLAPSED) {
                    Modifier.height(0.dp).then(modifier)
                } else {
                    modifier
                }
            ).fillMaxWidth(),
            factory = { viewContext ->
                MindboxEmbeddedBlockView(viewContext, placeSystemName, timeoutMs).apply {
                    setAppearanceObserver { shown -> appearance = shown }
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
                }
            },
            update = { view ->
                view.setPlaceholderView(placeholder?.let { placeholderHost.value })
                view.setErrorView(error?.let { errorHost.value })
            },
            onRelease = { view -> view.release() },
        )
    }
}
