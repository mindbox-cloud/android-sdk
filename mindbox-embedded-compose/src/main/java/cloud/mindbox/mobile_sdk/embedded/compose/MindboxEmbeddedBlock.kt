package cloud.mindbox.mobile_sdk.embedded.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockAppearance
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockFailReason
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockListener
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockLoadingStrategy
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
import cloud.mindbox.mobile_sdk.logger.Level
import kotlin.math.roundToInt

/**
 * An embedded Mindbox block as a composable.
 *
 * The caller marks a *place* by its [placeSystemName] — what the place shows is decided by the
 * mobile config, the app never learns it. **The caller owns the size**: give the block an
 * explicit height (e.g. `Modifier.height(120.dp)`) — the block is a fixed frame and the content
 * adapts to it. What the block shows before the SDK answers is decided by [loadingStrategy]: a
 * placeholder (the SDK's default placeholder or the [placeholder] slot), nothing, or — by
 * default — nothing until the place has shown content once on this device and a placeholder from
 * then on. A block that takes its space up front keeps the layout still at the price of flashing
 * where there is nothing to show; a block that waits hidden never flashes at the price of the
 * layout growing when content arrives — a place that always has a campaign behind it is worth an
 * explicit [MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER]. On failure the [error] slot shows,
 * if set. The content is revealed with the SDK's own animation — a fade, and the growth of a
 * block that waited hidden — unless [animatesReveal] is off.
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
 * @param timeoutMs How long the block waits to learn what it shows before failing with
 * [MindboxEmbeddedBlockFailReason.NETWORK_ERROR] — [onFail], and the [error] slot applies — in
 * milliseconds. `null` means the SDK default of 30 s. Fixed when the block is created, as the
 * place is: a new value given to a block already on screen is ignored, and the block says so in
 * the log. Wrap the block in a `key()` of your own to build one on a different budget.
 * @param loadingStrategy What the block shows until the SDK answers.
 * [MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC] — the default — keeps the block hidden until
 * the place has shown content once on this device and puts a placeholder there from then on. The
 * first look is known before the first frame, so a block that waits hidden never flashes reserved
 * space. Fixed when the block is created, as the place is.
 * @param animatesReveal Whether the SDK animates the reveal of the content — a fade, and the
 * growth of a block that waited hidden. `true` by default. Turn it off to animate the block's
 * container yourself in [onLoad]. Fixed when the block is created, as the place is.
 * @param onLoad The block is shown and visible. Main thread.
 * @param onEmpty The place has nothing to show — a normal outcome, not a breakage: no campaign
 * for the place, or the targeting, the A/B split, the show limits or the page itself left it
 * empty. The block collapsed; the [error] slot does not apply. Main thread.
 * @param onFail The block could not get or show its content; the reason is one of the
 * [MindboxEmbeddedBlockFailReason] constants, for the host's logs. Not called for an empty place —
 * that is [onEmpty], so a section hidden from `onFail` alone stays for a place with nothing to
 * show. The block collapsed, or — if the [error] slot is set — stayed in place showing it. Main
 * thread.
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
    loadingStrategy: MindboxEmbeddedBlockLoadingStrategy = MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC,
    animatesReveal: Boolean = true,
    onLoad: () -> Unit = {},
    onEmpty: () -> Unit = {},
    onFail: (MindboxEmbeddedBlockFailReason) -> Unit = {},
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    val currentOnLoad by rememberUpdatedState(onLoad)
    val currentOnEmpty by rememberUpdatedState(onEmpty)
    val currentOnFail by rememberUpdatedState(onFail)
    val currentPlaceholder by rememberUpdatedState(placeholder)
    val currentError by rememberUpdatedState(error)

    val context = LocalContext.current
    val screenOwner by rememberUpdatedState(LocalLifecycleOwner.current)

    key(placeSystemName) {
        val creationTimeoutMs = rememberFixedAtCreation(placeSystemName, "timeoutMs", timeoutMs)
        val creationLoadingStrategy = rememberFixedAtCreation(placeSystemName, "loadingStrategy", loadingStrategy)
        val creationAnimatesReveal = rememberFixedAtCreation(placeSystemName, "animatesReveal", animatesReveal)
        var look by remember {
            mutableStateOf(
                AppearanceChange(
                    MindboxEmbeddedBlockView.initialAppearance(context, placeSystemName, creationLoadingStrategy),
                    animated = false,
                ),
            )
        }

        val placeholderHost = rememberLazySlotHost { currentPlaceholder }
        val errorHost = rememberLazySlotHost { currentError }

        val revealFraction = remember { Animatable(look.appearance.targetFraction) }
        LaunchedEffect(look.appearance) {
            val reveals = look.appearance == MindboxEmbeddedBlockAppearance.CONTENT && revealFraction.value < 1f
            if (reveals && look.animated) {
                revealFraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(MindboxEmbeddedBlockView.REVEAL_ANIMATION_DURATION_MS.toInt()),
                )
            } else {
                revealFraction.snapTo(look.appearance.targetFraction)
            }
        }

        AndroidView(
            modifier = Modifier
                .revealHeight(look.appearance) { revealFraction.value }
                .then(modifier)
                .fillMaxWidth(),
            factory = { viewContext ->
                MindboxEmbeddedBlockView(
                    context = viewContext,
                    placeSystemName = placeSystemName,
                    timeoutMs = creationTimeoutMs,
                    loadingStrategy = creationLoadingStrategy,
                    animatesReveal = creationAnimatesReveal,
                ).apply {
                    setScreenOwner(screenOwner)
                    setAppearanceObserver { shown -> look = AppearanceChange(shown, isRevealAnimated) }
                    setListener(
                        object : MindboxEmbeddedBlockListener {
                            override fun onLoad(view: MindboxEmbeddedBlockView) {
                                currentOnLoad()
                            }

                            override fun onEmpty(view: MindboxEmbeddedBlockView) {
                                currentOnEmpty()
                            }

                            override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {
                                currentOnFail(reason)
                            }
                        },
                    )
                }
            },
            update = { view ->
                view.setScreenOwner(screenOwner)
                view.setPlaceholderView(placeholder?.let { placeholderHost.value })
                view.setErrorView(error?.let { errorHost.value })
            },
            onRelease = { view -> view.releaseOrRetain() },
        )
    }
}

private data class AppearanceChange(
    val appearance: MindboxEmbeddedBlockAppearance,
    val animated: Boolean,
)

private val MindboxEmbeddedBlockAppearance.targetFraction: Float
    get() = if (this == MindboxEmbeddedBlockAppearance.COLLAPSED) 0f else 1f

@Composable
private fun <T> rememberFixedAtCreation(placeSystemName: String, name: String, value: T): T {
    val creationValue = remember { value }
    LaunchedEffect(value) {
        if (value != creationValue) {
            Mindbox.writeLog(
                "[EmbeddedBlock] Block '$placeSystemName' was given $name=$value after creation " +
                    "and keeps $creationValue: $name is fixed when the block is created. Wrap the " +
                    "block in a key() of your own to build one anew.",
                Level.WARN,
            )
        }
    }
    return creationValue
}

@Composable
private fun rememberLazySlotHost(slot: () -> (@Composable () -> Unit)?): Lazy<ComposeView> {
    val context = LocalContext.current
    return remember(context) {
        lazy(LazyThreadSafetyMode.NONE) {
            ComposeView(context).apply { setContent { slot()?.invoke() } }
        }
    }
}

private fun Modifier.revealHeight(
    appearance: MindboxEmbeddedBlockAppearance,
    revealFraction: () -> Float,
): Modifier =
    if (appearance == MindboxEmbeddedBlockAppearance.COLLAPSED) {
        height(0.dp)
    } else {
        clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val revealedHeight = (placeable.height * revealFraction()).roundToInt()
            layout(placeable.width, revealedHeight) { placeable.placeRelative(0, 0) }
        }
    }
