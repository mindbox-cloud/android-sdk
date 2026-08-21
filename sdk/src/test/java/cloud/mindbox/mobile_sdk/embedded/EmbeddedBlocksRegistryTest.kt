package cloud.mindbox.mobile_sdk.embedded

import android.os.Looper
import cloud.mindbox.mobile_sdk.inapp.domain.models.EmbeddedPlaceEvent
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The central controller is deliberately dumb: a registry and a router. Both directions call
 * the same interactor code — no selection filter is ever invoked from here.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlocksRegistryTest {

    private class RecordingHandle(override var isActive: Boolean = true) : EmbeddedBlockHandle {
        val received = mutableListOf<InAppType.Embedded?>()

        override fun onContentResolved(content: InAppType.Embedded?) {
            received.add(content)
        }
    }

    private val interactor: InAppInteractor = mockk(relaxed = true)

    // A var, like the SDK scope it stands for: soft re-initialization cancels it and puts a fresh
    // one in its place.
    private var scope = TestScope(UnconfinedTestDispatcher())

    private val place = "main-screen-top"
    private val content = InAppStub.getEmbedded()

    private val placeEvents = MutableSharedFlow<EmbeddedPlaceEvent>()
    private val configUpdates = MutableSharedFlow<Unit>()

    private fun controller(): EmbeddedBlocksRegistryImpl {
        coEvery { interactor.listenEmbeddedPlaceEvents() } returns placeEvents
        coEvery { interactor.listenConfigUpdates() } returns configUpdates
        return EmbeddedBlocksRegistryImpl(
            inAppInteractor = interactor,
            scopeProvider = { scope },
        )
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `block appearance pulls content for its place`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        coEvery { interactor.selectInAppForPlace(place, operation) } returns content
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
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        val handle = RecordingHandle()
        val controller = controller()
        controller.register(place, handle)
        idleMain()

        scope.launch { placeEvents.emit(EmbeddedPlaceEvent("nobody-registered-here", operation)) }
        idleMain()

        assertTrue(handle.received.isEmpty())
        coVerify(exactly = 0) { interactor.selectInAppForPlace(any(), any()) }
    }

    @Test
    fun `operation for a paused place is skipped and the next appearance resolves fresh`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        coEvery { interactor.selectInAppForPlace(place, operation) } returns content
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
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
        val controller = controller()
        controller.register(place, RecordingHandle())
        idleMain()

        controller.onBlockAppeared(place)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) }
        coVerify(exactly = 1) { interactor.listenEmbeddedPlaceEvents() }
        coVerify(exactly = 1) { interactor.listenConfigUpdates() }
        coVerify(exactly = 0) { interactor.getInAppToShowById(any()) }
        coVerify(exactly = 0) { interactor.filterShowableInAppIds(any()) }
    }

    @Test
    fun `new config re-resolves places with an active block`() {
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
    fun `operation queued while resolving keeps its trigger for the second pass`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        val firstResolveGate = CompletableDeferred<InAppType.Embedded?>()
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } coAnswers { firstResolveGate.await() }
        coEvery { interactor.selectInAppForPlace(place, operation) } returns content
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
        firstResolveGate.complete(null)
        idleMain()

        coVerify(exactly = 1) { interactor.selectInAppForPlace(place, operation) }
        assertEquals(content, handle.received.last())
    }

    @Test
    fun `channels resubscribe after the SDK scope is recreated`() {
        val operation = InAppEventType.OrdinalEvent(EventType.AsyncOperation("story-operation"))
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
        coEvery { interactor.selectInAppForPlace(place, operation) } returns content
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
        coEvery { interactor.selectInAppForPlace(place, InAppEventType.EmbeddedPlaceRequested(place)) } returns content
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
}
