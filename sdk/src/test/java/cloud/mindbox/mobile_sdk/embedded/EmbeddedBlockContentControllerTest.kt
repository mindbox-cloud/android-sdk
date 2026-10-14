package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.managers.LifecycleManager
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import android.os.Looper
import android.os.SystemClock
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
import org.robolectric.Robolectric
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

        var endedSessions = setOf<Long>()

        override fun isLiveSession(sessionEpoch: Long): Boolean = sessionEpoch !in endedSessions

        var isReturnCheckPending = false
        var areChannelsDead = false

        override fun deferUntilReturnChecked(): Boolean {
            if (areChannelsDead && !isReturnCheckPending) lastHandle?.onReturnChecked()
            return isReturnCheckPending
        }

        override fun onAppResumedOn(activity: Activity) = Unit

        val reservedShows = mutableListOf<Pair<String, Long>>()
        var reservation = PlaceShowReservation.RESERVED

        override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer): PlaceShowReservation {
            reservedShows.add(content.inAppId to answer.sessionEpoch)
            return reservation
        }

        val givenBackShows = mutableListOf<Pair<String, Long>>()

        override fun giveBackParkedShow(placeSystemName: PlaceKey, sessionEpoch: Long) {
            givenBackShows.add(placeSystemName.value to sessionEpoch)
        }

        override fun onReturnCheckOver() = Unit

        fun pushContent(
            placeSystemName: String,
            content: InAppType.Embedded,
            selectionTime: Milliseconds = Milliseconds(0L),
        ) {
            lastHandle?.onContentResolved(content, selectionTime)
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

    private open class ReadyUpdatableProvider : EmbeddedUpdatableContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View? = View(RuntimeEnvironment.getApplication())

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() = Unit

        override fun refreshForSession(
            params: Map<String, String>,
            sessionEpoch: Long,
            selectionTime: Milliseconds,
            onResult: (Boolean) -> Unit,
        ) = updateParams(params, onResult)

        override fun scheduleSessionRefresh(sessionEpoch: Long, selectionTime: Milliseconds) = Unit

        override fun confirmForSession(sessionEpoch: Long) = Unit

        override fun timeToDisplaySoFar(sessionEpoch: Long): Milliseconds? = null

        override fun withholdShow(isWithheld: Boolean) = Unit

        override val rendersNothing: Boolean = false

        override var onRendersNothing: (() -> Unit)? = null

        override fun refreshMetricsSnapshot(frequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency, tags: Map<String, String>?) = Unit

        override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) = onResult(true)
    }

    private val blocksRegistry = FakeBlocksRegistry()
    private val createdProviders = mutableListOf<FakeProvider>()
    private val states = mutableListOf<EmbeddedBlockState>()
    private val networkError = EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR)
    private val internalError = EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR)

    private var isAppInForeground = true

    private fun controller(
        configTimeout: Milliseconds = Milliseconds(30_000L),
        tracker: InAppFailureTracker? = null,
        monotonicNow: () -> Milliseconds = { Milliseconds(SystemClock.elapsedRealtime()) },
        hostActivity: () -> Activity? = { null },
        providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider? = { _, _ ->
            FakeProvider().also { createdProviders.add(it) }
        },
    ): EmbeddedBlockContentController =
        EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = configTimeout,
            providerFactory = providerFactory,
            blocksRegistry = { blocksRegistry },
            monotonicNow = monotonicNow,
            failureTracker = { tracker },
            isTagsFeatureEnabled = { true },
            isAppInForeground = { isAppInForeground },
            hostActivity = hostActivity,
        ).apply {
            onStateChange = { state -> states.add(state) }
        }

    private fun idleFor(duration: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
    }

    private val content = InAppStub.getEmbedded()

    private class SessionAwareProvider(private val rendersOnStart: Boolean = true) : ReadyUpdatableProvider() {
        val confirmedSessions = mutableListOf<Long>()
        val withheldShows = mutableListOf<Boolean>()
        val sessionRefreshes = mutableListOf<Long>()
        val sessionRefreshSelectionTimes = mutableListOf<Milliseconds>()
        val sessionRefreshParams = mutableListOf<Map<String, String>>()
        val scheduledSessionRefreshes = mutableListOf<Pair<Long, Milliseconds>>()
        val paramUpdates = mutableListOf<Map<String, String>>()
        var pushResult: ((Boolean) -> Unit)? = null
        var releaseCount = 0
        val timeToDisplayBySession = mutableMapOf<Long, Milliseconds>()
        override var rendersNothing = false
            set(value) {
                field = value
                if (value) onRendersNothing?.invoke()
            }

        override fun start() {
            if (rendersOnStart) render()
        }

        override fun release() {
            releaseCount++
        }

        override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
            paramUpdates.add(params)
            pushResult = onResult
        }

        var crashesOnSessionRefresh = false

        override fun refreshForSession(
            params: Map<String, String>,
            sessionEpoch: Long,
            selectionTime: Milliseconds,
            onResult: (Boolean) -> Unit,
        ) {
            sessionRefreshes.add(sessionEpoch)
            sessionRefreshSelectionTimes.add(selectionTime)
            sessionRefreshParams.add(params)
            check(!crashesOnSessionRefresh) { "the page crashed on the session refresh" }
            pushResult = onResult
        }

        override fun scheduleSessionRefresh(sessionEpoch: Long, selectionTime: Milliseconds) {
            scheduledSessionRefreshes.add(sessionEpoch to selectionTime)
        }

        override fun confirmForSession(sessionEpoch: Long) {
            confirmedSessions.add(sessionEpoch)
        }

        override fun timeToDisplaySoFar(sessionEpoch: Long): Milliseconds? = timeToDisplayBySession[sessionEpoch]

        override fun withholdShow(isWithheld: Boolean) {
            withheldShows.add(isWithheld)
        }

        fun render() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }
    }

    private fun sessionAwareController(
        providers: MutableList<SessionAwareProvider>,
        rendersOnStart: Boolean = true,
        monotonicNow: () -> Milliseconds = { Milliseconds(SystemClock.elapsedRealtime()) },
        selectionTimes: MutableList<Milliseconds> = mutableListOf(),
        hostActivity: () -> Activity? = { null },
        readyTimeout: Milliseconds = Milliseconds(7_000L),
    ) = EmbeddedBlockContentController(
        placeSystemName = "main-screen-top",
        configTimeout = Milliseconds(30_000L),
        readyTimeout = readyTimeout,
        providerFactory = { _, selectionTime ->
            selectionTimes.add(selectionTime)
            SessionAwareProvider(rendersOnStart).also { providers.add(it) }
        },
        blocksRegistry = { blocksRegistry },
        monotonicNow = monotonicNow,
        isAppInForeground = { isAppInForeground },
        hostActivity = hostActivity,
    ).apply { onStateChange = { state -> states.add(state) } }

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
    fun `a block back on screen before the campaign's delay ends builds its page with the selection time`() {
        var clock = Milliseconds(0L)
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = controller(
            monotonicNow = { clock },
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                FakeProvider()
            },
        )
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(null)
        blocksRegistry.lastHandle?.onContentPending()
        controller.pause()
        clock = Milliseconds(45_000L)
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(40L))

        assertEquals(listOf(Milliseconds(40L)), selectionTimes)
    }

    @Test
    fun `a page built for a collapsed block carries the selection time of the answer that revived it`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = controller(
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                FakeProvider()
            },
        )
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(null)
        assertEquals(EmbeddedBlockState.Empty, states.last())

        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(40L))

        assertEquals(listOf(Milliseconds(40L)), selectionTimes)
    }

    @Test
    fun `content deferred while the block is away is built with the selection time it came with`() {
        var clock = Milliseconds(0L)
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = controller(
            monotonicNow = { clock },
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                FakeProvider()
            },
        )
        controller.start()
        controller.pause()

        blocksRegistry.lastHandle?.onContentPending()
        clock = Milliseconds(20_000L)
        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(250L))
        clock = Milliseconds(45_000L)
        controller.start()

        assertEquals(listOf(Milliseconds(250L)), selectionTimes)
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
            tracker.sendBlockFailure(
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
    fun `each page is built with the selection time of its own answer`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = controller(
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                FakeProvider()
            },
        )
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(2_000L))
        controller.pause()
        controller.start()

        blocksRegistry.pushContent("main-screen-top", content.copy(inAppId = "another-winner"), selectionTime = Milliseconds(1_000L))

        assertEquals(listOf(Milliseconds(2_000L), Milliseconds(1_000L)), selectionTimes)
    }

    @Test
    fun `another page arriving while the first one still loads is built with its own selection time`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = controller(
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                FakeProvider(onStart = {})
            },
        )
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(50L))
        assertEquals(EmbeddedBlockState.Loading, states.last())

        val movedLayer = (content.layers.single() as Layer.WebViewLayer)
            .copy(contentUrl = "https://static.example/another-page.html")
        blocksRegistry.pushContent("main-screen-top", content.copy(layers = listOf(movedLayer)), selectionTime = Milliseconds(30L))

        assertEquals(listOf(Milliseconds(50L), Milliseconds(30L)), selectionTimes)
    }

    @Test
    fun `a page rebuilt after a failed in-place update carries the selection time of the answer that asked for it`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val controller = EmbeddedBlockContentController(
            placeSystemName = "main-screen-top",
            configTimeout = Milliseconds(30_000L),
            providerFactory = { _, selectionTime ->
                selectionTimes.add(selectionTime)
                object : ReadyUpdatableProvider() {
                    override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
                        onResult(false)
                    }
                }
            },
            blocksRegistry = { blocksRegistry },
        ).apply { onStateChange = { state -> states.add(state) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content, selectionTime = Milliseconds(100L))

        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer)
            .copy(params = mapOf("items" to "[]"))
        blocksRegistry.pushContent(
            "main-screen-top",
            content.copy(layers = listOf(refreshedLayer)),
            selectionTime = Milliseconds(700L),
        )
        idleFor(Duration.ZERO)

        assertEquals(listOf(Milliseconds(100L), Milliseconds(700L)), selectionTimes)
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
        val updatableProvider = object : ReadyUpdatableProvider() {
            override fun refreshMetricsSnapshot(frequency: cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency, tags: Map<String, String>?) {
                snapshotFrequency = frequency
                snapshotTags = tags
            }
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
        val updatableProvider = object : ReadyUpdatableProvider() {
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
        class RecordingUpdatableProvider : ReadyUpdatableProvider() {
            var updatedParams: Map<String, String>? = null

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
    fun `an answer the SDK could not give keeps the content a block is showing until the block leaves the screen`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker)
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        assertEquals(EmbeddedBlockState.Ready, states.last())

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        verify(exactly = 1) { tracker.sendPlaceWaitBudgetExceeded(any(), any(), WaitBudgetPhase.CONFIG_MISSING) }

        controller.pause()

        assertEquals(networkError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(null, controller.contentView)
    }

    @Test
    fun `an answer the SDK could not give to an operation drops the content a block was showing at once`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        blocksRegistry.lastHandle?.onConfigUnavailable(placeAnswer(isByOperation = true))

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
            tracker.sendBlockFailure(
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
            tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
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
            tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
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
            tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
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
            tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any())
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
    fun `an answer the SDK could not give to a paused block waits for its return, then fails it and asks afresh`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()

        blocksRegistry.lastHandle?.onConfigUnavailable()
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(networkError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `nothing to show for a block showing content keeps it until the block leaves the screen`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        assertTrue(controller.contentView != null)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `an operation that leaves the place empty collapses a shown block at once`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(isByOperation = true))

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `nothing to show for a block that has not shown content yet collapses it at once`() {
        val controller = controller { _, _ -> FakeProvider(onStart = {}).also { createdProviders.add(it) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        assertEquals(EmbeddedBlockState.Loading, states.last())

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `nothing to show parked while the block was away collapses it at once on return`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(null, controller.contentView)
    }

    @Test
    fun `a block holding a collapse is not kept for its screen`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        assertTrue(controller.isRetainable)

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        assertFalse(controller.isRetainable)
    }

    @Test
    fun `content arriving while a collapse is held cancels the collapse`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        controller.pause()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        assertTrue(controller.isRetainable)
    }

    @Test
    fun `a data push that fails after the place became empty does not rebuild the content`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]"))
        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = listOf(refreshedLayer)), placeAnswer())
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        providers.single().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, providers.size)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `content is confirmed for the session whose answer built it`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 2L))

        assertEquals(listOf(2L), providers.single().confirmedSessions)
    }

    @Test
    fun `a new session's same content refreshes the page in place once, with the same params`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertEquals(1, providers.size)
        assertEquals(listOf(1L), providers.single().sessionRefreshes)
        assertEquals(listOf((content.layers.single() as Layer.WebViewLayer).params), providers.single().sessionRefreshParams)
        assertTrue(providers.single().paramUpdates.isEmpty())
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `the same content again within its session does not refresh the page`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        controller.pause()
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertTrue(providers.single().sessionRefreshes.isEmpty())
        assertTrue(providers.single().paramUpdates.isEmpty())
    }

    @Test
    fun `a new session's content with new params refreshes the page for the session`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]"))

        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = listOf(refreshedLayer)), placeAnswer(sessionEpoch = 1L))

        assertEquals(listOf(1L), providers.single().sessionRefreshes)
        assertEquals(listOf(mapOf("items" to "[]")), providers.single().sessionRefreshParams)
        assertTrue(providers.single().paramUpdates.isEmpty())
    }

    @Test
    fun `new params for a page still loading rebuild it instead of updating it in place`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]"))

        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = listOf(refreshedLayer)), placeAnswer(sessionEpoch = 0L))

        assertEquals(2, providers.size)
        assertEquals(1, providers.first().releaseCount)
        assertTrue(providers.first().paramUpdates.isEmpty())
    }

    @Test
    fun `a page still loading when a new session picks it again is refreshed once it renders`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        assertTrue(providers.single().sessionRefreshes.isEmpty())

        providers.single().render()

        assertEquals(1, providers.size)
        assertEquals(listOf(1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `a page still loading when a new session picks it again is told at the answer to time that session's show from it`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))

        assertEquals(listOf(1L to Milliseconds(300L)), providers.single().scheduledSessionRefreshes)
        assertTrue(providers.single().sessionRefreshes.isEmpty())
    }

    @Test
    fun `a new session's refresh due while the page loads waits for the session check of the app's return`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        isAppInForeground = false
        controller.pause()
        providers.single().render()
        isAppInForeground = true
        blocksRegistry.isReturnCheckPending = true

        controller.start()
        providers.single().render()

        assertTrue(providers.single().sessionRefreshes.isEmpty())

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(listOf(1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `a new session's refresh due while the page loads is not sent to a page whose collapse is held, and goes once content clears the hold`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.isReturnCheckPending = true
        providers.single().render()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 1L))

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertTrue(providers.single().sessionRefreshes.isEmpty())

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertEquals(listOf(1L), providers.single().sessionRefreshes)
        assertEquals(false, providers.single().withheldShows.last())
    }

    @Test
    fun `a new session's refresh due while the page loads is sent once when asking about the return check releases the checked blocks on the way`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.areChannelsDead = true

        providers.single().render()

        assertEquals(listOf(1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `a new session's refresh due while the page loads is dropped when the session check of the app's return ends that session`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        isAppInForeground = false
        controller.pause()
        providers.single().render()
        isAppInForeground = true
        blocksRegistry.isReturnCheckPending = true
        controller.start()
        providers.single().render()

        blocksRegistry.endedSessions = setOf(0L, 1L)
        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertTrue(providers.single().sessionRefreshes.isEmpty())

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 2L))

        assertEquals(listOf(2L), providers.single().sessionRefreshes)
        assertEquals(0, providers.single().releaseCount)
    }

    @Test
    fun `a new session's refresh due while the page loads is dropped when that session ended before the page rendered`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.endedSessions = setOf(0L, 1L)

        providers.single().render()

        assertTrue(providers.single().sessionRefreshes.isEmpty())
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a new session's refresh that the page could not take rebuilds the page for that session`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        providers.first().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, providers.size)
        assertEquals(listOf(1L), providers.last().confirmedSessions)
    }

    @Test
    fun `a held collapse withholds the page's show and content that clears it lets the show go`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 1L))
        assertEquals(true, providers.single().withheldShows.last())

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertEquals(false, providers.single().withheldShows.last())
        assertEquals(1, providers.size)
    }

    @Test
    fun `a different winner clearing a held collapse never lets the replaced page's show go`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 1L))

        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 1L))

        assertEquals(2, providers.size)
        assertEquals(listOf(true), providers.first().withheldShows)
        assertEquals(1, providers.first().releaseCount)
    }

    @Test
    fun `a winner without a webview layer keeps the content a block is showing until the block leaves the screen`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker)
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)

        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = emptyList()), placeAnswer())

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        verify(exactly = 1) { tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any()) }

        controller.pause()

        assertEquals(internalError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `nothing to show parked in a session that has ended keeps the content and lets the place answer again`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.endedSessions = setOf(0L)

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `another winner parked in a session that has ended does not rebuild the page`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 0L))
        blocksRegistry.endedSessions = setOf(0L)

        controller.start()

        assertEquals(1, createdProviders.size)
        assertEquals(0, createdProviders.single().releaseCount)
    }

    @Test
    fun `an operation's answer in a new session refreshes the page once like any other`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, isByOperation = true))
        assertEquals(listOf(1L), providers.single().sessionRefreshes)

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertEquals(listOf(1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `a session refresh the page could not take while a collapse is held is sent again once content clears the hold`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 1L))

        providers.single().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertEquals(1, providers.size)
        assertEquals(listOf(1L, 1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `a collapse held when the app goes to background survives it and the block returns still holding it`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        isAppInForeground = false
        controller.pause()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(true), providers.single().withheldShows)

        isAppInForeground = true
        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(true), providers.single().withheldShows)
        assertFalse(controller.isRetainable)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
    }

    @Test
    fun `a collapse held through the background applies once the block leaves the screen inside the app`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())
        isAppInForeground = false
        controller.pause()
        isAppInForeground = true
        controller.start()

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `nothing to show that reached a block away in background is held on its return`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())
        isAppInForeground = true

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        assertTrue(controller.contentView != null)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `an answer the SDK could not give that reached a block away in background is held on its return`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onConfigUnavailable()
        isAppInForeground = true

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)

        controller.pause()

        assertEquals(networkError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `a block away only because the app went to background tells the registry so until it returns`() {
        val controller = controller()
        controller.start()
        val handle = blocksRegistry.lastHandle!!
        assertFalse(handle.isPausedForBackground)

        isAppInForeground = false
        controller.pause()
        assertTrue(handle.isPausedForBackground)

        controller.start()
        assertFalse(handle.isPausedForBackground)

        isAppInForeground = true
        controller.pause()
        assertFalse(handle.isPausedForBackground)
    }

    @Test
    fun `a winner without a page that reaches a block away reports its failure at once and not again on its return`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker)
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()

        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = emptyList()), placeAnswer())

        verify(exactly = 1) { tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any()) }
        val reportedBeforeReturn = states.size

        controller.start()

        verify(exactly = 1) { tracker.sendBlockFailure(inAppId = "embedded-id", failureReason = FailureReason.UNKNOWN_ERROR, errorDetails = any(), tags = any()) }
        assertEquals(listOf(internalError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(1, createdProviders.single().startCount)
        assertEquals(null, controller.contentView)
    }

    @Test
    fun `an answer the SDK could not give to a paused block that has not shown content waits for its return, then fails it and asks afresh`() {
        val controller = controller { _, _ -> FakeProvider(onStart = {}).also { createdProviders.add(it) } }
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        controller.pause()

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(EmbeddedBlockState.Loading, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(networkError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `a page rebuilt after its new session's refresh failed carries the new session's selection time`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L, selectionTime = Milliseconds(100L)))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(400L)))

        providers.first().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, providers.size)
        assertEquals(listOf(Milliseconds(100L), Milliseconds(400L)), selectionTimes)
    }

    @Test
    fun `a page rebuilt after its new session's refresh failed carries the time the failed page counted for that session`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L, selectionTime = Milliseconds(100L)))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(400L)))
        providers.first().timeToDisplayBySession[1L] = Milliseconds(7_400L)

        providers.first().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, providers.size)
        assertEquals(listOf(Milliseconds(100L), Milliseconds(7_400L)), selectionTimes)
    }

    @Test
    fun `a page rebuilt after its new session's refresh crashed carries the time the failed page counted for that session`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L, selectionTime = Milliseconds(100L)))
        providers.first().timeToDisplayBySession[1L] = Milliseconds(450L)
        providers.first().crashesOnSessionRefresh = true

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(400L)))

        assertEquals(2, providers.size)
        assertEquals(listOf(Milliseconds(100L), Milliseconds(450L)), selectionTimes)
    }

    @Test
    fun `a page rebuilt after an update within its session failed carries only the selection time of the answer that asked for it`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L, selectionTime = Milliseconds(100L)))
        providers.first().timeToDisplayBySession[0L] = Milliseconds(9_000L)
        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]"))
        blocksRegistry.lastHandle?.onContentResolved(
            content.copy(layers = listOf(refreshedLayer)),
            placeAnswer(sessionEpoch = 0L, selectionTime = Milliseconds(50L)),
        )

        providers.first().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, providers.size)
        assertEquals(listOf(Milliseconds(100L), Milliseconds(50L)), selectionTimes)
    }

    private val blockScreen: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val otherScreen: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun controllerHoldingThroughBackground(providers: MutableList<SessionAwareProvider>): EmbeddedBlockContentController {
        val controller = sessionAwareController(providers, hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())
        isAppInForeground = false
        controller.pause()
        return controller
    }

    @Test
    fun `a collapse held through the background applies as soon as the app comes back on another screen, and the block returns collapsed`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerHoldingThroughBackground(providers)

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
        assertFalse(controller.isPausedForBackground)

        isAppInForeground = true
        val reportedBeforeReturn = states.size
        controller.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `a collapse held through the background survives the app coming back on the block's own screen`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerHoldingThroughBackground(providers)

        controller.onAppResumedOn(blockScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(controller.isPausedForBackground)

        isAppInForeground = true
        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
    }

    @Test
    fun `a block whose screen is unknown keeps its background pause when the app comes back`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())
        isAppInForeground = false
        controller.pause()

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertTrue(controller.isPausedForBackground)
    }

    @Test
    fun `nothing to show that reached a block away in background collapses it as soon as the app comes back on another screen, and the block returns collapsed`() {
        val controller = controller(hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)

        isAppInForeground = true
        val reportedBeforeReturn = states.size
        controller.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `an answer the SDK could not give that reached a block away in background fails it as soon as the app comes back on another screen, and the block returns asking afresh`() {
        val controller = controller(hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onConfigUnavailable()

        controller.onAppResumedOn(otherScreen)

        assertEquals(networkError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)

        isAppInForeground = true
        val reportedBeforeReturn = states.size
        controller.start()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `an answer the SDK could not give that reached a block holding a collapse through the background is the only outcome the app's return on another screen reports`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerHoldingThroughBackground(providers)
        blocksRegistry.lastHandle?.onConfigUnavailable()
        val reportedBeforeLeaving = states.size

        controller.onAppResumedOn(otherScreen)

        assertEquals(listOf<EmbeddedBlockState>(networkError), states.drop(reportedBeforeLeaving))
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `content that reached a block away in background over a held collapse cancels it, so the app coming back on another screen keeps the block and its return keeps the page in place`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerHoldingThroughBackground(providers)
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
        assertEquals(listOf(true), providers.single().withheldShows)

        isAppInForeground = true
        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, providers.size)
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(true, false), providers.single().withheldShows)
        assertEquals(listOf(content.inAppId to 0L), blocksRegistry.reservedShows)
    }

    @Test
    fun `a block whose held collapse the app's return on another screen applied is not left behind for a new session`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerHoldingThroughBackground(providers)

        controller.onAppResumedOn(otherScreen)
        controller.onSessionRenewed()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertFalse(controller.isLeftBehind)
    }

    @Test
    fun `a block whose parked refusal the app's return on another screen applied is not left behind for a new session`() {
        val controller = controller(hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        controller.onAppResumedOn(otherScreen)
        controller.onSessionRenewed()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertFalse(controller.isLeftBehind)
    }

    @Test
    fun `a block whose page rendered nothing and that the app's return on another screen collapsed is not left behind for a new session`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithEmptiedPageThroughBackground(providers)

        controller.onAppResumedOn(otherScreen)
        controller.onSessionRenewed()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertFalse(controller.isLeftBehind)
    }

    private fun controllerWithEmptiedPageThroughBackground(providers: MutableList<SessionAwareProvider>): EmbeddedBlockContentController {
        val controller = controllerShowingPage(providers, hostActivity = { blockScreen })
        isAppInForeground = false
        controller.pause()
        providers.single().rendersNothing = true
        return controller
    }

    @Test
    fun `a new session's content parked over a page that rendered nothing in background keeps the block when the app comes back on another screen, and its return refreshes the page in place`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithEmptiedPageThroughBackground(providers)
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
        assertTrue(controller.isLeftBehind)
        assertEquals(listOf(true), providers.single().withheldShows)
        assertEquals(listOf("main-screen-top" to 1L), blocksRegistry.givenBackShows)

        isAppInForeground = true
        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, providers.size)
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(1L), providers.single().sessionRefreshes)
        assertEquals(listOf(true, false), providers.single().withheldShows)
        assertEquals(listOf(content.inAppId to 1L), blocksRegistry.reservedShows)
    }

    @Test
    fun `another in-app's content parked over a page that rendered nothing in background keeps the block when the app comes back on another screen, and its return builds the new page`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithEmptiedPageThroughBackground(providers)
        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer())

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
        assertEquals(listOf(true), providers.single().withheldShows)

        isAppInForeground = true
        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(2, providers.size)
        assertEquals(1, providers.first().releaseCount)
        assertEquals(listOf("another-winner" to 0L), blocksRegistry.reservedShows)
    }

    @Test
    fun `a page that renders nothing in background after content was parked for its block keeps the block when the app comes back on another screen and withholds its show`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers, hostActivity = { blockScreen })
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))
        providers.single().rendersNothing = true

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(true), providers.single().withheldShows)
    }

    private fun controllerShowingPage(
        providers: MutableList<SessionAwareProvider>,
        hostActivity: () -> Activity? = { null },
    ): EmbeddedBlockContentController {
        val controller = sessionAwareController(providers, hostActivity = hostActivity)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        return controller
    }

    @Test
    fun `a page that renders nothing while its block is shown keeps the block until it leaves the screen`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers)

        providers.single().rendersNothing = true

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `a page that renders nothing and then fails reports only the failure when its block leaves the screen`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers)
        providers.single().rendersNothing = true

        providers.single().onStateChange?.invoke(internalError)
        controller.pause()

        assertEquals(internalError, states.last())
        assertFalse(EmbeddedBlockState.Empty in states)
    }

    @Test
    fun `a block whose page renders nothing is not kept for its screen`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers)
        assertTrue(controller.isRetainable)

        providers.single().rendersNothing = true

        assertFalse(controller.isRetainable)
    }

    @Test
    fun `a block whose page rendered nothing in background is kept for its screen once content is parked for it`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithEmptiedPageThroughBackground(providers)
        assertFalse(controller.isRetainable)

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        assertTrue(controller.isRetainable)
        assertEquals(null, controller.heldOutcome)
    }

    @Test
    fun `a page that renders nothing keeps its block through the background, and the block collapses as soon as the app comes back on another screen`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers, hostActivity = { blockScreen })
        providers.single().rendersNothing = true
        isAppInForeground = false
        controller.pause()

        assertEquals(EmbeddedBlockState.Ready, states.last())

        controller.onAppResumedOn(otherScreen)

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `a page that rendered nothing while the app was in background keeps its block on the return to its own screen until the block leaves it`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers, hostActivity = { blockScreen })
        isAppInForeground = false
        controller.pause()
        providers.single().rendersNothing = true
        controller.onAppResumedOn(blockScreen)
        isAppInForeground = true

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
    }

    @Test
    fun `a page that renders nothing after its block left the screen collapses the block at once and gives its place up`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers)
        controller.pause()

        providers.single().rendersNothing = true

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
        assertEquals(listOf("main-screen-top"), blocksRegistry.droppedPlaces)
    }

    @Test
    fun `a page that renders nothing after its block left the screen keeps the block while content is parked for it, and the block's return refreshes the page in place`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerShowingPage(providers)
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L))

        providers.single().rendersNothing = true

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(blocksRegistry.droppedPlaces.isEmpty())
        assertEquals(listOf(true), providers.single().withheldShows)

        controller.start()

        assertEquals(1, providers.size)
        assertEquals(listOf(1L), providers.single().sessionRefreshes)
        assertEquals(listOf(true, false), providers.single().withheldShows)
    }

    @Test
    fun `a data push that fails while the page renders nothing does not rebuild the page`() {
        val providers = mutableListOf<SessionAwareProvider>()
        controllerShowingPage(providers)
        providers.single().rendersNothing = true
        val refreshedLayer = (content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]"))
        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = listOf(refreshedLayer)), placeAnswer())

        providers.single().pushResult?.invoke(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, providers.size)
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a block that left the screen when the app came back on another one tells the registry so until it starts, and not once it leaves again`() {
        val controller = controller(hostActivity = { blockScreen })
        controller.start()
        val handle = blocksRegistry.lastHandle!!
        isAppInForeground = false
        controller.pause()
        assertFalse(handle.isLeftBehind)

        controller.onAppResumedOn(otherScreen)

        assertTrue(handle.isLeftBehind)
        assertFalse(handle.isPausedForBackground)

        isAppInForeground = true
        controller.start()
        controller.pause()

        assertFalse(handle.isLeftBehind)
    }

    private fun controllerLeftOnAppReturn(
        providers: MutableList<SessionAwareProvider>,
        rendersOnStart: Boolean = true,
        selectionTimes: MutableList<Milliseconds> = mutableListOf(),
    ): EmbeddedBlockContentController {
        val controller = sessionAwareController(
            providers,
            rendersOnStart = rendersOnStart,
            selectionTimes = selectionTimes,
            hostActivity = { blockScreen },
        )
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        isAppInForeground = false
        controller.pause()
        controller.onAppResumedOn(otherScreen)
        isAppInForeground = true
        return controller
    }

    @Test
    fun `a block the app's return left on another screen gives back the show reserved for the answer parked for it and reserves it again once it is back`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 0L))

        controller.onAppResumedOn(otherScreen)
        isAppInForeground = true

        assertEquals(listOf("main-screen-top" to 0L), blocksRegistry.givenBackShows)
        assertTrue(blocksRegistry.reservedShows.isEmpty())

        controller.start()

        assertEquals(listOf("another-winner" to 0L), blocksRegistry.reservedShows)
    }

    @Test
    fun `a block back on its screen after the app came back on another one in the same session keeps its page`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerLeftOnAppReturn(providers)

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
    }

    @Test
    fun `a block back on its screen after the app came back on another one drops an answer parked in the ended session and holds the new session's refusal on its kept page`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, hostActivity = { blockScreen })
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.endedSessions = setOf(0L)
        controller.onAppResumedOn(otherScreen)
        blocksRegistry.isReturnCheckPending = true
        isAppInForeground = true

        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = 1L))
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertEquals(listOf(true), providers.single().withheldShows)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, providers.single().releaseCount)
    }

    @Test
    fun `a page still loading when the app came back on another screen is kept and takes the new session's data and selection time once it renders`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerLeftOnAppReturn(providers, rendersOnStart = false)
        blocksRegistry.endedSessions = setOf(0L)

        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))

        assertTrue(providers.single().sessionRefreshes.isEmpty())

        providers.single().render()

        assertEquals(listOf(Milliseconds(300L)), providers.single().sessionRefreshSelectionTimes)
        assertEquals(0, providers.single().releaseCount)
    }

    @Test
    fun `a feed back on its screen before the new session answers gives way to the new session's other page built with its selection time`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerLeftOnAppReturn(providers, selectionTimes = selectionTimes)
        blocksRegistry.endedSessions = setOf(0L)

        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))

        assertEquals(listOf(Milliseconds(0L), Milliseconds(300L)), selectionTimes)
        assertEquals(1, providers.first().releaseCount)
    }

    @Test
    fun `a page still loading when the app came back on another screen gives way to the new session's other page parked for it, built with the selection time it came with`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerLeftOnAppReturn(providers, rendersOnStart = false, selectionTimes = selectionTimes)
        blocksRegistry.lastHandle?.onContentResolved(
            content.copy(inAppId = "another-winner"),
            placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)).copy(isShowReserved = false),
        )
        blocksRegistry.endedSessions = setOf(0L)

        controller.start()

        assertEquals(listOf(Milliseconds(0L), Milliseconds(300L)), selectionTimes)
    }

    @Test
    fun `a page still loading when the app came back on another screen has only the rest of its page budget on the block's return`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false, hostActivity = { blockScreen }, readyTimeout = Milliseconds(7_000L))
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        idleFor(Duration.ofMillis(6_500L))
        isAppInForeground = false
        controller.pause()
        controller.onAppResumedOn(otherScreen)
        isAppInForeground = true
        assertTrue(controller.isLeftBehind)

        controller.start()
        idleFor(Duration.ofMillis(400L))

        assertEquals(EmbeddedBlockState.Loading, states.last())

        idleFor(Duration.ofMillis(200L))

        assertTrue(states.last() is EmbeddedBlockState.Failed)
    }

    @Test
    fun `a new session's answer parked while the block was away refreshes the page with the selection time it came with`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))

        controller.start()

        assertEquals(listOf(Milliseconds(300L)), providers.single().sessionRefreshSelectionTimes)
    }

    @Test
    fun `a new session's answers parked one after another while the block is away keep the selection time of the first`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))
        blocksRegistry.lastHandle?.onConfigUnavailable(placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(20L)))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(5L)))

        controller.start()

        assertEquals(listOf(Milliseconds(300L)), providers.single().sessionRefreshSelectionTimes)
    }

    @Test
    fun `a new session's answer for another in-app parked after the first while the block is away builds its page with its own selection time`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))
        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(5L)))

        controller.start()

        assertEquals(listOf(Milliseconds(0L), Milliseconds(5L)), selectionTimes)
    }

    private fun controllerWithUnreservedContentParked(providers: MutableList<SessionAwareProvider>): EmbeddedBlockContentController {
        val controller = sessionAwareController(providers)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L).copy(isShowReserved = false))
        return controller
    }

    @Test
    fun `content that reached a block away from the screen without a show reservation reserves it when the block returns`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithUnreservedContentParked(providers)
        assertTrue(blocksRegistry.reservedShows.isEmpty())

        controller.start()

        assertEquals(listOf(content.inAppId to 1L), blocksRegistry.reservedShows)
        assertEquals(listOf(1L), providers.single().sessionRefreshes)
    }

    @Test
    fun `content that reached a block away from the screen without a show reservation collapses it before it shows when the show budgets are spent`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithUnreservedContentParked(providers)
        blocksRegistry.reservation = PlaceShowReservation.REFUSED
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, providers.single().releaseCount)
        assertTrue(providers.single().sessionRefreshes.isEmpty())
    }

    @Test
    fun `content that reached a block away from the screen without a show reservation is dropped when its session ends before the reservation`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = controllerWithUnreservedContentParked(providers)
        blocksRegistry.reservation = PlaceShowReservation.STALE

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, providers.single().releaseCount)
        assertTrue(providers.single().sessionRefreshes.isEmpty())
    }

    @Test
    fun `a block paused while another screen of the app stays started has left the screen`() {
        val lifecycle = LifecycleManager(currentActivityName = null, currentIntent = null, isAppInBackground = false)
        lifecycle.onActivityStarted(blockScreen)
        lifecycle.onActivityResumed(blockScreen)
        lifecycle.onActivityStarted(otherScreen)
        LifecycleManager.instance = lifecycle
        try {
            val controller = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> FakeProvider().also { createdProviders.add(it) } },
                blocksRegistry = { blocksRegistry },
                hostActivity = { blockScreen },
            ).apply { onStateChange = { state -> states.add(state) } }
            controller.start()
            blocksRegistry.pushContent("main-screen-top", content)
            blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

            lifecycle.onActivityPaused(blockScreen)
            controller.pause()

            assertEquals(EmbeddedBlockState.Empty, states.last())
            assertEquals(1, createdProviders.single().releaseCount)
        } finally {
            LifecycleManager.instance = null
        }
    }

    @Test
    fun `a winner without a page that reached a block away in background is held on its return`() {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = false
        controller.pause()
        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = emptyList()), placeAnswer())
        isAppInForeground = true

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)

        controller.pause()

        assertEquals(internalError, states.last())
        assertEquals(1, createdProviders.single().releaseCount)
    }

    private fun controllerAwayWithAnswerParked(
        isInBackground: Boolean = false,
        parkAnswer: EmbeddedBlockHandle.() -> Unit,
    ): EmbeddedBlockContentController {
        val controller = controller()
        controller.start()
        blocksRegistry.pushContent("main-screen-top", content)
        isAppInForeground = !isInBackground
        controller.pause()
        blocksRegistry.lastHandle?.parkAnswer()
        isAppInForeground = true
        blocksRegistry.isReturnCheckPending = true
        return controller
    }

    @Test
    fun `nothing to show parked while the block was away collapses it at once on a return before the session check of the app's return`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(null, placeAnswer()) }
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `an answer the SDK could not give parked while the block was away fails it at once on a return before the session check of the app's return`() {
        val controller = controllerAwayWithAnswerParked { onConfigUnavailable() }
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(networkError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `a winner without a page parked while the block was away fails it at once on a return before the session check of the app's return`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(layers = emptyList()), placeAnswer()) }
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(internalError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(1, createdProviders.single().releaseCount)
    }

    @Test
    fun `nothing to show parked through the background is held at once on a return before the session check of the app's return`() {
        val controller = controllerAwayWithAnswerParked(isInBackground = true) { onContentResolved(null, placeAnswer()) }

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(EmbeddedBlockState.Empty, controller.heldOutcome)
        assertEquals(0, createdProviders.single().releaseCount)
    }

    @Test
    fun `a block back before the session check of the app's return keeps the content parked for it until the check has run`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer()) }

        controller.start()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, createdProviders.size)

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(2, createdProviders.size)
        assertEquals(1, createdProviders.first().releaseCount)
    }

    @Test
    fun `content that reaches a block waiting for the session check of the return takes the place of the content parked for it`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer()) }
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer())
        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
        assertEquals(1, createdProviders.size)
    }

    @Test
    fun `nothing to show that reaches a block waiting for the session check of the return is held at once and drops the content parked for it`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer()) }
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer())

        assertEquals(EmbeddedBlockState.Empty, controller.heldOutcome)

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, createdProviders.size)

        controller.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, createdProviders.single().releaseCount)

        controller.start()

        assertEquals(EmbeddedBlockState.Loading, states.last())
        assertEquals(1, createdProviders.size)
    }

    @Test
    fun `an answer the SDK could not give that reaches a block waiting for the session check of the return is held at once and drops the content parked for it`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer()) }
        controller.start()

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(networkError, controller.heldOutcome)

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, createdProviders.size)
    }

    @Test
    fun `a winner without a page that reaches a block waiting for the session check of the return is held at once and drops the content parked for it`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer()) }
        controller.start()

        blocksRegistry.lastHandle?.onContentResolved(content.copy(layers = emptyList()), placeAnswer())

        assertEquals(internalError, controller.heldOutcome)

        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(1, createdProviders.size)
    }

    @Test
    fun `content kept for the session check of the return is dropped when the check ends its session`() {
        val controller = controllerAwayWithAnswerParked { onContentResolved(content.copy(inAppId = "another-winner"), placeAnswer(sessionEpoch = 0L)) }
        controller.start()

        blocksRegistry.endedSessions = setOf(0L)
        blocksRegistry.isReturnCheckPending = false
        controller.onReturnChecked()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, createdProviders.single().releaseCount)
    }

    @Test
    fun `a new session's refresh due while the page loads keeps the selection time of the session's first answer`() {
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, rendersOnStart = false)
        controller.start()
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 0L))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)))
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(20L)))

        providers.single().render()

        assertEquals(listOf(Milliseconds(300L)), providers.single().sessionRefreshSelectionTimes)
    }

    @Test
    fun `an answer the SDK could not give to a block paused before any answer fails it on its return and asks afresh`() {
        val tracker = mockk<InAppFailureTracker>(relaxed = true)
        val controller = controller(tracker = tracker)
        controller.start()
        controller.pause()

        blocksRegistry.lastHandle?.onConfigUnavailable()

        assertEquals(EmbeddedBlockState.Loading, states.last())
        verify(exactly = 1) {
            tracker.sendPlaceWaitBudgetExceeded(PlaceKey.of("main-screen-top"), any(), WaitBudgetPhase.CONFIG_MISSING)
        }
        val reportedBeforeReturn = states.size

        controller.start()

        assertEquals(listOf(networkError, EmbeddedBlockState.Loading), states.drop(reportedBeforeReturn))
        assertEquals(listOf("main-screen-top", "main-screen-top"), blocksRegistry.appearedPlaces)
    }

    @Test
    fun `a new session leaves behind a block that left its screen with its page until the block comes back`() {
        val controller = controller()
        controller.start()
        controller.onContentResolved(content, placeAnswer())
        controller.pause()

        controller.onSessionRenewed()

        assertTrue(controller.isLeftBehind)
        assertFalse(controller.isPausedForBackground)

        controller.start()
        controller.pause()

        assertFalse(controller.isLeftBehind)
    }

    @Test
    fun `a new session leaves behind a block that left its screen while waiting for its first answer`() {
        val controller = controller()
        controller.start()
        controller.pause()

        controller.onSessionRenewed()

        assertTrue(controller.isLeftBehind)
    }

    @Test
    fun `a new session leaves behind no block on screen, away in background or collapsed`() {
        val onScreen = controller()
        onScreen.start()
        onScreen.onContentResolved(content, placeAnswer())
        val inBackground = controller()
        inBackground.start()
        inBackground.onContentResolved(content, placeAnswer())
        isAppInForeground = false
        inBackground.pause()
        isAppInForeground = true
        val collapsed = controller()
        collapsed.start()
        collapsed.onContentResolved(null, placeAnswer())
        collapsed.pause()

        listOf(onScreen, inBackground, collapsed).forEach { block -> block.onSessionRenewed() }
        onScreen.pause()

        assertFalse(onScreen.isLeftBehind)
        assertFalse(inBackground.isLeftBehind)
        assertFalse(collapsed.isLeftBehind)
    }

    @Test
    fun `a new session leaves behind no collapsed block whose only parked answer is nothing to show`() {
        val controller = controller()
        controller.start()
        controller.onContentResolved(null, placeAnswer())
        controller.pause()
        controller.onContentResolved(null, placeAnswer())

        controller.onSessionRenewed()

        assertFalse(controller.isHoldingContent)
        assertFalse(controller.isLeftBehind)
    }

    @Test
    fun `a block that left its screen waiting for its first answer builds the page a new session parked for it with the selection time it came with and reserves its show on its return`() {
        val selectionTimes = mutableListOf<Milliseconds>()
        val providers = mutableListOf<SessionAwareProvider>()
        val controller = sessionAwareController(providers, selectionTimes = selectionTimes)
        controller.start()
        controller.pause()
        controller.onSessionRenewed()
        controller.onContentResolved(content, placeAnswer(sessionEpoch = 1L, selectionTime = Milliseconds(300L)).copy(isShowReserved = false))
        assertTrue(blocksRegistry.reservedShows.isEmpty())

        controller.start()

        assertEquals(listOf(Milliseconds(300L)), selectionTimes)
        assertEquals(listOf(content.inAppId to 1L), blocksRegistry.reservedShows)
    }
}
