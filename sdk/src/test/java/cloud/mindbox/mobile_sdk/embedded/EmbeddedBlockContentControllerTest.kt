package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import android.os.Looper
import android.view.View
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.Closeable
import java.time.Duration

/**
 * The per-view state machine: both timeouts, the failed-state re-resolve, the same-winner
 * dedup and the paused-delivery deferral.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentControllerTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        val droppedPlaces = mutableListOf<String>()

        override fun onBlockContentDropped(placeSystemName: PlaceKey) {
            droppedPlaces.add(placeSystemName.value)
        }

        val appearedPlaces = mutableListOf<String>()
        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) {
            appearedPlaces.add(placeSystemName.value)
        }

        override fun startListening() = Unit

        fun pushContent(placeSystemName: String, content: InAppType.Embedded) {
            lastHandle?.onContentResolved(content)
        }
    }

    private class FakeProvider(
        override val contentView: View? = View(RuntimeEnvironment.getApplication()),
        private val onStart: FakeProvider.() -> Unit = { onStateChange?.invoke(EmbeddedBlockState.Ready) },
    ) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        var startCount = 0
        var releaseCount = 0

        override fun start() {
            startCount++
            onStart()
        }

        override fun pause() = Unit

        override fun release() {
            releaseCount++
        }
    }

    private val blocksRegistry = FakeBlocksRegistry()
    private val createdProviders = mutableListOf<FakeProvider>()
    private val states = mutableListOf<EmbeddedBlockState>()
    private val networkError = EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR)
    private val internalError = EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR)

    private fun controller(
        configTimeout: Milliseconds = Milliseconds(30_000L),
        tracker: InAppFailureTracker? = null,
        providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider? = { _, _ ->
            FakeProvider().also { createdProviders.add(it) }
        },
    ): EmbeddedBlockContentController =
        EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = configTimeout,
            providerFactory = providerFactory,
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
            isTagsFeatureEnabled = { true },
        ).apply {
            onStateChange = { state -> states.add(state) }
        }

    private fun idleFor(duration: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
    }

    private val content = InAppStub.getEmbedded()

    @Test
    fun `start registers and pulls content for the place`() {
        val controller = controller()

        controller.start()

        assertEquals(listOf("main-screen-top"), blocksRegistry.appearedPlaces)
        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states)
    }

    @Test
    fun `no config within timeout fails with a network error`() {
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()

        idleFor(Duration.ofMillis(30_001L))

        assertEquals(networkError, states.last())
    }

    @Test
    fun `pending winner disarms the waiting budget and keeps the skeleton`() {
        // A delayed winner is the SDK's answer, not its silence: the 30s budget stands down
        // while the block stays in the loading state until the delivery.
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()

        blocksRegistry.lastHandle?.onContentPending()
        idleFor(Duration.ofMillis(30_001L))

        assertEquals(EmbeddedBlockState.Loading, states.last())
    }

    @Test
    fun `the delay window leaves the attempt clock`() {
        var clock = 1_000L
        var receivedStart: Long? = null
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, startTick ->
                receivedStart = startTick.interval
                FakeProvider()
            },
            blocksRegistry = { blocksRegistry },
            monotonicNow = { Milliseconds(clock) },
        ).apply { onStateChange = { state -> states.add(state) } }

        controller.start()
        // The campaign's delay begins at 2s and delivers at 7s: those five seconds are the
        // campaign's choice, not the user's wait for the SDK — the clock base slides past them.
        clock = 2_000L
        blocksRegistry.lastHandle?.onContentPending()
        clock = 7_000L
        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(6_000L, receivedStart)
    }

    @Test
    fun `the delay window leaves the attempt clock of a block that was off screen`() {
        var clock = 1_000L
        var receivedStart: Long? = null
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, startTick ->
                receivedStart = startTick.interval
                FakeProvider()
            },
            blocksRegistry = { blocksRegistry },
            monotonicNow = { Milliseconds(clock) },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        controller.pause()

        clock = 2_000L
        blocksRegistry.lastHandle?.onContentPending()
        clock = 7_000L
        blocksRegistry.pushContent("main-screen-top", content)
        clock = 8_000L
        controller.start()

        assertEquals(6_000L, receivedStart)
    }

    @Test
    fun `a padded mixed-case place name is normalized for the registry and the failure report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = EmbeddedBlockContentController(
            placeSystemName = "  Main-Screen-Top  ",
            configTimeout = Milliseconds(50L),
            providerFactory = { _, _ -> FakeProvider() },
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        idleFor(Duration.ofMillis(51L))

        assertEquals(listOf("main-screen-top"), blocksRegistry.appearedPlaces)
        verify(exactly = 1) {
            tracker.sendPlaceWaitBudgetExceeded(
                PlaceKey.of("main-screen-top"),
                Milliseconds(50L),
                cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase.CONFIG_MISSING,
            )
        }
    }

    @Test
    fun `config timeout ships the anonymous ShowFailure`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(50L),
            providerFactory = { _, _ -> FakeProvider() },
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        idleFor(Duration.ofMillis(51L))

        // The SDK stayed silent for the whole budget: the fact ships with no in-app to name.
        verify(exactly = 1) {
            tracker.sendPlaceWaitBudgetExceeded(
                PlaceKey.of("main-screen-top"),
                Milliseconds(50L),
                cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase.CONFIG_MISSING,
            )
        }
        assertEquals(networkError, states.last())
    }

    @Test
    fun `a config present but a silent resolve ships the resolve_pending phase`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(50L),
            providerFactory = { _, _ -> FakeProvider() },
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
            hasConfig = { true },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        idleFor(Duration.ofMillis(51L))

        verify(exactly = 1) {
            tracker.sendPlaceWaitBudgetExceeded(
                PlaceKey.of("main-screen-top"),
                Milliseconds(50L),
                cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase.RESOLVE_PENDING,
            )
        }
    }

    @Test
    fun `a page silent past its budget ships presentation_failed with the snapshot tags`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val silentProvider = object : EmbeddedContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() = Unit

            override fun pause() = Unit

            override fun release() = Unit
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            readyTimeout = Milliseconds(100L),
            providerFactory = { _, _ -> silentProvider },
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
            isTagsFeatureEnabled = { true },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content.copy(tags = mapOf("a" to "b")))
        idleFor(Duration.ofMillis(101L))

        verify(exactly = 1) {
            tracker.sendFailure(
                inAppId = "embedded-id",
                failureReason = cloud.mindbox.mobile_sdk.models.operation.request.FailureReason.PRESENTATION_FAILED,
                errorDetails = any(),
                tags = mapOf("a" to "b"),
            )
        }
        assertEquals(internalError, states.last())
    }

    @Test
    fun `an armed pending delivery keeps the budget quiet across leave and return`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(50L),
            providerFactory = { _, _ -> FakeProvider() },
            blocksRegistry = { blocksRegistry },
            failureTracker = { tracker },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.lastHandle?.onContentPending()

        // The block leaves and returns while the winner waits out its delay: the SDK has
        // answered — the re-armed budget must not fire a false "the SDK stayed silent".
        controller.pause()
        controller.start()
        idleFor(Duration.ofMillis(51L))

        verify(exactly = 0) { tracker.sendPlaceWaitBudgetExceeded(any(), any(), any()) }
        assertEquals(EmbeddedBlockState.Loading, states.last())
    }

    @Test
    fun `content arriving after the timeout is dropped and the block stays collapsed`() {
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()
        idleFor(Duration.ofMillis(30_001L))
        assertEquals(networkError, states.last())

        blocksRegistry.lastHandle?.onContentPending()
        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(networkError, states.last())
        assertTrue(createdProviders.isEmpty())
    }

    @Test
    fun `a block that gave up waiting is inactive for the registry until it comes back`() {
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()
        assertEquals(true, blocksRegistry.lastHandle?.isActive)

        idleFor(Duration.ofMillis(30_001L))
        assertEquals(false, blocksRegistry.lastHandle?.isActive)

        controller.pause()
        controller.start()

        assertEquals(true, blocksRegistry.lastHandle?.isActive)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `returning after the timeout asks afresh with the whole budget`() {
        val controller = controller(configTimeout = Milliseconds(50L))
        controller.start()
        idleFor(Duration.ofMillis(51L))
        assertEquals(networkError, states.last())
        controller.pause()

        controller.start()
        idleFor(Duration.ofMillis(40L))
        assertEquals(EmbeddedBlockState.Loading, states.last())

        blocksRegistry.pushContent("main-screen-top", content)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a page silent past its budget gives the block up until it comes back`() {
        var builtPages = 0
        val silentProvider = object : EmbeddedContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() = Unit

            override fun pause() = Unit

            override fun release() = Unit
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            readyTimeout = Milliseconds(100L),
            providerFactory = { _, _ -> silentProvider.also { builtPages++ } },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        idleFor(Duration.ofMillis(101L))
        assertEquals(internalError, states.last())

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(1, builtPages)
        assertEquals(false, blocksRegistry.lastHandle?.isActive)
        assertEquals(internalError, states.last())
    }

    @Test
    fun `host resource is not consulted here — the timeout is a constructor value`() {
        // The view reads the integer resource / XML attribute; the controller only obeys it.
        val controller = controller(configTimeout = Milliseconds(50L))
        controller.start()

        idleFor(Duration.ofMillis(51L))

        assertEquals(networkError, states.last())
    }

    @Test
    fun `resolved content becomes Ready`() {
        val controller = controller()
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, createdProviders.size)
    }

    @Test
    fun `null content collapses to Empty`() {
        val controller = controller()
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(null)

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertTrue(createdProviders.isEmpty())
    }

    @Test
    fun `same winner with same params does not touch the content`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val provider = createdProviders.single()

        blocksRegistry.pushContent("main-screen-top", content)

        // No re-creation and no release: the layout is not poked for an identical winner.
        assertEquals(1, createdProviders.size)
        assertEquals(0, provider.releaseCount)
    }

    @Test
    fun `different winner replaces the content`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val first = createdProviders.single()

        blocksRegistry.pushContent("main-screen-top", content.copy(inAppId = "another-winner"))

        assertEquals(2, createdProviders.size)
        assertEquals(1, first.releaseCount)
    }

    @Test
    fun `content delivered while paused is applied on the next start`() {
        val controller = controller()
        controller.start()
        controller.pause()

        blocksRegistry.lastHandle?.onContentResolved(content)
        // Nothing is drawn in the background.
        assertTrue(createdProviders.isEmpty())

        controller.start()

        assertEquals(1, createdProviders.size)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `content parked while paused keeps the place's hold until the block applies it`() {
        val controller = controller()
        controller.start()
        val handle = blocksRegistry.lastHandle!!
        handle.onContentResolved(null)
        controller.pause()
        assertFalse(handle.isHoldingContent)

        handle.onContentResolved(content)

        // The registry reserved for this delivery; a neighbour dropping its content must not free it.
        assertTrue(handle.isHoldingContent)
        controller.start()
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertTrue(handle.isHoldingContent)
    }

    @Test
    fun `failed state re-resolves on returning to the screen`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val provider = createdProviders.single()
        provider.onStateChange?.invoke(networkError)
        controller.pause()

        controller.start()

        // The failed provider is dropped and the place is asked again — not resumed.
        assertEquals(1, provider.releaseCount)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `page silence within ready timeout reports Failed`() {
        val silentProvider = object : EmbeddedContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() = Unit // never reports anything

            override fun pause() = Unit

            override fun release() = Unit
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            readyTimeout = Milliseconds(7_000L),
            providerFactory = { _, _ -> silentProvider },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        idleFor(Duration.ofMillis(7_001L))

        assertEquals(internalError, states.last())
    }

    @Test
    fun `config clock counts only the time the block is on screen`() {
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()
        idleFor(Duration.ofSeconds(20))
        controller.pause()
        // Off screen the clock stands still, however long the block stays there.
        idleFor(Duration.ofSeconds(20))
        controller.start()

        idleFor(Duration.ofSeconds(9))
        assertTrue(states.none { state -> state is EmbeddedBlockState.Empty })

        idleFor(Duration.ofSeconds(2))
        assertEquals(networkError, states.last())
    }

    @Test
    fun `page budget is not refilled by re-entering the screen`() {
        val silentProvider = object : EmbeddedContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() = Unit // never reports anything

            override fun pause() = Unit

            override fun release() = Unit
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            readyTimeout = Milliseconds(7_000L),
            providerFactory = { _, _ -> silentProvider },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        idleFor(Duration.ofSeconds(5))
        controller.pause()
        controller.start()

        // 5 of the 7 seconds are already spent: flicking the screen hands back the remainder,
        // not the whole budget.
        idleFor(Duration.ofSeconds(1))
        assertTrue(states.none { state -> state is EmbeddedBlockState.Failed })

        idleFor(Duration.ofMillis(1_100L))
        assertEquals(internalError, states.last())
    }

    @Test
    fun `non-positive config timeout falls back to the default`() {
        val controller = controller(configTimeout = Milliseconds(0L))
        controller.start()

        idleFor(Duration.ofMillis(29_999L))
        assertTrue(states.none { state -> state is EmbeddedBlockState.Empty })

        idleFor(Duration.ofMillis(2L))
        assertEquals(networkError, states.last())
    }

    @Test
    fun `provider is built with the attempt start, not the delivery moment`() {
        var clock = 1_000L
        var receivedStart: Long? = null
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, startTick ->
                receivedStart = startTick.interval
                FakeProvider()
            },
            blocksRegistry = { blocksRegistry },
            monotonicNow = { Milliseconds(clock) },
        ).apply { onStateChange = { state -> states.add(state) } }

        controller.start()
        // The place answers five seconds later; the wait belongs to timeToDisplay.
        clock = 6_000L
        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(1_000L, receivedStart)
    }

    @Test
    fun `release closes the registration`() {
        val controller = controller()
        controller.start()

        controller.release()

        assertEquals(null, blocksRegistry.lastHandle)
    }

    @Test
    fun `a re-resolve that changes only frequency or tags refreshes the snapshot without a rebuild`() {
        var snapshotFrequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency? = null
        var snapshotTags: Map<String, String>? = null
        var builtPages = 0
        val updatableProvider = object : EmbeddedUpdatableContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() {
                onStateChange?.invoke(EmbeddedBlockState.Ready)
            }

            override fun pause() = Unit

            override fun release() = Unit

            override fun refreshMetricsSnapshot(frequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency, tags: Map<String, String>?) {
                snapshotFrequency = frequency
                snapshotTags = tags
            }

            override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) = onResult(true)
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> updatableProvider.also { builtPages++ } },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        val changed = content.copy(
            frequency = cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency(
                cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency.Delay.OneTimePerSession
            ),
            tags = mapOf("a" to "b"),
        )
        blocksRegistry.pushContent("main-screen-top", changed)

        assertEquals(1, builtPages)
        assertEquals(changed.frequency, snapshotFrequency)
        assertEquals(mapOf("a" to "b"), snapshotTags)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `same winner with new params updates the content in place`() {
        var updatedParams: Map<String, String>? = null
        val updatableProvider = object : EmbeddedUpdatableContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start() {
                onStateChange?.invoke(EmbeddedBlockState.Ready)
            }

            override fun pause() = Unit

            override fun release() = Unit

            override fun refreshMetricsSnapshot(frequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency, tags: Map<String, String>?) = Unit

            override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
                updatedParams = params
                onResult(true)
            }
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> updatableProvider },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer)
            .copy(params = mapOf("items" to "[]"))
        blocksRegistry.pushContent("main-screen-top", content.copy(layers = listOf(refreshedLayer)))

        // The webview stays; only the new params travel over the bridge.
        assertEquals(mapOf("items" to "[]"), updatedParams)
    }

    @Test
    fun `same winner pointing at another page rebuilds the content`() {
        // The address is part of the page's identity: the backend re-pointing the same in-app at
        // another page must not be deduplicated into keeping the old one.
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val first = createdProviders.single()

        val movedLayer = (content.layers.single() as Layer.WebViewLayer)
            .copy(contentUrl = "https://static.example/another-page.html")
        blocksRegistry.pushContent("main-screen-top", content.copy(layers = listOf(movedLayer)))

        assertEquals(2, createdProviders.size)
        assertEquals(1, first.releaseCount)
    }

    @Test
    fun `new params with a new page address rebuild instead of updating in place`() {
        class RecordingUpdatableProvider : EmbeddedUpdatableContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())
            var updatedParams: Map<String, String>? = null

            override fun start() {
                onStateChange?.invoke(EmbeddedBlockState.Ready)
            }

            override fun pause() = Unit

            override fun release() = Unit

            override fun refreshMetricsSnapshot(frequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency, tags: Map<String, String>?) = Unit

            override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
                updatedParams = params
                onResult(true)
            }
        }

        val updatables = mutableListOf<RecordingUpdatableProvider>()
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> RecordingUpdatableProvider().also { updatables.add(it) } },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        val movedLayer = (content.layers.single() as Layer.WebViewLayer)
            .copy(contentUrl = "https://static.example/another-page.html", params = mapOf("items" to "[]"))
        blocksRegistry.pushContent("main-screen-top", content.copy(layers = listOf(movedLayer)))

        assertEquals(2, updatables.size)
        assertEquals(null, updatables.first().updatedParams)
    }

    @Test
    fun `nameless block collapses to empty`() {
        val controller = EmbeddedBlockContentController(
            placeSystemName = null,
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> FakeProvider() },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }

        controller.start()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertTrue(blocksRegistry.appearedPlaces.isEmpty())
    }

    @Test
    fun `delivery of the same winner after a failure rebuilds the content`() {
        // A failed block stays started and registered; a push or config re-resolve returning the
        // SAME winner must bring it back instead of being deduplicated into the error view.
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val failed = createdProviders.single()
        failed.onStateChange?.invoke(networkError)
        assertEquals(networkError, states.last())

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(2, createdProviders.size)
        assertEquals(1, failed.releaseCount)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a block that drew nothing asks for its content again on return`() {
        // The page answered `contentRendered {count: 0}` — every element was filtered out, expired
        // or not targeted at this customer. None of those reasons outlives the screen, so the
        // remembered "empty" must not either: iOS rebuilds here, and so do we.
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val drewNothing = createdProviders.single()
        drewNothing.onStateChange?.invoke(EmbeddedBlockState.Empty)
        controller.pause()

        controller.start()

        assertEquals(1, drewNothing.releaseCount)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `delivery of the same winner to a collapsed block rebuilds the content`() {
        // For a collapsed block the same answer is news: nothing is on screen to deduplicate
        // against, and only a rebuilt page can draw and report itself again.
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val drewNothing = createdProviders.single()
        drewNothing.onStateChange?.invoke(EmbeddedBlockState.Empty)
        assertEquals(EmbeddedBlockState.Empty, states.last())

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(2, createdProviders.size)
        assertEquals(1, drewNothing.releaseCount)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `provider factory crash degrades to failed instead of crashing the host`() {
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> error("factory boom") },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(internalError, states.last())
    }

    @Test
    fun `provider start crash degrades to failed instead of crashing the host`() {
        val crashingProvider = object : EmbeddedContentProvider {
            override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
            override val contentView: View? = View(RuntimeEnvironment.getApplication())

            override fun start(): Unit = error("start boom")

            override fun pause() = Unit

            override fun release() = Unit
        }
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, _ -> crashingProvider },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(internalError, states.last())
    }

    @Test
    fun `crash in the host state listener stays contained`() {
        val controller = controller()
        controller.onStateChange = { error("host listener boom") }

        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        // No assertion beyond survival: the listener crash is logged, never rethrown.
        assertEquals(listOf("main-screen-top"), blocksRegistry.appearedPlaces)
    }

    // ---- the place's hold in the show budgets: held while content is loading or shown, given back once the content is dropped ----

    @Test
    fun `a block holds content from the delivery until it shows, and lets go on Failed or Empty`() {
        val controller = controller()
        controller.start()
        assertTrue(controller.isHoldingContent) // Loading: the attempt is on

        blocksRegistry.pushContent("main-screen-top", content)
        assertTrue(controller.isHoldingContent) // Ready (FakeProvider reports Ready on start)
        assertEquals(emptyList<String>(), blocksRegistry.droppedPlaces)

        createdProviders.last().onStateChange?.invoke(networkError)
        assertFalse(controller.isHoldingContent)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `nothing to show drops the content for the place, and so does releasing the block`() {
        val controller = controller()
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(null)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)

        controller.release()
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `content arriving after the block gave up is dropped and the registry is told`() {
        val controller = controller(configTimeout = Milliseconds(30_000L))
        controller.start()
        idleFor(Duration.ofMillis(30_001L))
        val droppedByTimeout = blocksRegistry.droppedPlaces.size

        blocksRegistry.pushContent("main-screen-top", content)

        // The registry reserved for this delivery; the drop must hand the hold back.
        assertEquals(droppedByTimeout + 1, blocksRegistry.droppedPlaces.size)
        assertFalse(controller.isHoldingContent)
        assertEquals(0, createdProviders.size)
    }

    @Test
    fun `the SDK having no config fails the block with a network error at once and ships the place-named failure`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker) { _, _ -> FakeProvider() }
        controller.start()
        idleFor(Duration.ofMillis(250L))

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(networkError, states.last())
        verify(exactly = 1) {
            tracker.sendPlaceWaitBudgetExceeded(PlaceKey.of("main-screen-top"), Milliseconds(250L), WaitBudgetPhase.CONFIG_MISSING)
        }
        assertTrue(blocksRegistry.droppedPlaces.contains("main-screen-top"))

        // The answer settled the budget: the timeout does not report the place a second time.
        idleFor(Duration.ofMillis(30_001L))
        verify(exactly = 1) { tracker.sendPlaceWaitBudgetExceeded(any(), any(), any()) }
    }

    @Test
    fun `an answer the SDK could not give drops the content a block was showing`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        assertEquals(EmbeddedBlockState.Ready, states.last())

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(networkError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(null, controller.contentView)
    }

    @Test
    fun `an answer the SDK could not give after the block gave up is dropped`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(configTimeout = Milliseconds(50L), tracker = tracker) { _, _ -> FakeProvider() }
        controller.start()
        idleFor(Duration.ofMillis(51L))
        assertEquals(networkError, states.last())
        val reportedSoFar = states.size

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(reportedSoFar, states.size)
        verify(exactly = 1) { tracker.sendPlaceWaitBudgetExceeded(any(), any(), any()) }
    }

    @Test
    fun `a winner without a webview layer is an internal error with a report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker) { _, _ -> FakeProvider() }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content.copy(layers = emptyList(), tags = mapOf("a" to "b")))

        assertEquals(internalError, states.last())
        verify(exactly = 1) {
            tracker.sendFailure(
                inAppId = "embedded-id",
                failureReason = FailureReason.UNKNOWN_ERROR,
                errorDetails = any(),
                tags = mapOf("a" to "b"),
            )
        }
    }

    @Test
    fun `content that cannot be built is an internal error with a report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker) { _, _ -> null }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(internalError, states.last())
        verify(exactly = 1) {
            tracker.sendFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
        }
    }

    @Test
    fun `a page whose start crashes is an internal error with a report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val crashing = FakeProvider(contentView = null, onStart = { throw IllegalStateException("boom") })
        val controller = controller(tracker = tracker) { _, _ -> crashing }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(internalError, states.last())
        assertEquals(1, crashing.releaseCount)
        verify(exactly = 1) {
            tracker.sendFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
        }
    }

    @Test
    fun `a page whose resume crashes is an internal error with a report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val provider = FakeProvider(
            onStart = {
                if (startCount > 1) throw IllegalStateException("boom")
                onStateChange?.invoke(EmbeddedBlockState.Ready)
            },
        )
        val controller = controller(tracker = tracker) { _, _ -> provider }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        assertEquals(EmbeddedBlockState.Ready, states.last())

        controller.pause()
        controller.start()

        // The failure settles like every other one: the block asks afresh on its next appearance, not now.
        assertEquals(listOf(EmbeddedBlockState.Ready, internalError), states.takeLast(2))
        assertEquals(listOf("main-screen-top"), blocksRegistry.appearedPlaces)
        verify(exactly = 1) {
            tracker.sendFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
        }
    }

    @Test
    fun `ready content without a view is an internal error with a report`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val viewless = FakeProvider(contentView = null)
        val controller = controller(tracker = tracker) { _, _ -> viewless }
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content)

        assertEquals(internalError, states.last())
        assertTrue(states.none { state -> state is EmbeddedBlockState.Ready })
        assertEquals(1, viewless.releaseCount)
        assertEquals(null, controller.contentView)
        verify(exactly = 1) {
            tracker.sendFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
        }
    }

    @Test
    fun `the same failure reported twice is one state and a new reason is another`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        val provider = createdProviders.single()

        provider.onStateChange?.invoke(networkError)
        provider.onStateChange?.invoke(networkError)
        provider.onStateChange?.invoke(internalError)

        assertEquals(
            listOf(
                networkError,
                internalError,
            ),
            states.filterIsInstance<EmbeddedBlockState.Failed>(),
        )
    }

    @Test
    fun `an answer the SDK could not give reaches a paused block at once and the return asks afresh`() {
        val controller = controller()
        controller.start()
        controller.pause()

        blocksRegistry.lastHandle?.onConfigUnavailable()
        assertEquals(networkError, states.last())

        controller.start()

        assertEquals(EmbeddedBlockState.Loading, states.last())
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }
}
