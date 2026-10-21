package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import cloud.mindbox.mobile_sdk.embedded.webview.DataPushOutcome
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.managers.LifecycleManager
import cloud.mindbox.mobile_sdk.models.ConfigVersion
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlocksRegistryTest {

    private class RecordingHandle(
        override var isActive: Boolean = true,
        override var isPausedForBackground: Boolean = false,
        override var isLeftBehind: Boolean = false,
    ) : EmbeddedBlockHandle {
        override var isHoldingContent: Boolean = false
        override var holdsLiveContent: Boolean = false
        val received = mutableListOf<InAppType.Embedded?>()
        val selectionTimes = mutableListOf<Milliseconds>()
        val answers = mutableListOf<EmbeddedPlaceAnswer>()
        var pendingCount = 0
        var unavailableCount = 0

        override fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
            received.add(content)
            selectionTimes.add(answer.selectionTime)
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
            if (leavesOnSessionRenewed) {
                isLeftBehind = true
                holdsLiveContent = true
            }
        }
    }

    private val interactor: InAppInteractor = mockk(relaxed = true)

    private var scope = TestScope(UnconfinedTestDispatcher())
    private var workerScope: CoroutineScope? = null

    private val place = PlaceKey.of("main-screen-top")
    private val content = InAppStub.getEmbedded()

    private val olderConfig = ConfigVersion.FIRST
    private val newerConfig = olderConfig.next()

    private fun winner(
        variant: InAppType.Embedded = content,
        delay: Milliseconds? = null,
        configVersion: ConfigVersion = olderConfig,
    ): EmbeddedResolveOutcome.Content = EmbeddedResolveOutcome.Content(variant, delay, configVersion)

    private val placeEvents = MutableSharedFlow<EmbeddedPlaceEvent>()
    private val configVersions = MutableSharedFlow<ConfigVersion>()
    private val sessionEpochs = MutableStateFlow(SessionEpoch(0L))
    private val returnCheckPending = MutableStateFlow(false)

    private var currentSessionEpoch = SessionEpoch(0L)
    private var isSessionEnding = false
    private var isAppPresent = true

    private fun isLive(sessionEpoch: SessionEpoch): Boolean = !isSessionEnding && sessionEpoch == currentSessionEpoch

    private var clock = Milliseconds(0L)

    private fun advance(by: Milliseconds) {
        clock = Milliseconds(clock.interval + by.interval)
    }

    private fun controller(): EmbeddedBlocksRegistryImpl {
        stubInteractor()
        return EmbeddedBlocksRegistryImpl(
            inAppInteractor = interactor,
            scopeProvider = { workerScope ?: scope },
            monotonicNow = { clock },
            isAppPresent = { isAppPresent },
        )
    }

    private fun stubInteractor() {
        coEvery { interactor.listenEmbeddedPlaceEvents() } returns placeEvents
        coEvery { interactor.listenConfigVersions() } returns configVersions
        every { interactor.listenSessionEpoch() } returns sessionEpochs
        every { interactor.returnCheckPending } returns returnCheckPending
        every { interactor.reservePlaceShow(any(), any(), any()) } returns PlaceShowReservation.RESERVED
        every { interactor.releasePlaceShow(any(), any()) } returns true
        every { interactor.markEmbeddedDelayWaitedOut(any(), any(), any()) } answers { isLive(SessionEpoch(thirdArg())) }
        every { interactor.isLiveSession(any()) } answers { isLive(SessionEpoch(firstArg())) }
        every { interactor.sessionEpoch } answers { currentSessionEpoch }
    }

    private val keptAliveHandles = mutableListOf<EmbeddedBlockHandle>()

    private fun <T : EmbeddedBlockHandle> keptAlive(handle: T): T = handle.also { keptAliveHandles.add(it) }

    private fun screen(): Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitWorkPostedToMain(worker: ExecutorService) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (shadowOf(Looper.getMainLooper()).isIdle) {
            check(System.currentTimeMillis() < deadline) { "nothing reached the main thread" }
            Thread.sleep(5L)
        }
        worker.submit {}.get(5L, TimeUnit.SECONDS)
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
    fun `two blocks on the same place both receive the same content from one resolve`() {
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
    fun `a block let go by a host without a lifecycle owner is not kept alive by the registry`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        val controller = controller()
        var handle: RecordingHandle? = RecordingHandle()
        val handleReference = WeakReference<EmbeddedBlockHandle>(handle)
        controller.register(place, handle!!)
        idleMain()

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
    fun `the registry routes and never calls the selection filter itself`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle()))
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        coVerify(exactly = 1) { interactor.listenEmbeddedPlaceEvents() }
        coVerify(exactly = 1) { interactor.listenConfigVersions() }
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

        assertEquals(1, handle.pendingCount)
        assertEquals(emptyList<InAppType.Embedded?>(), handle.received)

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `the same winner re-selected mid-delay keeps the running timer and its original schedule`() {
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
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(2, handle.pendingCount)
        assertEquals(emptyList<InAppType.Embedded?>(), handle.received)

        scope.testScheduler.advanceTimeBy(2_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `the same winner re-selected mid-delay brings its fresh content but keeps the selection time that started the delay`() {
        val refreshed = content.copy(
            layers = listOf((content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]")))
        )
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers {
            selections++
            if (selections == 1) {
                advance(Milliseconds(320L))
                winner(delay = Milliseconds(5_000L))
            } else {
                advance(Milliseconds(40L))
                winner(variant = refreshed, delay = Milliseconds(5_000L))
            }
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(3_000L)
        scope.testScheduler.runCurrent()
        controller.onBlockAppeared(place)
        idleMain()
        scope.testScheduler.advanceTimeBy(2_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(refreshed), handle.received)
        assertEquals(listOf(Milliseconds(320L)), handle.selectionTimes)
        verify(exactly = 1) { interactor.reservePlaceShow(place, refreshed, any()) }
    }

    @Test
    fun `a delayed winner keeps the selection time of its first pass when a newer config announced mid-resolve asks the place again`() {
        val firstPassGate = CompletableDeferred<Unit>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers {
            selections++
            if (selections == 1) {
                firstPassGate.await()
                advance(Milliseconds(1_070L))
                winner(delay = Milliseconds(10_000L), configVersion = olderConfig)
            } else {
                advance(Milliseconds(15L))
                winner(delay = Milliseconds(10_000L), configVersion = newerConfig)
            }
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        scope.launch { configVersions.emit(newerConfig) }
        idleMain()
        firstPassGate.complete(Unit)
        idleMain()
        scope.testScheduler.advanceTimeBy(10_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(2, selections)
        assertEquals(listOf(Milliseconds(1_070L)), handle.selectionTimes)
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

        scope.launch { configVersions.emit(newerConfig) }
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a config update within a session asks no place whose blocks are away in background or left behind`() {
        coEvery { interactor.selectInAppForPlace(any(), any()) } returns winner()
        val backgroundPlace = PlaceKey.of("background-place")
        val leftPlace = PlaceKey.of("left-place")
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle()))
        controller.register(backgroundPlace, keptAlive(RecordingHandle(isActive = false, isPausedForBackground = true)))
        controller.register(leftPlace, keptAlive(RecordingHandle(isActive = false, isLeftBehind = true)))
        idleMain()

        scope.launch { configVersions.emit(newerConfig) }
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(backgroundPlace, any()) }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(leftPlace, any()) }
    }

    @Test
    fun `invalidation for a paused place is skipped and the next appearance resolves fresh`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val handle = RecordingHandle(isActive = false)
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { configVersions.emit(newerConfig) }
        idleMain()
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
    fun `the content comes with the time its selection took`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers {
            advance(Milliseconds(320L))
            winner()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        clock = Milliseconds(1_000L)

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        assertEquals(listOf(Milliseconds(320L)), handle.selectionTimes)
    }

    @Test
    fun `a delayed winner comes with the time its selection took, not the delay`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers {
            advance(Milliseconds(320L))
            winner(delay = Milliseconds(5_000L))
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        advance(Milliseconds(5_000L))
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        assertEquals(listOf(Milliseconds(320L)), handle.selectionTimes)
    }

    @Test
    fun `pending is announced to paused handles too so their wait budget stays quiet on return`() {
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
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(content, handle.received.last())
    }

    @Test
    fun `channels resubscribe on a fresh scope after a soft re-initialization cancelled the old one`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        coEvery { interactor.selectInAppForPlace(place, operation) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.cancel()
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        scope.launch { configVersions.emit(newerConfig) }
        idleMain()

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

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `startListening leaves live channels alone, one subscription per channel and no re-resolve`() {
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle()))
        idleMain()

        controller.startListening()
        controller.startListening()
        idleMain()

        coVerify(exactly = 1) { interactor.listenEmbeddedPlaceEvents() }
        coVerify(exactly = 1) { interactor.listenConfigVersions() }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    private class HoldingHandle : EmbeddedBlockHandle {
        override val isActive: Boolean = true
        override val isPausedForBackground: Boolean = false
        override val isLeftBehind: Boolean = false
        override val isHoldingContent: Boolean = true
        override val holdsLiveContent: Boolean = true

        override fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {}

        override fun onConfigUnavailable(answer: EmbeddedPlaceAnswer) {}

        override fun onAppResumedOn(activity: Activity) = Unit

        override fun onReturnChecked() = Unit

        override fun onSessionRenewed() = Unit
    }

    @Test
    fun `the place reserves its show when the content is delivered, once for all its blocks`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns winner()
        val first = RecordingHandle()
        val second = RecordingHandle()
        val controller = controller()
        controller.register(place, first)
        controller.register(place, second)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(place, content, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), first.received)
        assertEquals(listOf<InAppType.Embedded?>(content), second.received)
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
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns EmbeddedResolveOutcome.Empty(olderConfig)
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle()))
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
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }

        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(place, content, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
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
        val onlyBlock = RecordingHandle()
        alone.register(place, onlyBlock)
        idleMain()
        alone.onBlockContentDropped(place)
        idleMain()
        verify(exactly = 1) { interactor.releasePlaceShow(place) }
    }

    @Test
    fun `the last block leaving the place gives the hold back`() {
        val controller = controller()
        val registration = controller.register(place, keptAlive(RecordingHandle()))
        idleMain()

        registration.close()
        idleMain()

        verify { interactor.releasePlaceShow(place) }
    }

    @Test
    fun `an answer the SDK could not give is delivered to the blocks as such and gives the hold back`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns
            EmbeddedResolveOutcome.ConfigUnavailable(olderConfig)
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
            EmbeddedResolveOutcome.ConfigUnavailable(olderConfig)
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
            EmbeddedResolveOutcome.ConfigUnavailable(olderConfig)
        val controller = controller()
        val registration = controller.register(place, keptAlive(RecordingHandle()))
        idleMain()
        registration.close()
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

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
    fun `an answer carries the session its resolve started in and the time its selection took`() {
        currentSessionEpoch = SessionEpoch(3L)
        clock = Milliseconds(1_000L)
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            clock = Milliseconds(1_300L)
            winner()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(SessionEpoch(3L), handle.answers.single().sessionEpoch)
        assertEquals(Milliseconds(300L), handle.answers.single().selectionTime)
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

        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        firstResolveGate.complete(winner())
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
        assertEquals(SessionEpoch(1L), handle.answers.single().sessionEpoch)
        verify(exactly = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(0L)) }
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

        currentSessionEpoch = SessionEpoch(1L)
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
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
        coEvery { interactor.selectInAppForPlace(place, any()) } returns EmbeddedResolveOutcome.Empty(olderConfig)
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
        every { interactor.markEmbeddedDelayWaitedOut(place, content.inAppId, SessionEpoch(0L)) } returns false
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
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

        currentSessionEpoch = SessionEpoch(1L)
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
        assertEquals(SessionEpoch(1L), handle.answers.single().sessionEpoch)
    }

    @Test
    fun `a feed on screen stays as it is through a new session's delay and takes that session's data once the delay has run`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen winner(delay = Milliseconds(5_000L))
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        val reportedBeforeRenewal = states.size

        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        scope.testScheduler.advanceTimeBy(4_999L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(reportedBeforeRenewal, states.size)
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(0, pages.single().releaseCount)
        assertTrue(pages.single().sessionRefreshes.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }

        scope.testScheduler.advanceTimeBy(2L)
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(reportedBeforeRenewal, states.size)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(SessionEpoch(1L)), pages.single().sessionRefreshes.map { refresh -> refresh.first })
        verify(exactly = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
    }

    @Test
    fun `a new session's failure is delivered at once instead of waiting for the ended session's timer`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns
            winner(delay = Milliseconds(5_000L)) andThen EmbeddedResolveOutcome.ConfigUnavailable(olderConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(1, handle.unavailableCount)
        scope.testScheduler.advanceTimeBy(5_001L)
        scope.testScheduler.runCurrent()
        idleMain()
        assertTrue(handle.received.isEmpty())
    }

    @Test
    fun `a queued pass that took in a config update answers as a config pass, still with the operation's trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns EmbeddedResolveOutcome.Empty(olderConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        scope.launch { configVersions.emit(newerConfig) }
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
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

        currentSessionEpoch = SessionEpoch(1L)
        firstResolveGate.complete(EmbeddedResolveOutcome.ConfigUnavailable(olderConfig))
        idleMain()

        assertEquals(0, handle.unavailableCount)
        verify(exactly = 0) { interactor.releasePlaceShow(place, any()) }
    }

    @Test
    fun `an appearance queued with an operation answers as a non-operation pass, still with the operation's trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns EmbeddedResolveOutcome.Empty(olderConfig)
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
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    private val queuedOperation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("queued-operation"))

    private fun queueOperationBehindAResolveInFlight(
        handle: RecordingHandle,
        alsoQueue: EmbeddedBlocksRegistryImpl.() -> Unit = {},
    ): CompletableDeferred<EmbeddedResolveOutcome> {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() } andThen winner()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        controller.alsoQueue()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, queuedOperation)) }
        idleMain()
        return firstResolveGate
    }

    @Test
    fun `an operation queued behind a resolve in flight is dropped when no block of its place is on screen or away in background by the time it would be asked`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueOperationBehindAResolveInFlight(handle)

        handle.isActive = false
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, queuedOperation) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
    }

    @Test
    fun `an operation queued behind a resolve in flight is asked for a block of its place away in background`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueOperationBehindAResolveInFlight(handle)

        handle.isActive = false
        handle.isPausedForBackground = true
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a queued pass that took in a config update is asked without the operation queued with it when no block of its place is on screen or away in background`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueOperationBehindAResolveInFlight(handle) {
            scope.launch { configVersions.emit(newerConfig) }
            idleMain()
        }

        handle.isActive = false
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, queuedOperation) }
        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    private fun emitQueuedOperation() {
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, queuedOperation)) }
        idleMain()
    }

    private fun renewTheSession(whileItEnds: () -> Unit = {}) {
        isSessionEnding = true
        whileItEnds()
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
    }

    private fun queueTheNewSessionAskBehindAResolveInFlight(
        handle: RecordingHandle,
        whileTheSessionEnds: () -> Unit = {},
    ): CompletableDeferred<EmbeddedResolveOutcome> {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { firstResolveGate.await() } andThen winner()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        renewTheSession(whileTheSessionEnds)
        return firstResolveGate
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight keeps the operation it carries for a block it left behind holding content`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueTheNewSessionAskBehindAResolveInFlight(handle) {
            emitQueuedOperation()
            handle.isActive = false
            handle.leavesOnSessionRenewed = true
        }

        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
        assertEquals(listOf(SessionEpoch(1L) to false), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight keeps the operation it carries for a block of its place on screen`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueTheNewSessionAskBehindAResolveInFlight(handle, whileTheSessionEnds = ::emitQueuedOperation)

        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight keeps the operation it carries for a block of its place away in background with nothing on it`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueTheNewSessionAskBehindAResolveInFlight(handle, whileTheSessionEnds = ::emitQueuedOperation)

        handle.isActive = false
        handle.isPausedForBackground = true
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight is asked without the operation it carries once every block of its place has left with nothing on it`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueTheNewSessionAskBehindAResolveInFlight(handle, whileTheSessionEnds = ::emitQueuedOperation)

        handle.isActive = false
        advance(Milliseconds(300L))
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, queuedOperation) }
        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(
            listOf(Triple(SessionEpoch(1L), false, Milliseconds(300L))),
            handle.answers.map { answer -> Triple(answer.sessionEpoch, answer.isByOperation, answer.selectionTime) },
        )
    }

    @Test
    fun `an operation queued with a new session's ask behind a resolve in flight is dropped from that ask once every block of its place has left with nothing on it`() {
        val handle = RecordingHandle()
        val firstResolveGate = queueTheNewSessionAskBehindAResolveInFlight(handle)
        emitQueuedOperation()

        handle.isActive = false
        firstResolveGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, queuedOperation) }
        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight keeps the operation it carries for a block that was on screen at the renewal and left holding its page`() {
        val configPassAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        var resolves = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { if (resolves++ == 1) configPassAnswer.await() else winner() }
        val registry = controller()
        val block = liveBlock(registry, mutableListOf())
        block.start()
        idleMain()
        scope.launch { configVersions.emit(newerConfig) }
        idleMain()
        renewTheSession(whileItEnds = ::emitQueuedOperation)

        block.leaveForAnotherScreen()
        configPassAnswer.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
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

        currentSessionEpoch = SessionEpoch(1L)
        clock = Milliseconds(4_000L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(backgroundPlace, any()) }
        coVerify(exactly = 0) { interactor.selectInAppForPlace(awayPlace, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(leftPlace, any()) }
        assertEquals(listOf(SessionEpoch(1L)), onScreen.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(SessionEpoch(1L)), inBackground.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(SessionEpoch(1L) to false), leftBySession.answers.map { answer -> answer.sessionEpoch to answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(leftPlace, any(), any()) }
        assertEquals(false, onScreen.answers.single().isByOperation)
    }

    @Test
    fun `an operation that arrives while the session is ending reaches the new session's selection with its trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        isSessionEnding = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L) to false), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
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

    private fun announceNewerConfig() {
        scope.launch { configVersions.emit(newerConfig) }
        idleMain()
    }

    @Test
    fun `a block waiting for the first config is selected once when the config is announced before its pass answers`() {
        val waitingPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { waitingPass.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        announceNewerConfig()
        waitingPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a block answered from the first config before it is announced is not asked again by the announcement`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        announceNewerConfig()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    private fun queueAnOperationBehindAPassWaitingForTheConfig(
        handle: RecordingHandle,
        isOperationBeforeTheAnnouncement: Boolean,
        askThePlace: EmbeddedBlocksRegistryImpl.() -> Unit,
    ) {
        val waitingPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { waitingPass.await() }
        coEvery { interactor.selectInAppForPlace(place, queuedOperation) } returns winner(configVersion = newerConfig)
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.askThePlace()

        if (isOperationBeforeTheAnnouncement) emitQueuedOperation()
        announceNewerConfig()
        if (!isOperationBeforeTheAnnouncement) emitQueuedOperation()
        waitingPass.complete(EmbeddedResolveOutcome.Empty(newerConfig))
        idleMain()
    }

    @Test
    fun `an operation that reaches a block waiting for the first config before the config is announced is answered by its own operation pass`() {
        val handle = RecordingHandle()
        queueAnOperationBehindAPassWaitingForTheConfig(handle, isOperationBeforeTheAnnouncement = true) {
            onBlockAppeared(place)
            idleMain()
        }

        assertEquals(listOf(false, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `an operation that reaches a block waiting for the first config after the config is announced is answered by its own operation pass`() {
        val handle = RecordingHandle()
        queueAnOperationBehindAPassWaitingForTheConfig(handle, isOperationBeforeTheAnnouncement = false) {
            onBlockAppeared(place)
            idleMain()
        }

        assertEquals(listOf(false, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `an operation that reaches a place whose new session's pass waits for the config before the config is announced is answered by its own operation pass`() {
        val handle = RecordingHandle()
        queueAnOperationBehindAPassWaitingForTheConfig(handle, isOperationBeforeTheAnnouncement = true) { renewTheSession() }

        assertEquals(listOf(SessionEpoch(1L) to false, SessionEpoch(1L) to true), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `an operation that reaches a place whose new session's pass waits for the config after the config is announced is answered by its own operation pass`() {
        val handle = RecordingHandle()
        queueAnOperationBehindAPassWaitingForTheConfig(handle, isOperationBeforeTheAnnouncement = false) { renewTheSession() }

        assertEquals(listOf(SessionEpoch(1L) to false, SessionEpoch(1L) to true), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `a block on screen is selected once in a new session when the new config is announced before its pass answers`() {
        val newSessionsPass = CompletableDeferred<EmbeddedResolveOutcome>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) winner() else newSessionsPass.await()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        renewTheSession()
        announceNewerConfig()
        newSessionsPass.complete(winner(configVersion = newerConfig))
        idleMain()

        assertEquals(2, selections)
        assertEquals(listOf(SessionEpoch(0L), SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a block on screen is selected once in a new session when its pass answers before the new config is announced`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        renewTheSession()
        announceNewerConfig()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(0L), SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an operation carried into a new session is the session's one selection when the new config is announced before its pass answers`() {
        val newSessionsPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        coEvery { interactor.selectInAppForPlace(place, queuedOperation) } coAnswers { newSessionsPass.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        renewTheSession(whileItEnds = ::emitQueuedOperation)
        announceNewerConfig()
        newSessionsPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `an operation carried into a new session is the session's one selection when its pass answers before the new config is announced`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        renewTheSession(whileItEnds = ::emitQueuedOperation)
        announceNewerConfig()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a new session does not ask again a place whose block started in it before the new session was announced`() {
        val startedPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { startedPass.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        startedPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a new session does not ask again a place whose block start was answered in it before the new session was announced`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a new session's ask held until a screen of the app resumes is not made once the block's return has asked its place in that session`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle(isActive = false, isPausedForBackground = true)
        isAppPresent = false
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        handle.isActive = true
        handle.isPausedForBackground = false
        controller.onBlockAppeared(place)
        idleMain()
        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a new session asks the operation carried into it for a place whose block started in it before the new session was announced`() {
        val startedPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { startedPass.await() }
        coEvery { interactor.selectInAppForPlace(place, queuedOperation) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        emitQueuedOperation()
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        startedPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a new session's ask held until a screen of the app resumes asks the operation it carries once the block's return has asked its place in that session`() {
        val returnPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { returnPass.await() }
        coEvery { interactor.selectInAppForPlace(place, queuedOperation) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        emitQueuedOperation()
        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        handle.isActive = true
        handle.isPausedForBackground = false
        controller.onBlockAppeared(place)
        idleMain()
        isAppPresent = true
        controller.onAppResumedOn(screen())
        returnPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a new session does not ask again a place whose block started in it before the new session was announced, also when a start waited for that session`() {
        val startedPass = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers { startedPass.await() }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        controller.onBlockAppeared(place)
        idleMain()
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        startedPass.complete(winner(configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a new session's ask held until a screen of the app resumes is not made once the block's return has asked its place in that session, also when a start waited for that session`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        controller.onBlockAppeared(place)
        idleMain()
        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        handle.isActive = true
        handle.isPausedForBackground = false
        controller.onBlockAppeared(place)
        idleMain()
        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    private fun failTheFirstSelection(after: CompletableDeferred<Unit>? = null) {
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) {
                after?.await()
                throw IllegalStateException("the selection read no config")
            }
            winner(configVersion = newerConfig)
        }
    }

    @Test
    fun `a new session asks again a place whose block started in it before the new session was announced when that start read no config`() {
        failTheFirstSelection()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
    }

    @Test
    fun `a new session asks again a place whose block started in it before the new session was announced when that start reads no config after the announcement`() {
        val failingStart = CompletableDeferred<Unit>()
        failTheFirstSelection(after = failingStart)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        failingStart.complete(Unit)
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
    }

    @Test
    fun `a new session does not ask again a place whose block started in it and stopped before the new session was announced when that start reads no config after the announcement`() {
        val failingStart = CompletableDeferred<Unit>()
        failTheFirstSelection(after = failingStart)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        handle.isActive = false
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        failingStart.complete(Unit)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
    }

    @Test
    fun `an operation queued behind a start that stood for the new session's ask and then read no config answers as the new session's pass`() {
        val failingStart = CompletableDeferred<Unit>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers {
            failingStart.await()
            throw IllegalStateException("the selection read no config")
        }
        coEvery { interactor.selectInAppForPlace(place, queuedOperation) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        emitQueuedOperation()

        failingStart.complete(Unit)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    private fun withPassesOnAJoinedWorker(action: () -> Unit) {
        workerScope = CoroutineScope(
            object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    thread { block.run() }.join()
                }
            }
        )
        try {
            action()
        } finally {
            workerScope = null
        }
    }

    @Test
    fun `a new session's ask held until a screen of the app resumes is made when the app resumes while the block's return that read no config is still finishing`() {
        failTheFirstSelection()
        val handle = RecordingHandle(isActive = false, isPausedForBackground = true)
        isAppPresent = false
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        handle.isActive = true
        handle.isPausedForBackground = false
        withPassesOnAJoinedWorker { controller.onBlockAppeared(place) }
        shadowOf(Looper.getMainLooper()).runOneTask()

        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
    }

    @Test
    fun `a new session's ask held until a screen of the app resumes is made once the block's return the resume left it to reads no config`() {
        val failingReturn = CompletableDeferred<Unit>()
        failTheFirstSelection(after = failingReturn)
        val handle = RecordingHandle(isActive = false, isPausedForBackground = true)
        isAppPresent = false
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        handle.isActive = true
        handle.isPausedForBackground = false
        controller.onBlockAppeared(place)
        idleMain()
        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        failingReturn.complete(Unit)
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
    }

    @Test
    fun `a block that registers at a place forgotten while its pass flew is asked by the new session that pass was asked in`() {
        val startedPass = CompletableDeferred<EmbeddedResolveOutcome>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) startedPass.await() else winner(configVersion = newerConfig)
        }
        val controller = controller()
        val registration = controller.register(place, keptAlive(RecordingHandle()))
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        registration.close()
        idleMain()
        startedPass.complete(winner(configVersion = newerConfig))
        idleMain()
        val registeredInBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        controller.register(place, registeredInBackground)
        idleMain()

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        assertEquals(2, selections)
        assertEquals(listOf(SessionEpoch(1L)), registeredInBackground.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `a place whose pass already read the config announced while it flies is not asked again for it`() {
        val secondPass = CompletableDeferred<EmbeddedResolveOutcome>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) winner() else secondPass.await()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        announceNewerConfig()
        secondPass.complete(winner(configVersion = newerConfig))
        idleMain()

        assertEquals(2, selections)
    }

    private fun resubscribeTheChannelsWhileTheirPassWaits(handle: RecordingHandle): CompletableDeferred<EmbeddedResolveOutcome> {
        val resubscriptionPass = CompletableDeferred<EmbeddedResolveOutcome>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) resubscriptionPass.await() else winner(variant = contentOfNewerConfig, configVersion = newerConfig)
        }
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.cancel()
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()
        announceNewerConfig()
        return resubscriptionPass
    }

    private val contentOfNewerConfig = content.copy(inAppId = "of-the-newer-config")

    @Test
    fun `a soft re-init asks a place once when the pass of its resubscription already read the config the re-init publishes again`() {
        val handle = RecordingHandle()
        val resubscriptionPass = resubscribeTheChannelsWhileTheirPassWaits(handle)

        resubscriptionPass.complete(winner(variant = contentOfNewerConfig, configVersion = newerConfig))
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(contentOfNewerConfig), handle.received)
    }

    @Test
    fun `a soft re-init asks a place again when the config it publishes again is newer than the one the pass of its resubscription read`() {
        val handle = RecordingHandle()
        val resubscriptionPass = resubscribeTheChannelsWhileTheirPassWaits(handle)

        resubscriptionPass.complete(winner(configVersion = olderConfig))
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content, contentOfNewerConfig), handle.received)
    }

    @Test
    fun `a pass that read no config is asked again once when a config is announced while it flies`() {
        val failingPass = CompletableDeferred<Unit>()
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) {
                failingPass.await()
                throw IllegalStateException("the selection read no config")
            }
            winner(configVersion = newerConfig)
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()

        announceNewerConfig()
        failingPass.complete(Unit)
        idleMain()

        assertEquals(2, selections)
        assertEquals(listOf<InAppType.Embedded?>(null, content), handle.received)
    }

    @Test
    fun `a pass that read no config is not asked again when no config was announced while it flew`() {
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 1) throw IllegalStateException("the selection read no config")
            winner(configVersion = newerConfig)
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        announceNewerConfig()

        controller.onBlockAppeared(place)
        idleMain()

        assertEquals(2, selections)
        assertEquals(listOf<InAppType.Embedded?>(content, null), handle.received)
    }

    private fun cancelTheSdkScopeAndResubscribe(controller: EmbeddedBlocksRegistryImpl) {
        val cancelled = scope
        scope = TestScope(UnconfinedTestDispatcher())
        cancelled.cancel()
        controller.startListening()
        idleMain()
    }

    @Test
    fun `a pass cancelled with the sdk scope after a config was announced while it flew leaves its place to the resubscription's ask`() {
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) awaitCancellation()
            winner(configVersion = newerConfig)
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        announceNewerConfig()

        cancelTheSdkScopeAndResubscribe(controller)

        assertEquals(2, selections)
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `an operation queued behind a pass cancelled with the sdk scope is still asked`() {
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) awaitCancellation()
            winner(configVersion = newerConfig)
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        controller.onBlockAppeared(place)
        idleMain()
        announceNewerConfig()
        emitQueuedOperation()

        cancelTheSdkScopeAndResubscribe(controller)

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, queuedOperation) }
    }

    @Test
    fun `a start that stood for the new session's ask and was cancelled with the sdk scope leaves its place to the resubscription's ask`() {
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (selections++ == 0) awaitCancellation()
            winner(configVersion = newerConfig)
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        controller.onBlockAppeared(place)
        idleMain()
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        cancelTheSdkScopeAndResubscribe(controller)

        assertEquals(2, selections)
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a config announced after a pass read it off the main thread but before its answer reached the main thread does not ask the place again`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(configVersion = newerConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        withPassesOnAJoinedWorker { controller.onBlockAppeared(place) }

        announceNewerConfig()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `a config announced off the main thread asks its places on the main thread`() {
        val askedOnMain = mutableListOf<Boolean>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            askedOnMain.add(Looper.myLooper() == Looper.getMainLooper())
            winner(configVersion = newerConfig)
        }
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle()))
        idleMain()
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker.submit { runBlocking { configVersions.emit(newerConfig) } }.get(5L, TimeUnit.SECONDS)

            assertTrue(askedOnMain.isEmpty())
            idleMain()
            assertEquals(listOf(true), askedOnMain)
        } finally {
            worker.shutdownNow()
        }
    }

    private val winnerWithoutPage = content.copy(layers = emptyList())

    private fun deliverWinnerWithoutPage(handle: RecordingHandle, reservation: PlaceShowReservation = PlaceShowReservation.RESERVED) {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(variant = winnerWithoutPage)
        val controller = controller()
        every { interactor.reservePlaceShow(place, winnerWithoutPage, any()) } returns reservation
        controller.register(place, handle)
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
    }

    @Test
    fun `a winner without a page gives the show it reserved back at once`() {
        val handle = RecordingHandle()

        deliverWinnerWithoutPage(handle)

        verify(exactly = 1) { interactor.reservePlaceShow(place, winnerWithoutPage, SessionEpoch(0L)) }
        verify(exactly = 1) { interactor.releasePlaceShow(place, SessionEpoch(0L)) }
        assertEquals(listOf<InAppType.Embedded?>(winnerWithoutPage), handle.received)
    }

    @Test
    fun `a winner without a page whose show budgets are spent empties the place without a failure`() {
        val handle = RecordingHandle()

        deliverWinnerWithoutPage(handle, PlaceShowReservation.REFUSED)

        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
        verify(exactly = 1) { interactor.releasePlaceShow(place, SessionEpoch(0L)) }
    }

    @Test
    fun `a winner without a page for blocks off screen since the app's return checks the show budgets at its delivery`() {
        val handle = RecordingHandle(isActive = false, isLeftBehind = true)

        deliverWinnerWithoutPage(handle, PlaceShowReservation.REFUSED)

        assertEquals(listOf<InAppType.Embedded?>(null), handle.received)
    }

    @Test
    fun `a delay that ran out while the app was away delivers after the session check of its return, reserving no show for the block the return left off screen`() {
        scope = TestScope(StandardTestDispatcher())
        coEvery { interactor.awaitReturnChecked() } coAnswers { returnCheckPending.first { isPending -> !isPending } }
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner(delay = Milliseconds(5_000L))
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true).apply { leavesOnAppResumed = true }
        val controller = controller()
        controller.register(place, inBackground)
        controller.onBlockAppeared(place)
        idleMain()
        returnCheckPending.value = true
        scope.testScheduler.runCurrent()

        scope.testScheduler.advanceTimeBy(5_001L)
        idleMain()

        assertTrue(inBackground.received.isEmpty())
        verify(exactly = 0) { interactor.markEmbeddedDelayWaitedOut(any(), any(), any()) }

        returnCheckPending.value = false
        controller.onReturnCheckOver()
        controller.onAppResumedOn(screen())
        scope.testScheduler.runCurrent()
        idleMain()

        assertEquals(listOf<InAppType.Embedded?>(content), inBackground.received)
        assertEquals(listOf(false), inBackground.answers.map { answer -> answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `a winner whose delay ran out keeps its delivery when a failure overtakes it on the way to the main thread`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns
            winner(delay = Milliseconds(20L)) andThen EmbeddedResolveOutcome.ConfigUnavailable(olderConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        val worker = Executors.newSingleThreadExecutor()
        workerScope = CoroutineScope(worker.asCoroutineDispatcher())
        try {
            controller.onBlockAppeared(place)
            awaitWorkPostedToMain(worker)
            idleMain()
            awaitWorkPostedToMain(worker)
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
    fun `the same winner re-selected after its delay ran out but before its delivery reached the main thread is delivered once with the selection time that started the delay`() {
        val refreshed = content.copy(
            layers = listOf((content.layers.single() as Layer.WebViewLayer).copy(params = mapOf("items" to "[]")))
        )
        var selections = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            selections++
            if (selections == 1) {
                advance(Milliseconds(320L))
                winner(delay = Milliseconds(20L))
            } else {
                advance(Milliseconds(40L))
                winner(variant = refreshed, delay = Milliseconds(20L))
            }
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        val worker = Executors.newSingleThreadExecutor()
        workerScope = CoroutineScope(worker.asCoroutineDispatcher())
        try {
            controller.onBlockAppeared(place)
            awaitWorkPostedToMain(worker)
            idleMain()
            awaitWorkPostedToMain(worker)
            workerScope = null

            controller.onBlockAppeared(place)
            idleMain()

            assertEquals(listOf<InAppType.Embedded?>(refreshed), handle.received)
            assertEquals(listOf(Milliseconds(320L)), handle.selectionTimes)
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
    fun `a new session while no screen of the app is resumed asks a block away in background once a screen resumes, its selection counted from that ask`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            advance(Milliseconds(50L))
            winner()
        }
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        isAppPresent = false
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        clock = Milliseconds(4_000L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(place, any()) }

        isAppPresent = true
        clock = Milliseconds(6_000L)
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), inBackground.answers.map { answer -> answer.sessionEpoch })
        assertEquals(Milliseconds(50L), inBackground.answers.single().selectionTime)
    }

    @Test
    fun `a new session while the app has no resumed screen holds the ask of a block away in background until a screen resumes`() {
        stubInteractor()
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val screen = screen()
        val lifecycle = LifecycleManager(currentActivityName = null, currentIntent = null, isAppInBackground = false)
        lifecycle.onActivityStarted(screen)
        lifecycle.onActivityResumed(screen)
        lifecycle.onActivityPaused(screen)
        LifecycleManager.instance = lifecycle
        try {
            val controller = EmbeddedBlocksRegistryImpl(inAppInteractor = interactor, scopeProvider = { scope }, monotonicNow = { clock })
            controller.register(place, keptAlive(RecordingHandle(isActive = false, isPausedForBackground = true)))
            idleMain()

            currentSessionEpoch = SessionEpoch(1L)
            sessionEpochs.value = SessionEpoch(1L)
            idleMain()

            coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

            lifecycle.onActivityResumed(screen)
            controller.onAppResumedOn(screen)
            idleMain()

            coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        } finally {
            LifecycleManager.instance = null
        }
    }

    @Test
    fun `a new session's ask held for a block away in background is made once the app resumes on another screen, reserving no show for the block that left`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true).apply { leavesOnAppResumed = true }
        isAppPresent = false
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf<InAppType.Embedded?>(content), inBackground.received)
        assertEquals(listOf(SessionEpoch(1L) to false), inBackground.answers.map { answer -> answer.sessionEpoch to answer.isShowReserved })
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), any()) }
    }

    @Test
    fun `an operation held for the new session keeps its trigger when another new session begins before a screen of the app resumes`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        currentSessionEpoch = SessionEpoch(2L)
        sessionEpochs.value = SessionEpoch(2L)
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(2L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an operation held for the new session is dropped once a screen of the app resumes when its block left the screen with nothing on it`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        handle.isPausedForBackground = false
        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        assertTrue(handle.answers.isEmpty())
    }

    @Test
    fun `a place reserves its show at delivery only while one of its blocks is on screen or away in background`() {
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

        verify(exactly = 1) { interactor.reservePlaceShow(backgroundPlace, content, SessionEpoch(0L)) }
        verify(exactly = 0) { interactor.reservePlaceShow(place, any(), any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(leftPlace, any(), any()) }
        assertEquals(listOf(true), inBackground.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf(false), awayInApp.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf(false), leftOnReturn.answers.map { answer -> answer.isShowReserved })
        assertEquals(listOf<InAppType.Embedded?>(content), leftOnReturn.received)
    }

    @Test
    fun `a show parked for blocks the app's return left is given back only when no block of its place is on screen or away in background`() {
        val sharedPlace = PlaceKey.of("shared-place")
        val placeOnScreen = PlaceKey.of("place-on-screen")
        val onScreen = RecordingHandle()
        val leftBesideOnScreen = RecordingHandle(isActive = false, isLeftBehind = true)
        val controller = controller()
        controller.register(place, keptAlive(RecordingHandle(isActive = false, isLeftBehind = true)))
        controller.register(sharedPlace, keptAlive(RecordingHandle(isActive = false, isLeftBehind = true)))
        controller.register(sharedPlace, keptAlive(RecordingHandle(isActive = false, isPausedForBackground = true)))
        controller.register(placeOnScreen, onScreen)
        controller.register(placeOnScreen, leftBesideOnScreen)
        idleMain()

        controller.giveBackParkedShow(place, SessionEpoch(0L))
        controller.giveBackParkedShow(sharedPlace, SessionEpoch(0L))
        controller.giveBackParkedShow(placeOnScreen, SessionEpoch(0L))

        verify(exactly = 1) { interactor.releasePlaceShow(place, SessionEpoch(0L)) }
        verify(exactly = 0) { interactor.releasePlaceShow(sharedPlace, any()) }
        verify(exactly = 0) { interactor.releasePlaceShow(placeOnScreen, any()) }
    }

    @Test
    fun `an operation that arrives before the session check of the app's return is resolved once the check has run, after its blocks heard of it`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
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

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        val checksHeardBeforeReturn = handle.returnCheckedCount

        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(checksHeardBeforeReturn + 1, checksHeardBeforeAsk)
    }

    @Test
    fun `a block that appears before the session check of the app's return is asked at once`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `content delivered while the session check of the app's return runs reserves the show of a block on screen at once`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true

        controller.onBlockAppeared(place)
        idleMain()

        verify(exactly = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(0L)) }
        assertEquals(listOf(true), handle.answers.map { answer -> answer.isShowReserved })
    }

    @Test
    fun `an operation that arrives during the session check of the app's return reaches the new session's selection with its trigger`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
        isSessionEnding = true
        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an operation held for the session check of the app's return that ends the session rides the new session's ask, also when the reset lands before the check is reported over`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns EmbeddedResolveOutcome.Empty(olderConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(SessionEpoch(1L) to false), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `an operation held for the session check of the app's return that starts no new session is answered as an operation, also after a new session began while the app was away`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        handle.isActive = true
        handle.isPausedForBackground = false
        isAppPresent = true
        returnCheckPending.value = false
        controller.onReturnCheckOver()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(SessionEpoch(1L) to true), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    private fun EmbeddedBlocksRegistryImpl.holdOperationForTheReturnCheck(handle: RecordingHandle, operation: InAppEventType) {
        register(place, handle)
        idleMain()
        returnCheckPending.value = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
    }

    private fun EmbeddedBlocksRegistryImpl.finishTheReturnCheck() {
        returnCheckPending.value = false
        onReturnCheckOver()
        idleMain()
    }

    @Test
    fun `an operation held for the session check of the app's return is dropped once its blocks have left the screen with nothing on them`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, operation)

        handle.isActive = false
        controller.finishTheReturnCheck()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        assertTrue(handle.answers.isEmpty())
    }

    @Test
    fun `an operation held for the session check of the app's return that keeps the session is dropped for a block that left the screen holding its content`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, operation)

        handle.isActive = false
        handle.isHoldingContent = true
        controller.finishTheReturnCheck()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        assertTrue(handle.answers.isEmpty())
    }

    @Test
    fun `an operation held for the session check of the app's return that ends the session rides the new session's ask for a block that left the screen holding its content`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle().apply { leavesOnSessionRenewed = true }
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, operation)

        handle.isActive = false
        handle.isHoldingContent = true
        currentSessionEpoch = SessionEpoch(1L)
        controller.finishTheReturnCheck()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf(SessionEpoch(1L) to false), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `an operation held for the session check of the app's return is asked for a block still away in background with nothing on it`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, operation)

        handle.isActive = false
        handle.isPausedForBackground = true
        controller.finishTheReturnCheck()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
    }

    private val firstHeldOperation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("first-held-operation"))
    private val secondHeldOperation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("second-held-operation"))
    private val thirdHeldOperation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("third-held-operation"))

    private fun triggersAsked(answeringTheFirstWith: CompletableDeferred<EmbeddedResolveOutcome>): List<InAppEventType> {
        val asked = mutableListOf<InAppEventType>()
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            asked.add(secondArg())
            if (asked.size == 1) answeringTheFirstWith.await() else winner()
        }
        return asked
    }

    private fun emitOperation(operation: InAppEventType) {
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()
    }

    private fun EmbeddedBlocksRegistryImpl.holdTwoOperationsForAReturnCheckThatEndsTheSession(handle: RecordingHandle) {
        holdOperationForTheReturnCheck(handle, firstHeldOperation)
        emitOperation(secondHeldOperation)
        isSessionEnding = true
        finishTheReturnCheck()
    }

    @Test
    fun `two operations held for the session check of the app's return that keeps the session are both asked, in the order they arrived`() {
        val firstPass = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = firstPass)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, firstHeldOperation)
        emitOperation(secondHeldOperation)

        controller.finishTheReturnCheck()
        firstPass.complete(winner())
        idleMain()

        assertEquals(listOf<InAppEventType>(firstHeldOperation, secondHeldOperation), asked)
        assertEquals(listOf(true, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `of three operations held for the session check of the app's return, the first and the last are asked`() {
        val firstPass = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = firstPass)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdOperationForTheReturnCheck(handle, firstHeldOperation)
        emitOperation(secondHeldOperation)
        emitOperation(thirdHeldOperation)

        controller.finishTheReturnCheck()
        firstPass.complete(winner())
        idleMain()

        assertEquals(listOf<InAppEventType>(firstHeldOperation, thirdHeldOperation), asked)
        assertEquals(listOf(true, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `of two operations held for the session check of the app's return that ends the session, the first rides the new session's ask and the second follows it as an operation`() {
        val newSessionsPass = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = newSessionsPass)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdTwoOperationsForAReturnCheckThatEndsTheSession(handle)

        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        newSessionsPass.complete(winner())
        idleMain()

        assertEquals(listOf<InAppEventType>(firstHeldOperation, secondHeldOperation), asked)
        assertEquals(
            listOf(SessionEpoch(1L) to false, SessionEpoch(1L) to true),
            handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation },
        )
    }

    @Test
    fun `of two operations carried into a new session's ask held until a screen of the app resumes, the first rides that ask and the second follows it as an operation`() {
        val newSessionsPass = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = newSessionsPass)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdTwoOperationsForAReturnCheckThatEndsTheSession(handle)
        handle.isActive = false
        handle.isPausedForBackground = true
        isAppPresent = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        assertTrue(asked.isEmpty())

        handle.isActive = true
        handle.isPausedForBackground = false
        isAppPresent = true
        controller.onAppResumedOn(screen())
        idleMain()
        newSessionsPass.complete(winner())
        idleMain()

        assertEquals(listOf<InAppEventType>(firstHeldOperation, secondHeldOperation), asked)
        assertEquals(
            listOf(SessionEpoch(1L) to false, SessionEpoch(1L) to true),
            handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation },
        )
    }

    @Test
    fun `an appearance held for a new session after two operations rides that session's ask with the first, and the second still follows it as an operation`() {
        val newSessionsPass = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = newSessionsPass)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdTwoOperationsForAReturnCheckThatEndsTheSession(handle)
        controller.onBlockAppeared(place)
        idleMain()

        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        newSessionsPass.complete(winner())
        idleMain()

        assertEquals(listOf<InAppEventType>(firstHeldOperation, secondHeldOperation), asked)
        assertEquals(
            listOf(SessionEpoch(1L) to false, SessionEpoch(1L) to true),
            handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation },
        )
    }

    private fun EmbeddedBlocksRegistryImpl.holdTwoOperationsForTheReturnCheckWhileAPassOfTheirPlaceFlies(handle: RecordingHandle) {
        holdOperationForTheReturnCheck(handle, firstHeldOperation)
        emitOperation(secondHeldOperation)
        onBlockAppeared(place)
        idleMain()
    }

    @Test
    fun `of two operations held for the session check of the app's return that keeps the session, only the second is asked when a pass of their place is in flight as the check ends`() {
        val passInFlight = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = passInFlight)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdTwoOperationsForTheReturnCheckWhileAPassOfTheirPlaceFlies(handle)

        controller.finishTheReturnCheck()
        passInFlight.complete(winner())
        idleMain()

        assertEquals(listOf(InAppEventType.EmbeddedPlaceRequested(place), secondHeldOperation), asked)
        assertEquals(listOf(false, true), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `of two operations held for the session check of the app's return that ends the session, only the second rides the new session's ask when a pass of their place is in flight as the check ends`() {
        val passInFlight = CompletableDeferred<EmbeddedResolveOutcome>()
        val asked = triggersAsked(answeringTheFirstWith = passInFlight)
        val handle = RecordingHandle()
        val controller = controller()
        controller.holdTwoOperationsForTheReturnCheckWhileAPassOfTheirPlaceFlies(handle)
        isSessionEnding = true
        controller.finishTheReturnCheck()

        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        passInFlight.complete(winner())
        idleMain()

        assertEquals(listOf(InAppEventType.EmbeddedPlaceRequested(place), secondHeldOperation), asked)
        assertEquals(listOf(SessionEpoch(1L) to false), handle.answers.map { answer -> answer.sessionEpoch to answer.isByOperation })
    }

    @Test
    fun `an operation carried into a new session is dropped when its blocks left the screen with nothing on them before the session began`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        isSessionEnding = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        handle.isActive = false
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
        assertTrue(handle.answers.isEmpty())
    }

    @Test
    fun `an appearance that lands while an operation pass is in flight makes that pass's answer a non-operation answer`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        val operationGate = CompletableDeferred<EmbeddedResolveOutcome>()
        coEvery { interactor.selectInAppForPlace(place, operation) } coAnswers { operationGate.await() }
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns EmbeddedResolveOutcome.Empty(olderConfig)
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()
        operationGate.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        assertEquals(listOf(false, false), handle.answers.map { answer -> answer.isByOperation })
    }

    @Test
    fun `a new session's ask queued behind a resolve in flight counts its selection from the renewal`() {
        val firstResolveGate = CompletableDeferred<EmbeddedResolveOutcome>()
        var resolves = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            if (resolves++ == 0) firstResolveGate.await() else winner()
        }
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        clock = Milliseconds(1_000L)
        controller.onBlockAppeared(place)
        idleMain()

        currentSessionEpoch = SessionEpoch(1L)
        clock = Milliseconds(4_000L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
        clock = Milliseconds(9_000L)
        firstResolveGate.complete(winner())
        idleMain()

        assertEquals(listOf(SessionEpoch(1L)), handle.answers.map { answer -> answer.sessionEpoch })
        assertEquals(listOf(Milliseconds(5_000L)), handle.answers.map { answer -> answer.selectionTime })
    }

    @Test
    fun `a session renewal that came while the channels were dead still asks the blocks away in background`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val inBackground = RecordingHandle(isActive = false, isPausedForBackground = true)
        val controller = controller()
        controller.register(place, inBackground)
        idleMain()

        scope.cancel()
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(listOf(SessionEpoch(1L)), inBackground.answers.map { answer -> answer.sessionEpoch })
    }

    @Test
    fun `an operation held for the session check of the app's return waits for the check to be reported over, not for the flag to flip back`() {
        scope = TestScope(StandardTestDispatcher())
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        scope.testScheduler.runCurrent()

        returnCheckPending.value = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        scope.testScheduler.runCurrent()
        idleMain()
        returnCheckPending.value = false
        scope.testScheduler.runCurrent()
        idleMain()

        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }

        controller.onReturnCheckOver()
        scope.testScheduler.runCurrent()
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(listOf<InAppType.Embedded?>(content), handle.received)
    }

    @Test
    fun `channels that resubscribe after the session check of the app's return release what still waits for it`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("block-operation"))
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner()
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()
        returnCheckPending.value = true
        scope.launch { placeEvents.emit(EmbeddedPlaceEvent(place, operation)) }
        idleMain()

        scope.cancel()
        returnCheckPending.value = false
        scope = TestScope(UnconfinedTestDispatcher())
        controller.startListening()
        idleMain()

        assertEquals(1, handle.returnCheckedCount)
        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
    }

    private var isAppInForeground = true
    private val blockScreen: Activity by lazy { screen() }

    private class ShownPage : EmbeddedUpdatableContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(RuntimeEnvironment.getApplication())
        var releaseCount = 0
        val sessionRefreshes = mutableListOf<Pair<SessionEpoch, Milliseconds>>()
        val sessionRefreshParams = mutableListOf<Map<String, String>>()
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
            sessionEpoch: SessionEpoch,
            selectionTime: Milliseconds,
            onResult: (DataPushOutcome) -> Unit,
        ) {
            sessionRefreshes.add(sessionEpoch to selectionTime)
            sessionRefreshParams.add(params)
            onResult(DataPushOutcome.CONFIRMED)
        }

        override fun updateParams(params: Map<String, String>, onResult: (DataPushOutcome) -> Unit) = onResult(DataPushOutcome.CONFIRMED)

        override fun scheduleSessionRefresh(sessionEpoch: SessionEpoch, selectionTime: Milliseconds) = Unit

        override fun confirmForSession(sessionEpoch: SessionEpoch) = Unit

        override fun timeToDisplaySoFar(sessionEpoch: SessionEpoch): Milliseconds? = null

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
            monotonicNow = { clock },
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
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()
    }

    @Test
    fun `a feed the new session refuses is gone before its first frame when the user comes back to it from the screen the app returned on`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty(olderConfig)
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
            listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty),
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
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }

        isAppInForeground = true
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
    }

    @Test
    fun `a feed taken off its screen inside its activity while the app was in background is gone before its first frame when the user comes back to it and the new session refuses it, with no show reserved meanwhile`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty(olderConfig)
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
            listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty),
            states.drop(reportedBeforeReturn),
        )
        assertEquals(1, pages.single().releaseCount)
        assertTrue(pages.single().withheldShows.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
    }

    @Test
    fun `a feed taken off its screen in its activity while the app was in background and still picked by the new session reserves its show on the user's return, its data timed without the wait for it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            advance(Milliseconds(30L))
            winner()
        }
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForBackground()
        block.onLeftScreen()
        clock = Milliseconds(4_000L)

        registry.comeBackOnTheBlockScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
        assertTrue(pages.single().sessionRefreshes.isEmpty())
        val reportedBeforeReturn = states.size

        clock = Milliseconds(9_000L)
        isAppInForeground = true
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(30L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the new session refuses stays on screen until the user leaves it when the app comes back on its own screen`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty(olderConfig)
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForBackground()

        isSessionEnding = true
        isAppPresent = true
        registry.onAppResumedOn(blockScreen)
        idleMain()
        isAppInForeground = true
        block.start()
        idleMain()
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
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
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = EmbeddedResolveOutcome.Empty(olderConfig))
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
        renewalAnswer.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        assertEquals(0, pages.single().releaseCount)
        assertEquals(true, pages.single().withheldShows.lastOrNull())

        block.pause()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty), states.drop(reportedBeforeReturn))
        assertEquals(1, pages.single().releaseCount)
    }

    @Test
    fun `a feed the user comes back to before the new session answers takes the new session's data in place with the whole selection time when the new session still picks it`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForBackground()
        clock = Milliseconds(4_000L)
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        val reportedBeforeReturn = states.size

        clock = Milliseconds(9_000L)
        isAppInForeground = true
        block.start()
        idleMain()
        clock = Milliseconds(11_000L)
        renewalAnswer.complete(winner())
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(7_000L)), pages.single().sessionRefreshes)
        assertEquals(listOf((content.layers.single() as Layer.WebViewLayer).params), pages.single().sessionRefreshParams)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
    }

    @Test
    fun `a feed the user comes back to before the new session begins takes the new session's data with the time of the first selection after the reset`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            advance(Milliseconds(25L))
            winner()
        }
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, mutableListOf(), pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForBackground()
        isSessionEnding = true
        isAppPresent = true
        registry.onAppResumedOn(screen())
        idleMain()

        clock = Milliseconds(9_000L)
        isAppInForeground = true
        block.start()
        idleMain()
        clock = Milliseconds(12_000L)
        isSessionEnding = false
        currentSessionEpoch = SessionEpoch(1L)
        sessionEpochs.value = SessionEpoch(1L)
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(25L)), pages.single().sessionRefreshes)
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

        currentSessionEpoch = SessionEpoch(2L)
        sessionEpochs.value = SessionEpoch(2L)
        idleMain()

        coVerify(exactly = 4) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(2L)) }
    }

    private fun EmbeddedBlockContentController.leaveForAnotherScreen() {
        isAppInForeground = true
        pause()
    }

    @Test
    fun `a feed left for another screen and refused by a new session begun meanwhile is gone before its first frame when the user comes back to it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } returns winner() andThen EmbeddedResolveOutcome.Empty(olderConfig)
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()

        registry.comeBackOnAnotherScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
        val reportedBeforeReturn = states.size

        block.start()
        idleMain()

        assertEquals(
            listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty),
            states.drop(reportedBeforeReturn),
        )
        assertEquals(1, pages.single().releaseCount)
        assertTrue(pages.single().withheldShows.isEmpty())
        assertTrue(pages.single().sessionRefreshes.isEmpty())
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
    }

    @Test
    fun `a feed left for another screen and still picked by a new session begun meanwhile reserves its show only on the user's return, its data timed without the wait for it`() {
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            advance(Milliseconds(30L))
            winner()
        }
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = Milliseconds(4_000L)

        registry.comeBackOnAnotherScreenWithTheSessionOver()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
        val reportedBeforeReturn = states.size

        clock = Milliseconds(9_000L)
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(30L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the user comes back to from another screen before a new session begun meanwhile answers stays on screen when the new session refuses it and collapses once the user leaves it`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = EmbeddedResolveOutcome.Empty(olderConfig))
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        val reportedBeforeReturn = states.size

        block.start()
        idleMain()
        renewalAnswer.complete(EmbeddedResolveOutcome.Empty(olderConfig))
        idleMain()

        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
        assertEquals(0, pages.single().releaseCount)
        assertEquals(true, pages.single().withheldShows.lastOrNull())

        block.pause()

        assertEquals(listOf<EmbeddedBlockState>(EmbeddedBlockState.Empty), states.drop(reportedBeforeReturn))
        assertEquals(1, pages.single().releaseCount)
    }

    @Test
    fun `a feed the user comes back to from another screen before a new session begun meanwhile answers takes the new session's data in place with the whole selection time`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = Milliseconds(4_000L)
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        val reportedBeforeReturn = states.size

        clock = Milliseconds(9_000L)
        block.start()
        idleMain()
        clock = Milliseconds(11_000L)
        renewalAnswer.complete(winner())
        idleMain()

        assertEquals(0, pages.single().releaseCount)
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(7_000L)), pages.single().sessionRefreshes)
        assertEquals(listOf((content.layers.single() as Layer.WebViewLayer).params), pages.single().sessionRefreshParams)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a feed the user comes back to and leaves again before a new session begun meanwhile answers reserves no show until the user comes back once more, and leaves the time until that return out`() {
        val renewalAnswer = CompletableDeferred<EmbeddedResolveOutcome>()
        answerTheRenewalOnlyWhen(renewalAnswer, laterAnswer = winner())
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val registry = controller()
        val block = liveBlock(registry, states, pages)
        clock = Milliseconds(1_000L)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()
        clock = Milliseconds(4_000L)
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        clock = Milliseconds(9_000L)
        block.start()
        idleMain()
        block.leaveForAnotherScreen()

        clock = Milliseconds(11_000L)
        renewalAnswer.complete(winner())
        idleMain()

        coVerify(exactly = 3) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
        val reportedBeforeReturn = states.size

        clock = Milliseconds(15_000L)
        block.start()
        idleMain()

        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(1L)) }
        assertEquals(listOf(SessionEpoch(1L) to Milliseconds(7_000L)), pages.single().sessionRefreshes)
        assertEquals(0, pages.single().releaseCount)
        assertEquals(emptyList<EmbeddedBlockState>(), states.drop(reportedBeforeReturn))
    }

    @Test
    fun `a block that leaves its screen for another one and comes back in the same session is asked only on its return and reserves the show of an answer that reached it away only then`() {
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
        verify(exactly = 0) { interactor.reservePlaceShow(place, any(), any()) }

        block.start()
        idleMain()

        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(atLeast = 1) { interactor.reservePlaceShow(place, content, SessionEpoch(0L)) }
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
        registry.comeBackOnAnotherScreenWithTheSessionOver()
        coVerify(exactly = 2) { interactor.selectInAppForPlace(place, any()) }
        verify(exactly = 0) { interactor.releasePlaceShow(place) }

        block.release()
        idleMain()

        verify(exactly = 0) { interactor.reservePlaceShow(any(), any(), SessionEpoch(1L)) }
        verify(atLeast = 1) { interactor.releasePlaceShow(place) }
    }

    private val givenBackWinner = content.copy(inAppId = "given-back-winner")

    private fun blockOnAnotherScreenWhoseSiblingGaveBackTheShow(
        states: MutableList<EmbeddedBlockState>,
        pages: MutableList<ShownPage>,
    ): EmbeddedBlockContentController {
        val answerInBackground = CompletableDeferred<EmbeddedResolveOutcome>()
        val reAsk = CompletableDeferred<EmbeddedResolveOutcome>()
        var resolves = 0
        coEvery { interactor.selectInAppForPlace(place, any()) } coAnswers {
            when (resolves++) {
                0 -> winner()
                1 -> answerInBackground.await()
                else -> reAsk.await()
            }
        }
        val registry = controller()
        val onAnotherScreen = liveBlock(registry, states, pages)
        onAnotherScreen.start()
        idleMain()
        onAnotherScreen.leaveForAnotherScreen()
        val onScreen = liveBlock(registry, mutableListOf())
        onScreen.start()
        idleMain()
        onScreen.leaveForBackground()
        answerInBackground.complete(winner(givenBackWinner))
        idleMain()
        verify(exactly = 1) { interactor.reservePlaceShow(place, givenBackWinner, SessionEpoch(0L)) }

        isAppPresent = true
        registry.onAppResumedOn(screen())
        idleMain()

        verify(exactly = 1) { interactor.releasePlaceShow(place, SessionEpoch(0L)) }
        isAppInForeground = true
        return onAnotherScreen
    }

    @Test
    fun `a block on another screen of the app takes the show again for content its place got while the app was away once the return gives that show back, and shows it`() {
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val block = blockOnAnotherScreenWhoseSiblingGaveBackTheShow(states, pages)

        block.start()
        idleMain()

        verify(exactly = 2) { interactor.reservePlaceShow(place, givenBackWinner, SessionEpoch(0L)) }
        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals(2, pages.size)
    }

    @Test
    fun `a block on another screen of the app leaves its place empty for content its place got while the app was away when the show the return gave back is spent by then`() {
        val states = mutableListOf<EmbeddedBlockState>()
        val pages = mutableListOf<ShownPage>()
        val block = blockOnAnotherScreenWhoseSiblingGaveBackTheShow(states, pages)
        every { interactor.reservePlaceShow(place, givenBackWinner, SessionEpoch(0L)) } returns PlaceShowReservation.REFUSED

        block.start()
        idleMain()

        verify(exactly = 2) { interactor.reservePlaceShow(place, givenBackWinner, SessionEpoch(0L)) }
        assertEquals(EmbeddedBlockState.Empty, states.last())
        assertEquals(1, pages.size)
        assertEquals(1, pages.single().releaseCount)
    }
}
