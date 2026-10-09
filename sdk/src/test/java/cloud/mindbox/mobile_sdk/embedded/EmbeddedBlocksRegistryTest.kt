package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PlaceKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * The central controller is deliberately dumb: a registry and a router. Both directions call
 * the same interactor code — no selection filter is ever invoked from here.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlocksRegistryTest {

    private class RecordingHandle(
        override var isActive: Boolean = true,
        override var isPausedForBackground: Boolean = false,
        override var isLeftBehind: Boolean = false,
    ) : EmbeddedBlockHandle {
        override val isHoldingContent: Boolean = false
        val received = mutableListOf<InAppType.Embedded?>()
        val answers = mutableListOf<EmbeddedPlaceAnswer>()
        var pendingCount = 0
        var unavailableCount = 0

        override fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
            received.add(content)
            answers.add(answer)
        }

        override fun onContentPending() {
            pendingCount++
        }

        override fun onConfigUnavailable(answer: EmbeddedPlaceAnswer) {
            unavailableCount++
            answers.add(answer)
        }

        val resumedOn = mutableListOf<Activity>()
        var returnCheckedCount = 0
        var leavesOnAppResumed = false

        override fun onAppResumedOn(activity: Activity) {
            resumedOn.add(activity)
            if (leavesOnAppResumed) {
                isPausedForBackground = false
                isLeftBehind = true
            }
        }

        override fun onReturnChecked() {
            returnCheckedCount++
        }

        var leavesOnSessionRenewed = false

        override fun onSessionRenewed() {
            if (leavesOnSessionRenewed) isLeftBehind = true
        }
    }

    private val interactor: InAppInteractor = mockk(relaxed = true)

    // A var, like the SDK scope it stands for: soft re-initialization cancels it and puts a fresh
    // one in its place.
    private var scope = TestScope(UnconfinedTestDispatcher())
    private var workerScope: CoroutineScope? = null

    private val place = PlaceKey.of("main-screen-top")
    private val content = InAppStub.getEmbedded()

    private fun winner(variant: InAppType.Embedded = content, delay: Milliseconds? = null): EmbeddedResolveOutcome.Content =
        EmbeddedResolveOutcome.Content(variant, delay)

    private val placeEvents = MutableSharedFlow<EmbeddedPlaceEvent>()
    private val configUpdates = MutableSharedFlow<Unit>()
    private val sessionEpochs = MutableStateFlow(0L)
    private val returnCheckPending = MutableStateFlow(false)

    private var currentSessionEpoch = 0L
    private var isSessionEnding = false
    private var clock = 0L
    private var isAppPresent = true

    private fun isLive(sessionEpoch: Long): Boolean = !isSessionEnding && sessionEpoch == currentSessionEpoch

    private fun controller(): EmbeddedBlocksRegistryImpl {
        coEvery { interactor.listenEmbeddedPlaceEvents() } returns placeEvents
        coEvery { interactor.listenConfigUpdates() } returns configUpdates
        every { interactor.listenSessionEpoch() } returns sessionEpochs
        every { interactor.returnCheckPending } returns returnCheckPending
        every { interactor.reservePlaceShow(any(), any(), any()) } returns PlaceShowReservation.RESERVED
        every { interactor.releasePlaceShow(any(), any()) } returns true
        every { interactor.markEmbeddedDelayWaitedOut(any(), any(), any()) } answers { isLive(thirdArg()) }
        every { interactor.isLiveSession(any()) } answers { isLive(firstArg()) }
        every { interactor.sessionEpoch } answers { currentSessionEpoch }
        return EmbeddedBlocksRegistryImpl(
            inAppInteractor = interactor,
            scopeProvider = { workerScope ?: scope },
            monotonicNow = { Milliseconds(clock) },
            isAppPresent = { isAppPresent },
        )
    }

    private fun screen(): Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitWorkPostedToMain() {
        val deadline = System.currentTimeMillis() + 5_000L
        while (shadowOf(Looper.getMainLooper()).isIdle) {
            check(System.currentTimeMillis() < deadline) { "nothing reached the main thread" }
            Thread.sleep(5L)
        }
        Thread.sleep(100L)
    }

    @Test
    fun `block appearance pulls content for its place`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `two blocks on the same place both receive the same content`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val first = RecordingHandle()
        val second = RecordingHandle()
        val controller = controller()
        controller.register(place, first)
        controller.register(place, second)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), first.received)
        assertEquals(listOf<InAppType.Embedded?>(content), second.received)
        // One resolve serves everyone.
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    @Test
    fun `operation matched to a registered place resolves with that operation as the trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, operation) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
    }

    @Test
    fun `operation for an unregistered place is dropped without a resolve`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(PlaceKey.of("nobody-registered-here"), operation)) }
        idleMain()

        assertTrue(handle.received.isEmpty())
        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
    }

    @Test
    fun `a block whose host is gone stops holding its place`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        val controller = controller()
        var handle: RecordingHandle? = RecordingHandle()
        val handleReference = WeakReference<EmbeddedBlockHandle>(handle)
        controller.register(place, handle!!)
        idleMain()

        // A host without a lifecycle owner never says goodbye — it only lets the block go. The
        // registry must not be what keeps it, and the Activity behind it, in this process.
        handle = null
        awaitCollection(handleReference)

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
    }

    private fun awaitCollection(reference: WeakReference<*>) {
        repeat(50) {
            if (reference.get() == null) return
            System.gc()
            System.runFinalization()
            Thread.sleep(10L)
        }
        assertNull("the registry is still holding the handle of a host that is gone", reference.get())
    }

    @Test
    fun `operation for a paused place is skipped and the next appearance resolves fresh`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle(isActive = false)
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    @Test
    fun `unregistered handle stops receiving content`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, operation) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        val registration = controller.register(place, handle)
        idleMain()

        registration.close()
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        assertTrue(handle.received.isEmpty())
    }

    @Test
    fun `controller never calls selection itself`() {
        // The registry routes; the selection lives in the interactor. The only domain entries
        // the controller touches are selectInAppForPlace and the push flow subscription.
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val controller = controller()
        controller.register(place, RecordingHandle())
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        coVerify(exactly = 1) { interactor.listenEmbeddedPlaceEvents() }
        coVerify(exactly = 1) { interactor.listenConfigUpdates() }
        coVerify(exactly = 0) { interactor.getInAppToShowById(any()) }
        coVerify(exactly = 0) { interactor.filterShowableInAppIds(any(), any()) }
    }

    @Test
    fun `winner with delayTime is announced as pending and delivered after the delay`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        // The SDK has answered: the block hears "pending" at once, the content waits out the delay.
        assertEquals(1, handle.pendingCount)
        assertEquals(emptyList<InAppType.Embedded?>(), handle.received)

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `the same winner re-selected keeps the running delay timer`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(3_000L)
        scope.testScheduler.runCurrent()
        // The block left and returned mid-delay: the same winner re-selected must not restart
        // the countdown, or a frequently revisited block would wait forever.
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(2, handle.pendingCount)
        assertEquals(emptyList<InAppType.Embedded?>(), handle.received)

        scope.testScheduler.advanceTimeBy(2_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        // Delivered on the original schedule — five seconds after the first selection.
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a delay that elapsed while the block was away is delivered at once and not waited out again`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L)) andThen winner()
        val handle = RecordingHandle()
        val controller = controller()
        val registration = controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        // The block is detached mid-delay: the clock keeps running for the place, and its end
        // is what the session remembers — not the block that happened to be there.
        registration.close()
        idleMain()
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        verify(exactly = 1) { interactor.markEmbeddedDelayWaitedOut(place, content.inAppId, any()) }
        assertTrue(handle.received.isEmpty())

        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a newer resolve outcome supersedes a winner still waiting out its delay`() {
        val delayed = InAppStub.getEmbedded().copy(inAppId = "delayed")
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delayed, Milliseconds(5_000L)) andThen winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        // A new resolve lands while the old winner still waits: its outcome replaces the timer.
        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(10_000L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `new config re-resolves places with an active block`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle(isActive = true)
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { configUpdates.emit(Unit) }
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `invalidation for a paused place is skipped and the next appearance resolves fresh`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle(isActive = false)
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { configUpdates.emit(Unit) }
        idleMain()
        // Nothing resolves in the background for a paused block.
        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    @Test
    fun `resolve failure is delivered as nothing to show`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } throws IllegalStateException("boom")
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
    }

    @Test
    fun `an Error inside the resolve is delivered as nothing to show, not left to the budget`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } throws StackOverflowError()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
    }

    @Test
    fun `resolve failure while a winner waits out its delay keeps the running timer`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L)) andThenThrows IllegalStateException("boom")
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(2, handle.pendingCount)
        assertEquals(emptyList<InAppType.Embedded?>(), handle.received)

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        verify(exactly = 1) { interactor.markEmbeddedDelayWaitedOut(place, content.inAppId, any()) }
    }

    @Test
    fun `pending is announced to paused handles too so the delay leaves their clock`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L))
        val active = RecordingHandle()
        val paused = RecordingHandle(isActive = false)
        val controller = controller()
        controller.register(place, active)
        controller.register(place, paused)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(1, active.pendingCount)
        assertEquals(1, paused.pendingCount)
    }

    @Test
    fun `operation queued while resolving keeps its trigger for the second pass`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        // The pull resolve hangs on the gate; the operation lands meanwhile and must queue up
        // WITH its trigger — a triggerless second pass would never match operation-targetings.
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(content, handle.received.last())
    }

    @Test
    fun `channels resubscribe after the SDK scope is recreated`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        coEvery { interactor.selectInAppForPlace(place, operation) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        // Soft re-initialization: the scope both channels were collecting on is cancelled and a
        // fresh one takes its place.
        scope.cancel()
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        scope.launch { configUpdates.emit(Unit) }
        idleMain()

        // Push and config invalidation are both alive again on the new scope.
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        coVerify(atLeast = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    @Test
    fun `resubscribing re-resolves what may have changed while the channels were dead`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }

        scope.cancel()
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        // Nothing tells us what was emitted while nobody was collecting, so the place is stale.
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `startListening leaves live channels alone`() {
        val controller = controller()
        controller.register(place, RecordingHandle())
        idleMain()

        controller.startListening()
        controller.startListening()
        idleMain()

        // One subscription per channel, and no re-resolve: the channels never died.
        coVerify(exactly = 1) { interactor.listenEmbeddedPlaceEvents() }
        coVerify(exactly = 1) { interactor.listenConfigUpdates() }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    // ---- show budget holds: taken where the content goes to the blocks, given back when nothing shows ----

    private class HoldingHandle : EmbeddedBlockHandle {
        override val isActive: Boolean = true
        override val isPausedForBackground: Boolean = false
        override val isLeftBehind: Boolean = false
        override val isHoldingContent: Boolean = true

        override fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {}

        override fun onConfigUnavailable(answer: EmbeddedPlaceAnswer) {}

        override fun onAppResumedOn(activity: Activity) = Unit

        override fun onReturnChecked() = Unit

        override fun onSessionRenewed() = Unit
    }

    @Test
    fun `the place reserves its show when the content is delivered, once for all its blocks`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val controller = controller()
        controller.register(place, RecordingHandle())
        controller.register(place, RecordingHandle())
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(place, content, any()) }
    }

    @Test
    fun `a refused hold empties the place without a failure`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        every { interactor.reservePlaceShow(place, content, any()) } returns PlaceShowReservation.REFUSED
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
        verify { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `nothing to show over an active hold gives the hold back`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns EmbeddedResolveOutcome.Empty
        val controller = controller()
        controller.register(place, RecordingHandle())
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
        verify { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `a winner waiting out its delay reserves only when the delay has run`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L))
        val controller = controller()
        controller.register(place, RecordingHandle())
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        // The hold would sit through the whole delay otherwise — the budget is asked at the delivery.
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(place, content, any()) }
    }

    @Test
    fun `a block that dropped its content gives the hold back only when no block of the place still holds content`() {
        val controller = controller()
        val dropped = RecordingHandle()
        val stillLoading = HoldingHandle()
        controller.register(place, dropped)
        controller.register(place, stillLoading)
        idleMain()

        controller.onBlockContentDropped(place)
        idleMain()
        verify(exactly = 0) { interactor.releasePlaceShow(place) }

        val alone = controller()
        alone.register(place, RecordingHandle())
        idleMain()
        alone.onBlockContentDropped(place)
        idleMain()
        verify(exactly = 1) { interactor.releasePlaceShow(place) }
    }

    @Test
    fun `the last block leaving the place gives the hold back`() {
        val controller = controller()
        val registration = controller.register(place, RecordingHandle())
        idleMain()

        registration.close()
        idleMain()

        verify { interactor.releasePlaceShow(place) }
    }

    @Test
    fun `an answer the SDK could not give is delivered to the blocks as such and gives the hold back`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            EmbeddedResolveOutcome.ConfigUnavailable
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(1, handle.unavailableCount)
        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
        verify { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an answer the SDK could not give while a winner waits out its delay keeps the running timer`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            winner(delay = Milliseconds(5_000L)) andThen
            EmbeddedResolveOutcome.ConfigUnavailable
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(0, handle.unavailableCount)
        assertEquals(2, handle.pendingCount)

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(content, handle.received.last())
    }

    @Test
    fun `an answer the SDK could not give for a place nobody listens to is dropped with the hold`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            EmbeddedResolveOutcome.ConfigUnavailable
        val controller = controller()
        val registration = controller.register(place, RecordingHandle())
        idleMain()
        registration.close()
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        // Once for the unregistration, once for the answer that found nobody to hear it.
        verify(exactly = 1) { interactor.releasePlaceShow(place) }
        verify(exactly = 1) { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an answer tells whether an operation asked for it`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        assertEquals(listOf(false, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `an answer carries the session its resolve started in and the moment it started`() {
        currentSessionEpoch = 3L
        clock = 1_000L
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            clock = 1_300L
            winner()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(3L, handle.answers.single().sessionEpoch)
        assertEquals(Milliseconds(1_000L), handle.answers.single().selectionStartTick)
    }

    @Test
    fun `a resolve a new session overtook is dropped and the queued pass delivers the new session's answer`() {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { firstResolveGate.await() } andThen winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        controller.onBlockAppeared(place)
        idleMain()
        firstResolveGate.complete(winner())
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        assertEquals(1L, handle.answers.single().sessionEpoch)
        verify(exactly = 1) { interactor.reservePlaceShow(place, content, 1L) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 0L) }
    }

    @Test
    fun `an empty answer of an ended session gives back no hold and reaches no block`() {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { firstResolveGate.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an answer whose session ended before its hold was taken is dropped`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        every { interactor.reservePlaceShow(place, content, any()) } returns PlaceShowReservation.STALE
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an empty answer whose session ended before its hold was given back is dropped`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns EmbeddedResolveOutcome.Empty
        val handle = RecordingHandle()
        val controller = controller()
        every { interactor.releasePlaceShow(place, any()) } returns false
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertTrue(handle.received.isEmpty())
    }

    @Test
    fun `a delay timer of an ended session delivers nothing`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(delay = Milliseconds(5_000L))
        val handle = RecordingHandle()
        val controller = controller()
        every { interactor.markEmbeddedDelayWaitedOut(place, content.inAppId, 0L) } returns false
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `a new session picking the same delayed winner waits its delay again from the start`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(delay = Milliseconds(5_000L))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(3_000L)
        scope.testScheduler.runCurrent()

        currentSessionEpoch = 1L
        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(2_001L)
        scope.testScheduler.runCurrent()
        idleMain()
        assertTrue(handle.received.isEmpty())

        scope.testScheduler.advanceTimeBy(3_000L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        assertEquals(1L, handle.answers.single().sessionEpoch)
    }

    @Test
    fun `a new session's failure is delivered at once instead of waiting for the ended session's timer`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns
            winner(delay = Milliseconds(5_000L)) andThen EmbeddedResolveOutcome.ConfigUnavailable
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(1, handle.unavailableCount)
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()
        assertTrue(handle.received.isEmpty())
    }

    @Test
    fun `a delayed answer's selection clock leaves the campaign's delay out`() {
        clock = 1_000L
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            clock = 1_300L
            winner(delay = Milliseconds(5_000L))
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        clock = 6_300L
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(Milliseconds(6_000L), handle.answers.single().selectionStartTick)
    }

    @Test
    fun `a queued pass that took in a config update answers as a config pass, still with the operation's trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns EmbeddedResolveOutcome.Empty
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        scope.launch { configUpdates.emit(Unit) }
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `an answer the SDK could not give in a session that has ended reaches no block`() {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { firstResolveGate.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        firstResolveGate.complete(EmbeddedResolveOutcome.ConfigUnavailable)
        idleMain()

        assertEquals(0, handle.unavailableCount)
        verify(exactly = 0) { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an appearance queued with an operation answers as a non-operation pass, still with the operation's trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns EmbeddedResolveOutcome.Empty
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `a new session asks again every place with a block on screen, away in background or left behind by it, stamped with the new session`() {
        coEvery { interactor.selectInAppForPlace(any(), any()) } returns winner()
        val backgroundPlace = PlaceKey.of("background-place")
        val awayPlace = PlaceKey.of("away-place")
        val leftPlace = PlaceKey.of("left-place")
        val onScreen = RecordingHandle()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val awayInApp = RecordingHandle(isActive = false)
        val leftBySession = RecordingHandle(isActive = false).apply { leavesOnSessionRenewed = true }
        val controller = controller()
        controller.register(place, onScreen)
        controller.register(backgroundPlace, inBackground)
        controller.register(awayPlace, awayInApp)
        controller.register(leftPlace, leftBySession)
        idleMain()

        currentSessionEpoch = 1L
        clock = 4_000L
        sessionEpochs.value = 1L
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(backgroundPlace, any()) }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(awayPlace, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(leftPlace, any()) }
        assertEquals(listOf(1L), onScreen.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(1L), inBackground.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(1L to false), leftBySession.answers.map { answer -> answer.sessionEpoch to answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(leftPlace, any(), any()) }
        assertEquals(Milliseconds(4_000L), onScreen.answers.single().selectionStartTick)
        assertEquals(false, onScreen.answers.single().isByOperation)
    }

    @Test
    fun `a place asked while the session is ending waits for the new session and is asked once it begins`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        isSessionEnding = true
        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        isSessionEnding = false
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(1L), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an answer computed in a session that is ending reaches no block`() {
        val resolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { resolveGate.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        isSessionEnding = true
        resolveGate.complete(winner())
        idleMain()

        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `a winner without a page holds no show reservation for the place`() {
        val withoutPage = content.copy(layers = emptyList())
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(variant = withoutPage)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
        verify(exactly = 1) { interactor.releasePlaceShow(place, 0L) }
        assertEquals(listOf<InAppType.Embedded?>(withoutPage), handle.received)
    }

    @Test
    fun `a delay that ran out while the app was away delivers only once the session check of its return has run`() {
        val returnChecked = CompletableDeferred<Unit>()
        coEvery { interactor.awaitReturnChecked() } coAnswers { returnChecked.await() }
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(delay = Milliseconds(5_000L))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertTrue(handle.received.isEmpty())
        verify(exactly = 0) { interactor.markEmbeddedDelayWaitedOut(any(), any(), any()) }

        returnChecked.complete(Unit)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a winner whose delay ran out keeps its delivery when a failure overtakes it on the way to the main thread`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns
            winner(delay = Milliseconds(20L)) andThen EmbeddedResolveOutcome.ConfigUnavailable
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        val worker = Executors.newSingleThreadExecutor()
        workerScope = CoroutineScope(worker.asCoroutineDispatcher())
        try {
            controller.onBlockAppeared(place)
            awaitWorkPostedToMain()
            idleMain()
            awaitWorkPostedToMain()
            workerScope = null

            controller.onBlockAppeared(place)

            assertEquals(0, handle.unavailableCount)
            idleMain()
            assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        } finally {
            workerScope = null
            worker.shutdownNow()
        }
    }

    @Test
    fun `the app resuming on a screen tells every block of every place`() {
        val onScreen = RecordingHandle()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val controller = controller()
        controller.register(place, onScreen)
        controller.register(PlaceKey.of("other-place"), inBackground)
        idleMain()
        val resumed = screen()

        controller.onAppResumedOn(resumed)
        idleMain()

        assertEquals(listOf(resumed), onScreen.resumedOn)
        assertEquals(listOf(resumed), inBackground.resumedOn)
    }

    @Test
    fun `a new session while no screen of the app is resumed asks a block away in background once a screen resumes, timed from that resume`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        isAppPresent = false
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()

        currentSessionEpoch = 1L
        clock = 4_000L
        sessionEpochs.value = 1L
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, any()) }

        isAppPresent = true
        clock = 6_000L
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(1L), inBackground.answers.map { answer -> answer.sessionEpoch })
        assertEquals(Milliseconds(6_000L), inBackground.answers.single().selectionStartTick)
    }

    @Test
    fun `a new session's ask held for a block away in background is made once the app resumes on another screen, reserving no show for the block that left`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true).apply { leavesOnAppResumed = true }
        isAppPresent = false
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()

        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), inBackground.received)
        assertEquals(listOf(1L to false), inBackground.answers.map { answer -> answer.sessionEpoch to answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `a new session that begins after the app came back on another screen asks the block that left the screen, reserving no show for it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val leftOnReturn = RecordingHandle(isActive = false, isLeftBehind = true)
        val controller = controller()
        controller.register(place, leftOnReturn)
        idleMain()

        currentSessionEpoch = 1L
        clock = 4_000L
        sessionEpochs.value = 1L
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), leftOnReturn.received)
        assertEquals(listOf(1L to false), leftOnReturn.answers.map { answer -> answer.sessionEpoch to answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `a place reserves its show at delivery unless every block of it is off screen after leaving it on the app's return`() {
        coEvery { interactor.selectInAppForPlace(any(), any()) } returns winner()
        val backgroundPlace = PlaceKey.of("background-place")
        val leftPlace = PlaceKey.of("left-place")
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val awayInApp = RecordingHandle(isActive = false)
        val leftOnReturn = RecordingHandle(isActive = false, isLeftBehind = true)
        val controller = controller()
        controller.register(backgroundPlace, inBackground)
        controller.register(place, awayInApp)
        controller.register(leftPlace, leftOnReturn)
        idleMain()

        controller.onBlockAppeared(backgroundPlace)
        controller.onBlockAppeared(place)
        controller.onBlockAppeared(leftPlace)
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(backgroundPlace, content, 0L) }
        verify(exactly = 1) { interactor.reservePlaceShow(place, content, 0L) }
        verify(exactly = 0) { interactor.reservePlaceShow(leftPlace, any(), any()) }
        assertEquals(listOf(true), inBackground.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf(true), awayInApp.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf(false), leftOnReturn.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf<InAppType.Embedded?>(content), leftOnReturn.received)
    }

    @Test
    fun `a place asked before the session check of the app's return is asked once the check has run, after its blocks heard of it`() {
        val handle = RecordingHandle()
        var checksHeardBeforeAsk: Int? = null
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            checksHeardBeforeAsk = handle.returnCheckedCount
            winner()
        }
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        val checksHeardBeforeReturn = handle.returnCheckedCount

        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(checksHeardBeforeReturn + 1, checksHeardBeforeAsk)
    }

    @Test
    fun `a place asked before the session check of the app's return waits for the new session when the check found the session over`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true
        controller.onBlockAppeared(place)
        idleMain()

        isSessionEnding = true
        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        isSessionEnding = false
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(1L), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an appearance that lands while an operation pass is in flight makes that pass's answer a non-operation answer`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val operationGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, operation) } coAnswers { operationGate.await() }
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns EmbeddedResolveOutcome.Empty
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        operationGate.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight keeps the time of the renewal`() {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        var resolves = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (resolves++ == 0) firstResolveGate.await() else winner()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        clock = 1_000L
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = 1L
        clock = 4_000L
        sessionEpochs.value = 1L
        idleMain()
        clock = 9_000L
        firstResolveGate.complete(winner())
        idleMain()

        assertEquals(listOf(1L), handle.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(Milliseconds(4_000L)), handle.answers.map { answer -> answer.selectionStartTick })
    }

    @Test
    fun `a session renewal that came while the channels were dead still asks the blocks away in background`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()

        scope.cancel()
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(1L), inBackground.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a place asked before the session check of the app's return is asked once the check is reported over, even when the flag flipped back before anything watched it`() {
        scope = TestScope(StandardTestDispatcher())
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        scope.testScheduler.runCurrent()

        returnCheckPending.value = true
        controller.onBlockAppeared(place)
        idleMain()
        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()
        scope.testScheduler.runCurrent()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `channels that resubscribe after the session check of the app's return release what still waits for it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()
        returnCheckPending.value = true
        controller.onBlockAppeared(place)
        idleMain()

        scope.cancel()
        returnCheckPending.value = false
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        assertEquals(1, inBackground.returnCheckedCount)
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    private var isAppInForeground = true
    private val blockScreen: Activity by lazy { screen() }

    private class ShownPage : EmbeddedUpdatableContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(RuntimeEnvironment.getApplication())
        var releaseCount = 0
        val sessionRefreshes = mutableListOf<Pair<Long, Milliseconds>>()
        val withheldShows = mutableListOf<Boolean>()

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() {
            releaseCount++
        }

        override fun refreshForSession(
            params: Map<String, String>,
            sessionEpoch: Long,
            selectionStartTick: Milliseconds,
            onResult: (Boolean) -> Unit,
        ) {
            sessionRefreshes.add(sessionEpoch to selectionStartTick)
            onResult(true)
        }

        override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) = onResult(true)

        override fun confirmForSession(sessionEpoch: Long) = Unit

        override fun withholdShow(isWithheld: Boolean) {
            withheldShows.add(isWithheld)
        }

        override val rendersNothing = false

        override var onRendersNothing: (() -> Unit)? = null

        override fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?) = Unit
    }

    private fun liveBlock(
        registry: EmbeddedBlocksRegistry,
        states: MutableList<EmbeddedBlockState>,
        pages: MutableList<ShownPage> = mutableListOf(),
    ): EmbeddedBlockContentController =
        EmbeddedBlockContentController(
            placeSystemName = place.value,
            providerFactory = { _, _ -> ShownPage().also { page -> pages.add(page) } },
            blocksRegistry = { registry },
            monotonicNow = { Milliseconds(clock) },
            hostActivity = { blockScreen },
            isAppInForeground = { isAppInForeground },
        ).apply { onStateChange = { state -> states.add(state) } }

    private fun EmbeddedBlockContentController.leaveForBackground() {
        isAppInForeground = false
        pause()
        isAppPresent = false
    }

    private fun EmbeddedBlocksRegistryImpl.comeBackOnAnotherScreenWithTheSessionOver() = comeBackWithTheSessionOver(on = screen())

    private fun EmbeddedBlocksRegistryImpl.comeBackOnTheBlockScreenWithTheSessionOver() = comeBackWithTheSessionOver(on = blockScreen)

    private fun EmbeddedBlocksRegistryImpl.comeBackWithTheSessionOver(on: Activity) {
        isSessionEnding = true
        isAppPresent = true
        onAppResumedOn(on)
        idleMain()
        isSessionEnding = false
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()
    }

    @Test
    fun `a feed the new session refuses is gone before its first frame when the user comes back to it from the screen the app returned on`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty
        val states = mutableListOf<EmbeddedBlockState>()
        val registry = controller()
        val block = liveBlock(registry, states)
        block.start()
        idleMain()
        block.leaveForBackground()

        registry.comeBackOnAnotherScreenWithTheSessionOver()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        val reportedBeforeReturn = states.size
        isAppInForeground = true
        block.start()
        idleMain()

        assertEquals(
            listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading, EmbeddedBlockState.Empty),
            states.drop(reportedBeforeReturn),
        )
    }

    @Test
    fun `a feed the new session still picks reserves no show while the user is on the screen the app returned on`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val states = mutableListOf<EmbeddedBlockState>()
        val registry = controller()
        val block = liveBlock(registry, states)
        block.start()
        idleMain()
        block.leaveForBackground()

        registry.comeBackOnAnotherScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }

        isAppInForeground = true
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, 1L) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a feed taken off its screen inside its activity while the app was in background is gone before its first frame when the user comes back to it and the new session refuses it, with no show reserved meanwhile`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForBackground()
        block.onLeftScreen()

        registry.comeBackOnTheBlockScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
        val reportedBeforeReturn = states.size

        isAppInForeground = true
        block.start()
        idleMain()

        assertEquals(
            listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading, EmbeddedBlockState.Empty),
            states.drop(reportedBeforeReturn),
        )
        assertEquals(1, pages.single().releaseCount)
        assertTrue(pages.single().withheldShows.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
    }

    @Test
    fun `a feed taken off its screen inside its activity while the app was in background reserves its show only on the user's return when the new session still picks it, and takes the new session's data timed from that return`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForBackground()
        block.onLeftScreen()
        clock = 4_000L

        registry.comeBackOnTheBlockScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
        assertTrue(pages.single().sessionRefreshes.isEmpty())
        val reportedBeforeReturn = states.size

        clock = 9_000L
        isAppInForeground = true
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, 1L) }
        assertEquals(listOf(1L to Milliseconds(9_000L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the new session refuses stays on screen until the user leaves it when the app comes back on its own screen`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForBackground()
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()

        isAppPresent = true
        registry.onAppResumedOn(blockScreen)
        idleMain()
        isAppInForeground = true
        block.start()
        idleMain()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, pages.single().releaseCount)

        block.pause()

        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, pages.single().releaseCount)
    }

    private fun answerTheRenewalOnlyWhen(renewalAnswer: CompletableDeferred<EmbeddedResolveOutcome>, laterAnswer: EmbeddedResolveOutcome) {
        var resolves = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            when (resolves++) {
                0 -> winner()
                1 -> renewalAnswer.await()
                else -> laterAnswer
            }
        }
    }

    @Test
    fun `a feed the user comes back to before the new session answers stays on screen when the new session refuses it and collapses once the user leaves it`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = EmbeddedResolveOutcome.Empty)
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForBackground()
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        val reportedBeforeReturn = states.size

        isAppInForeground = true
        block.start()
        idleMain()
        renewalAnswer.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        assertEquals(0, pages.single().releaseCount)
        assertEquals(true, pages.single().withheldShows.lastOrNull())

        block.pause()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty), states.drop(reportedBeforeReturn))
        assertEquals(1, pages.single().releaseCount)
    }

    @Test
    fun `a feed the user comes back to before the new session answers takes the new session's data in place, timed from that return, when the new session still picks it`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForBackground()
        clock = 4_000L
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        val reportedBeforeReturn = states.size

        clock = 9_000L
        isAppInForeground = true
        block.start()
        idleMain()
        clock = 11_000L
        renewalAnswer.complete(winner())
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(1L to Milliseconds(9_000L)), pages.single().sessionRefreshes)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, 1L) }
    }

    @Test
    fun `a feed the user comes back to before the new session begins takes the new session's data timed from its first selection made after that return`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, mutableListOf(), pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForBackground()
        isSessionEnding = true
        isAppPresent = true
        registry.onAppResumedOn(screen())
        idleMain()

        clock = 9_000L
        isAppInForeground = true
        block.start()
        idleMain()
        clock = 12_000L
        isSessionEnding = false
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(1L to Milliseconds(12_000L)), pages.single().sessionRefreshes)
    }

    @Test
    fun `a block back from the screen the app returned on is asked by a later new session once it leaves its screen again with its page, reserving no show for it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val registry = controller()
        val block = liveBlock(registry, mutableListOf())
        block.start()
        idleMain()
        block.leaveForBackground()
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        isAppInForeground = true
        block.start()
        idleMain()
        block.pause()

        currentSessionEpoch = 2L
        sessionEpochs.value = 2L
        idleMain()

        coVerify(exactly = 4) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 2L) }
    }

    private fun EmbeddedBlockContentController.leaveForAnotherScreen() {
        isAppInForeground = true
        pause()
    }

    private fun EmbeddedBlocksRegistryImpl.beginNewSessionWhileAwayAndComeBackOnAnotherScreen() {
        isAppPresent = false
        currentSessionEpoch = 1L
        sessionEpochs.value = 1L
        idleMain()
        isAppPresent = true
        onAppResumedOn(screen())
        idleMain()
    }

    @Test
    fun `a feed left for another screen and refused by a new session begun meanwhile is gone before its first frame when the user comes back to it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()

        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
        val reportedBeforeReturn = states.size

        block.start()
        idleMain()

        assertEquals(
            listOf(EmbeddedBlockState.Empty, EmbeddedBlockState.Loading, EmbeddedBlockState.Empty),
            states.drop(reportedBeforeReturn),
        )
        assertEquals(1, pages.single().releaseCount)
        assertTrue(pages.single().withheldShows.isEmpty())
        assertTrue(pages.single().sessionRefreshes.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
    }

    @Test
    fun `a feed left for another screen and still picked by a new session begun meanwhile reserves its show only on the user's return and takes the new session's data timed from it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = 4_000L

        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
        val reportedBeforeReturn = states.size

        clock = 9_000L
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, 1L) }
        assertEquals(listOf(1L to Milliseconds(9_000L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the user comes back to from another screen before a new session begun meanwhile answers stays on screen when the new session refuses it and collapses once the user leaves it`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = EmbeddedResolveOutcome.Empty)
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()
        val reportedBeforeReturn = states.size

        block.start()
        idleMain()
        renewalAnswer.complete(EmbeddedResolveOutcome.Empty)
        idleMain()

        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        assertEquals(0, pages.single().releaseCount)
        assertEquals(true, pages.single().withheldShows.lastOrNull())

        block.pause()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty), states.drop(reportedBeforeReturn))
        assertEquals(1, pages.single().releaseCount)
    }

    @Test
    fun `a feed the user comes back to from another screen before a new session begun meanwhile answers takes the new session's data in place, timed from that return`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = 4_000L
        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()
        val reportedBeforeReturn = states.size

        clock = 9_000L
        block.start()
        idleMain()
        clock = 11_000L
        renewalAnswer.complete(winner())
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(1L to Milliseconds(9_000L)), pages.single().sessionRefreshes)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the user comes back to and leaves again before a new session begun meanwhile answers reserves no show until the user comes back once more, timed from that return`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = 1_000L
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = 4_000L
        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()
        clock = 9_000L
        block.start()
        idleMain()
        block.leaveForAnotherScreen()

        clock = 11_000L
        renewalAnswer.complete(winner())
        idleMain()

        coVerify(exactly = 3) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
        val reportedBeforeReturn = states.size

        clock = 15_000L
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, 1L) }
        assertEquals(listOf(1L to Milliseconds(15_000L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a block that leaves its screen for another one and comes back in the same session is asked only on its return and reserves at delivery an answer that reached it away`() {
        val firstAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { firstAnswer.await() }
        val states = mutableListOf<EmbeddedBlockState>()
        val registry = controller()
        val block = liveBlock(registry, states)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        registry.onAppResumedOn(screen())
        firstAnswer.complete(winner())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 1) { interactor.reservePlaceShow(place, content, 0L) }

        block.start()
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a feed asked by a new session while on another screen and released before the user comes back leaves no show reserved for its place`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val registry = controller()
        val block = liveBlock(registry, mutableListOf())
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        registry.beginNewSessionWhileAwayAndComeBackOnAnotherScreen()
        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.releasePlaceShow(place) }

        block.release()
        idleMain()

        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), 1L) }
        verify(atLeast = 1) { interactor.releasePlaceShow(place) }
    }
}
