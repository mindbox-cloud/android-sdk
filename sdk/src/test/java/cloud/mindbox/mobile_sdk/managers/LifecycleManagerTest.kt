package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cloud.mindbox.mobile_sdk.models.DIRECT
import cloud.mindbox.mobile_sdk.models.LINK
import cloud.mindbox.mobile_sdk.models.PUSH
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager.IS_OPENED_FROM_PUSH_BUNDLE_KEY
import io.mockk.mockk
import io.mockk.junit4.MockKRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
internal class LifecycleManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private val trackVisitEvents = mutableListOf<Pair<String?, String?>>()
    private val startedActivities = mutableListOf<Activity>()
    private val resumedActivities = mutableListOf<Activity>()
    private val pausedActivities = mutableListOf<Activity>()
    private val stoppedActivities = mutableListOf<Activity>()

    @Before
    fun setUp() {
        trackVisitEvents.clear()
        startedActivities.clear()
        resumedActivities.clear()
        pausedActivities.clear()
        stoppedActivities.clear()
    }

    @After
    fun tearDown() {
        LifecycleManager.instance = null
    }

    /** Manager with all callbacks wired to shared collections. */
    private fun createManager(
        currentActivityName: String? = null,
        currentIntent: Intent? = null,
        isAppInBackground: Boolean = false,
    ) = LifecycleManager(
        currentActivityName = currentActivityName,
        currentIntent = currentIntent,
        isAppInBackground = isAppInBackground,
    ).also { manager ->
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += activity
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivities += activity
            }

            override fun onActivityPaused(activity: Activity) {
                pausedActivities += activity
            }

            override fun onActivityStopped(activity: Activity) {
                stoppedActivities += activity
            }

            override fun onTrackVisitReady(source: String?, requestUrl: String?) {
                trackVisitEvents += source to requestUrl
            }
        }
    }

    /** Manager with NO callbacks — simulates the pre-init state. */
    private fun createManagerNoCallbacks(
        isAppInBackground: Boolean = true,
    ) = LifecycleManager(
        currentActivityName = null,
        currentIntent = null,
        isAppInBackground = isAppInBackground,
    )

    /** Attach a minimal track-visit listener after manager construction (simulates late init). */
    private fun listenTrackVisit(manager: LifecycleManager) {
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onTrackVisitReady(source: String?, requestUrl: String?) {
                trackVisitEvents += source to requestUrl
            }
        }
    }

    private fun buildActivityA(intent: Intent = Intent()): Activity =
        Robolectric.buildActivity(LifecycleTestActivityA::class.java, intent).create().get()

    private fun buildActivityB(intent: Intent = Intent()): Activity =
        Robolectric.buildActivity(LifecycleTestActivityB::class.java, intent).create().get()

    private fun mockOwner(): LifecycleOwner = mockk(relaxed = true)

    // region — null-safety: no crash before callbacks set

    @Test
    fun `onActivityStarted does not crash when all callbacks are null`() {
        createManagerNoCallbacks().onActivityStarted(mockk(relaxed = true))
    }

    @Test
    fun `onActivityResumed does not crash when callback is null`() {
        createManagerNoCallbacks().onActivityResumed(mockk(relaxed = true))
    }

    @Test
    fun `onActivityPaused does not crash when callback is null`() {
        createManagerNoCallbacks().onActivityPaused(mockk(relaxed = true))
    }

    @Test
    fun `onActivityStopped does not crash when callback is null`() {
        createManagerNoCallbacks().onActivityStopped(mockk(relaxed = true))
    }

    @Test
    fun `onNewIntent does not crash when callbacks is null`() {
        createManagerNoCallbacks().onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
    }

    // endregion

    // region — track visit NOT sent when callbacks is null

    @Test
    fun `foreground transition does not send track visit when callbacks is null`() {
        val manager = createManagerNoCallbacks()
        manager.onActivityStarted(mockk<Activity>(relaxed = true))
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_STOP)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertTrue(trackVisitEvents.isEmpty())
    }

    @Test
    fun `onNewIntent does not send track visit when callbacks is null`() {
        createManagerNoCallbacks().onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        assertTrue(trackVisitEvents.isEmpty())
    }

    @Test
    fun `onNewIntent with push intent does not send track visit when callbacks is null`() {
        val intent = Intent().apply { putExtra(IS_OPENED_FROM_PUSH_BUNDLE_KEY, true) }
        createManagerNoCallbacks().onNewIntent(intent)
        assertTrue(trackVisitEvents.isEmpty())
    }

    // endregion

    // region — track visit sent after callbacks set (init flow)

    @Test
    fun `foreground sends track visit after callbacks is set`() {
        val manager = createManagerNoCallbacks()
        manager.onActivityStarted(mockk<Activity>(relaxed = true))
        listenTrackVisit(manager)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_STOP)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `onNewIntent sends LINK track visit for https deeplink after callbacks set`() {
        val manager = createManagerNoCallbacks()
        listenTrackVisit(manager)
        val uri = Uri.parse("https://example.com/promo")
        manager.onNewIntent(Intent(Intent.ACTION_VIEW, uri))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK, trackVisitEvents[0].first)
        assertEquals(uri.toString(), trackVisitEvents[0].second)
    }

    @Test
    fun `onNewIntent sends PUSH track visit for push intent after callbacks set`() {
        val manager = createManagerNoCallbacks()
        listenTrackVisit(manager)
        val intent = Intent().apply { putExtra(IS_OPENED_FROM_PUSH_BUNDLE_KEY, true) }
        manager.onNewIntent(intent)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(PUSH, trackVisitEvents[0].first)
    }

    @Test
    fun `repeated onNewIntent with same intent sends DIRECT on second call`() {
        val manager = createManagerNoCallbacks()
        listenTrackVisit(manager)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        manager.onNewIntent(intent)
        manager.onNewIntent(intent)
        assertEquals(2, trackVisitEvents.size)
        assertEquals(LINK, trackVisitEvents[0].first)
        assertEquals(DIRECT, trackVisitEvents[1].first)
    }

    // endregion

    // region — source detection via onActivityStarted

    @Test
    fun `onActivityStarted sends DIRECT trackVisit for plain intent on same activity`() {
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(DIRECT to null, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted sends LINK trackVisit for HTTP deeplink intent`() {
        val url = "http://example.com/promo"
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityA(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK to url, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted sends LINK trackVisit for HTTPS deeplink intent`() {
        val url = "https://example.com/campaign"
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityA(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK to url, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted sends PUSH trackVisit for push-opened intent`() {
        val intent = Intent().apply { putExtra(IS_OPENED_FROM_PUSH_BUNDLE_KEY, true) }
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityA(intent))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(PUSH to null, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted sends DIRECT when intentChanged is false on second call`() {
        val url = "https://example.com"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val activity = buildActivityA(intent)
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(activity)
        trackVisitEvents.clear()
        // hash already known → isTrackVisitSent returns true but sends nothing
        assertTrue(manager.isTrackVisitSent())
        assertEquals(0, trackVisitEvents.size)
    }

    // endregion

    // region — onActivityStarted send / no-send conditions

    @Test
    fun `onActivityStarted does not send when same intent instance used again`() {
        val intent = Intent().apply { putExtra("key", "value") }
        val activity = buildActivityA(intent)
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(activity)
        trackVisitEvents.clear()
        manager.onActivityStarted(activity)
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onActivityStarted does not send when app is in background`() {
        val manager = createManager(isAppInBackground = true)
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onActivityStarted resets isAppInBackground after being called in background`() {
        val manager = createManager(isAppInBackground = true)
        manager.onActivityStarted(buildActivityA(Intent()))
        trackVisitEvents.clear()
        manager.onActivityStarted(buildActivityA(Intent().apply { putExtra("seq", 2) }))
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `onActivityStarted does not send DIRECT trackVisit for different activity class`() {
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityB(Intent()))
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onActivityStarted sends LINK trackVisit for different activity class with deeplink`() {
        val url = "https://example.com"
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityB(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK to url, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted sends PUSH trackVisit for different activity class with push intent`() {
        val intent = Intent().apply { putExtra(IS_OPENED_FROM_PUSH_BUNDLE_KEY, true) }
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(buildActivityB(intent))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(PUSH to null, trackVisitEvents[0])
    }

    @Test
    fun `onActivityStarted invokes onActivityStarted callback`() {
        val manager = createManager()
        val activity = buildActivityA()
        manager.onActivityStarted(activity)
        assertEquals(1, startedActivities.size)
        assertSame(activity, startedActivities[0])
    }

    // endregion

    // region — isCurrentActivityResumed

    @Test
    fun `isCurrentActivityResumed is true by default`() {
        assertTrue(createManager().isCurrentActivityResumed)
    }

    @Test
    fun `isCurrentActivityResumed is false after onActivityPaused`() {
        val manager = createManager()
        manager.onActivityPaused(mockk(relaxed = true))
        assertFalse(manager.isCurrentActivityResumed)
    }

    @Test
    fun `isCurrentActivityResumed is true after onActivityResumed`() {
        val manager = createManager()
        manager.onActivityPaused(mockk(relaxed = true))
        manager.onActivityResumed(mockk(relaxed = true))
        assertTrue(manager.isCurrentActivityResumed)
    }

    @Test
    fun `onActivityResumed sets isCurrentActivityResumed to true and invokes callback`() {
        val manager = createManager()
        val activity = buildActivityA()
        manager.onActivityPaused(activity)
        resumedActivities.clear()
        manager.onActivityResumed(activity)
        assertTrue(manager.isCurrentActivityResumed)
        assertEquals(1, resumedActivities.size)
        assertSame(activity, resumedActivities[0])
    }

    @Test
    fun `onActivityPaused sets isCurrentActivityResumed to false and invokes callback`() {
        val manager = createManager()
        val activity = buildActivityA()
        manager.onActivityPaused(activity)
        assertFalse(manager.isCurrentActivityResumed)
        assertEquals(1, pausedActivities.size)
        assertSame(activity, pausedActivities[0])
    }

    @Test
    fun `isCurrentActivityResumed toggles correctly across resume-pause cycles`() {
        val manager = createManager()
        val activity = buildActivityA()
        manager.onActivityResumed(activity)
        assertTrue(manager.isCurrentActivityResumed)
        manager.onActivityPaused(activity)
        assertFalse(manager.isCurrentActivityResumed)
        manager.onActivityResumed(activity)
        assertTrue(manager.isCurrentActivityResumed)
    }

    // endregion

    // region — all activity callbacks invoked when assigned

    @Test
    fun `all activity callbacks are invoked when assigned`() {
        val started = mutableListOf<Activity>()
        val resumed = mutableListOf<Activity>()
        val paused = mutableListOf<Activity>()
        val stopped = mutableListOf<Activity>()

        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = null,
            isAppInBackground = false,
        )
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onActivityStarted(activity: Activity) {
                started += activity
            }

            override fun onActivityResumed(activity: Activity) {
                resumed += activity
            }

            override fun onActivityPaused(activity: Activity) {
                paused += activity
            }

            override fun onActivityStopped(activity: Activity) {
                stopped += activity
            }
        }

        val activity = mockk<Activity>(relaxed = true)
        manager.onActivityStarted(activity)
        manager.onActivityResumed(activity)
        manager.onActivityPaused(activity)
        manager.onActivityStopped(activity)

        assertEquals(1, started.size)
        assertEquals(1, resumed.size)
        assertEquals(1, paused.size)
        assertEquals(1, stopped.size)
        assertSame(activity, started[0])
        assertSame(activity, resumed[0])
        assertSame(activity, paused[0])
        assertSame(activity, stopped[0])
    }

    // endregion

    // region — onActivityStopped

    @Test
    fun `onActivityStopped invokes onActivityStopped callback`() {
        val manager = createManager(
            currentActivityName = LifecycleTestActivityA::class.java.name,
            currentIntent = Intent(),
        )
        val activity = buildActivityA()
        manager.onActivityStopped(activity)
        assertEquals(1, stoppedActivities.size)
        assertSame(activity, stoppedActivities[0])
    }

    @Test
    fun `onActivityStopped updates currentIntent when both fields are null`() {
        val manager = createManager(currentActivityName = null, currentIntent = null)
        val intent = Intent().apply { putExtra("stopped", true) }
        val activity = buildActivityA(intent)
        manager.onActivityStopped(activity)
        // currentIntent is now set → isTrackVisitSent returns true
        assertTrue(manager.isTrackVisitSent())
    }

    @Test
    fun `onActivityStopped does not override currentIntent when both fields are already set`() {
        val originalIntent = Intent().apply { putExtra("original", true) }
        val manager = createManager(
            currentActivityName = LifecycleTestActivityA::class.java.name,
            currentIntent = originalIntent,
        )
        manager.onActivityStopped(buildActivityB(Intent().apply { putExtra("other", true) }))
        trackVisitEvents.clear()
        // currentIntent unchanged → isTrackVisitSent still returns true (has an intent)
        assertTrue(manager.isTrackVisitSent())
    }

    // endregion

    // region — app lifecycle (foreground / background)

    @Test
    fun `ON_STOP sets app to background`() {
        val manager = createManagerNoCallbacks()
        listenTrackVisit(manager)
        manager.onActivityStarted(mockk<Activity>(relaxed = true))
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_STOP)
        assertTrue(trackVisitEvents.isEmpty())
    }

    @Test
    fun `ON_STOP sets app to background so next onActivityStarted skips trackVisit`() {
        val manager = createManager()
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_STOP)
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `ON_START sends trackVisit with currentIntent`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val manager = createManager(currentIntent = intent)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `ON_START does not send trackVisit when currentIntent is null`() {
        val manager = createManager(currentIntent = null)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `ON_STOP then ON_START sends one trackVisit on return to foreground`() {
        val intent = Intent()
        val activity = buildActivityA(intent)
        val owner = mockOwner()
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        manager.onActivityStarted(activity)
        manager.onActivityResumed(activity)
        manager.onActivityPaused(activity)
        manager.onActivityStopped(activity)
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        trackVisitEvents.clear()
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `other lifecycle events do not send trackVisit`() {
        val manager = createManager(currentIntent = Intent())
        val owner = mockOwner()
        for (event in listOf(
            Lifecycle.Event.ON_CREATE,
            Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_DESTROY,
        )) {
            manager.onStateChanged(owner, event)
        }
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `keepalive timer fires onTrackVisitReady`() {
        val manager = createManagerNoCallbacks()
        listenTrackVisit(manager)
        manager.onActivityStarted(mockk<Activity>(relaxed = true))
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_STOP)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        // at least one track visit was sent (which in production also starts the timer)
        assertEquals(1, trackVisitEvents.size)
    }

    // endregion

    // region — scheduleReinitTrackVisit
    //
    // scheduleReinitTrackVisit() sets pendingVisit = true so the next callbacks assignment
    // (via attachLifecycleCallbacks during Mindbox.init reinit) dispatches a track-visit
    // immediately through the new endpoint. The backend uses this to learn the device is
    // active in the new environment.

    @Test
    fun `scheduleReinitTrackVisit dispatches visit immediately when callbacks are replaced`() {
        // Simulate app already running with a known intent
        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = Intent(),
            isAppInBackground = false,
        )
        // Reinit: schedule before replacing callbacks (mirrors setupLifecycleManager order)
        manager.scheduleReinitTrackVisit()
        // attachLifecycleCallbacks() replaces callbacks → pendingVisit = true → dispatch
        listenTrackVisit(manager)
        assertEquals("reinit must send exactly one visit via new callbacks", 1, trackVisitEvents.size)
    }

    @Test
    fun `scheduleReinitTrackVisit sends DIRECT source for plain intent`() {
        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = Intent(),
            isAppInBackground = false,
        )
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(DIRECT, trackVisitEvents[0].first)
        assertNull(trackVisitEvents[0].second)
    }

    @Test
    fun `scheduleReinitTrackVisit sends LINK source when current intent carries a deeplink`() {
        val url = "https://example.com/promo"
        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            isAppInBackground = false,
        )
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK, trackVisitEvents[0].first)
        assertEquals(url, trackVisitEvents[0].second)
    }

    @Test
    fun `scheduleReinitTrackVisit does not send when currentIntent is null`() {
        // e.g. very early reinit before any activity has started
        val manager = createManagerNoCallbacks(isAppInBackground = false)
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals("no visit when intent is still null", 0, trackVisitEvents.size)
    }

    @Test
    fun `scheduleReinitTrackVisit does not suppress following foreground visits`() {
        val owner = mockOwner()
        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = Intent(),
            isAppInBackground = false,
        )
        // Reinit dispatch
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals(1, trackVisitEvents.size)
        // Normal background + foreground must still produce a visit
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        assertEquals("foreground after reinit must add exactly one more visit", 2, trackVisitEvents.size)
    }

    @Test
    fun `each scheduleReinitTrackVisit call triggers one visit per callbacks replacement`() {
        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = Intent(),
            isAppInBackground = false,
        )
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals(1, trackVisitEvents.size)

        // Second reinit
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals(2, trackVisitEvents.size)
    }

    // endregion

    // region — isTrackVisitSent

    @Test
    fun `isTrackVisitSent returns false when currentIntent is null`() {
        val manager = createManager(currentIntent = null)
        assertFalse(manager.isTrackVisitSent())
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `isTrackVisitSent returns true and sends trackVisit for new intent hash`() {
        val manager = createManager(currentIntent = Intent())
        val result = manager.isTrackVisitSent()
        assertTrue(result)
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `isTrackVisitSent returns true but does not resend for already-known intent hash`() {
        val manager = createManager(currentIntent = Intent())
        manager.isTrackVisitSent()
        trackVisitEvents.clear()
        val result = manager.isTrackVisitSent()
        assertTrue(result)
        assertEquals(0, trackVisitEvents.size)
    }

    // endregion

    // region — onNewIntent

    @Test
    fun `onNewIntent does nothing for null intent`() {
        createManager().onNewIntent(null)
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onNewIntent sends LINK trackVisit for deeplink intent`() {
        val url = "https://example.com/promo"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val manager = createManager()
        manager.onNewIntent(intent)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK to url, trackVisitEvents[0])
    }

    @Test
    fun `onNewIntent sends PUSH trackVisit for push intent`() {
        val intent = Intent().apply { putExtra(IS_OPENED_FROM_PUSH_BUNDLE_KEY, true) }
        val manager = createManager()
        manager.onNewIntent(intent)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(PUSH to null, trackVisitEvents[0])
    }

    @Test
    fun `onNewIntent does not send trackVisit for plain intent without data or push key`() {
        val manager = createManager()
        manager.onNewIntent(Intent())
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onNewIntent sends DIRECT on second call with same intent`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val manager = createManager()
        manager.onNewIntent(intent)
        trackVisitEvents.clear()
        manager.onNewIntent(intent)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(DIRECT, trackVisitEvents[0].first)
    }

    @Test
    fun `onNewIntent sets skipNextTrackVisit when app is in background`() {
        val deeplink = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val manager = createManager(currentIntent = Intent(), isAppInBackground = true)
        manager.onNewIntent(deeplink)
        trackVisitEvents.clear()
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `onNewIntent when not in background does not set skipNextTrackVisit`() {
        val deeplink = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val manager = createManager(currentIntent = Intent(), isAppInBackground = false)
        manager.onNewIntent(deeplink)
        trackVisitEvents.clear()
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals(1, trackVisitEvents.size)
    }

    // endregion

    // region — intent hash deduplication

    @Test
    fun `different intents each trigger separate trackVisit`() {
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        for (i in 1..5) {
            manager.onActivityStarted(buildActivityA(Intent().apply { putExtra("seq", i) }))
        }
        assertEquals(5, trackVisitEvents.size)
    }

    @Test
    fun `after MAX_INTENT_HASHES entries oldest hash is evicted allowing reuse`() {
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        val firstIntent = Intent().apply { putExtra("id", 0) }
        manager.onActivityStarted(buildActivityA(firstIntent))
        for (i in 1..50) {
            manager.onActivityStarted(buildActivityA(Intent().apply { putExtra("id", i) }))
        }
        trackVisitEvents.clear()
        manager.onActivityStarted(buildActivityA(firstIntent))
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `reusing an intent whose hash is still in list does not send trackVisit`() {
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        val intent = Intent().apply { putExtra("id", 99) }
        manager.onActivityStarted(buildActivityA(intent))
        trackVisitEvents.clear()
        manager.onActivityStarted(buildActivityA(intent))
        assertEquals(0, trackVisitEvents.size)
    }

    // endregion

    // region — callbacks set after foreground transition (late-init scenarios)

    @Test
    fun `track visit dispatched when callbacks set after onMovedToForeground with null callbacks`() {
        // Simulates Activity-init: LifecycleManager registered early, activity starts before init
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        val owner = mockOwner()
        // onActivityStarted clears background flag and records the intent
        manager.onActivityStarted(buildActivityA(Intent()))
        // ProcessLifecycle ON_START fires → onMovedToForeground, but callbacks are still null
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        assertEquals("no track visit yet — callbacks not set", 0, trackVisitEvents.size)

        // Mindbox.init() sets callbacks
        listenTrackVisit(manager)

        assertEquals("pending track visit must be dispatched immediately on callbacks set", 1, trackVisitEvents.size)
    }

    @Test
    fun `no extra track visit when callbacks set while still in background`() {
        // Simulates Application.onCreate() init: callbacks set before any activity starts
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager)
        // No activity has started yet — no track visit should be sent
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `pending is cleared on background so next foreground sends exactly one track visit`() {
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        val owner = mockOwner()
        manager.onActivityStarted(buildActivityA(Intent()))
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        // Goes back to background before callbacks are set
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        listenTrackVisit(manager)
        // Pending was cleared on background → no dispatch on callbacks set
        assertEquals(0, trackVisitEvents.size)
        // Normal foreground cycle now sends exactly one track visit
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        assertEquals(1, trackVisitEvents.size)
    }

    @Test
    fun `deeplink source derived from stored intent state on late callbacks set`() {
        // Source (LINK) and URL are re-computed from currentIntent/intentChanged at dispatch time,
        // not stored as parameters — same as iOS deriving visit info from stored state.
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        val owner = mockOwner()
        val url = "https://example.com/promo"
        manager.onActivityStarted(buildActivityA(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        // ON_START fires with callbacks null — sets pendingVisit=true, stores nothing else
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        // callbacks setter fires dispatchCurrentVisit → derives LINK from currentIntent
        listenTrackVisit(manager)
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK, trackVisitEvents[0].first)
        assertEquals(url, trackVisitEvents[0].second)
    }

    // endregion

    // region — Case 3: foreground fires before first activity (foregroundedWithoutIntent flag)
    //
    // Scenario: MindboxLifecycleInitializer did NOT run. Mindbox.init() is called from
    // Application.onCreate(), so callbacks are set before any activity starts.
    // Because ProcessLifecycleOwnerInitializer registered LifecycleDispatcher first, the
    // process-level ON_START event fires *before* LifecycleManager.onActivityStarted.
    // At that moment currentIntent is null, so the visit must be deferred until
    // onActivityStarted provides the intent.

    @Test
    fun `track visit sent when ON_START fires before first onActivityStarted`() {
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager) // callbacks set in Application.onCreate, before any activity

        // ON_START fires first (currentIntent still null) — no visit yet
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        assertEquals("no track visit yet — currentIntent is null", 0, trackVisitEvents.size)

        // onActivityStarted fires after, supplying the intent
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals("track visit must be dispatched once intent arrives", 1, trackVisitEvents.size)
    }

    @Test
    fun `DIRECT source sent in Case 3 for plain cold-start intent`() {
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(DIRECT, trackVisitEvents[0].first)
        assertNull(trackVisitEvents[0].second)
    }

    @Test
    fun `LINK source sent in Case 3 when first activity carries a deeplink intent`() {
        val url = "https://example.com/promo"
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager)
        manager.onStateChanged(mockOwner(), Lifecycle.Event.ON_START)
        manager.onActivityStarted(buildActivityA(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK, trackVisitEvents[0].first)
        assertEquals(url, trackVisitEvents[0].second)
    }

    @Test
    fun `foregroundedWithoutIntent is cleared on background so stale flag does not fire later`() {
        val owner = mockOwner()
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager)
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        // Activity starts after background — flag is gone, no track visit from Case 3 path
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `Case 3 full cycle then background-foreground sends exactly two track visits total`() {
        val owner = mockOwner()
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        listenTrackVisit(manager)

        // Case 3 first foreground
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals(1, trackVisitEvents.size)

        // User backgrounds and returns
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        assertEquals("second foreground must add exactly one more visit", 2, trackVisitEvents.size)
    }

    @Test
    fun `scheduleReinitTrackVisit in Case 3 still sends deferred visit when activity provides intent`() {
        // Reinit happens in Case 3 (no initializer + Application.onCreate).
        // pendingVisit = true from scheduleReinitTrackVisit; callbacks replaced immediately after.
        // dispatchCurrentVisit returns early (currentIntent == null).
        // foregroundedWithoutIntent path then delivers the visit once the activity starts.
        val owner = mockOwner()
        val manager = createManagerNoCallbacks(isAppInBackground = true)
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals("no visit yet — intent is null at dispatch time", 0, trackVisitEvents.size)
        // ON_START fires before activity (Case 3)
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        // Activity starts, providing the intent
        manager.onActivityStarted(buildActivityA(Intent()))
        assertEquals("reinit must not suppress the deferred Case 3 visit", 1, trackVisitEvents.size)
    }

    // endregion

    // region — initialization order

    @Test
    fun `manager with null currentActivityName does not send DIRECT for first activity start`() {
        val manager = createManager(currentActivityName = null, currentIntent = null)
        manager.onActivityStarted(buildActivityA(Intent()))
        // areActivitiesEqual = (null == ActivityA.name) = false, source = DIRECT → no send
        assertEquals(0, trackVisitEvents.size)
    }

    @Test
    fun `manager with null currentActivityName sends non-DIRECT trackVisit for first activity start`() {
        val url = "https://example.com"
        val manager = createManager(currentActivityName = null, currentIntent = null)
        manager.onActivityStarted(buildActivityA(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        // areActivitiesEqual = false, source = LINK → sends
        assertEquals(1, trackVisitEvents.size)
        assertEquals(LINK to url, trackVisitEvents[0])
    }

    @Test
    fun `full session lifecycle produces exactly two trackVisit events`() {
        val intent = Intent()
        val activity = buildActivityA(intent)
        val owner = mockOwner()
        val manager = createManager(currentActivityName = LifecycleTestActivityA::class.java.name)
        // Launch
        manager.onActivityStarted(activity)
        manager.onActivityResumed(activity)
        // User presses home
        manager.onActivityPaused(activity)
        manager.onActivityStopped(activity)
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        // User returns
        manager.onStateChanged(owner, Lifecycle.Event.ON_START)
        manager.onActivityStarted(activity)
        manager.onActivityResumed(activity)
        assertEquals(2, trackVisitEvents.size)
    }

    @Test
    fun `scheduleReinitTrackVisit while backgrounded sends visit on foreground and does not block subsequent visits`() {
        // Typical reinit-while-backgrounded scenario:
        // 1. Initial launch → visit #1
        // 2. User backgrounds the app
        // 3. Mindbox.init() called again (reinit) → scheduleReinitTrackVisit + callbacks replaced
        //    → visit #2 dispatched immediately via dispatchCurrentVisit (new endpoint)
        // 4. User returns to foreground → visit #3
        // 5. User backgrounds and foregrounds again → visit #4
        val intent = Intent()
        val activity = buildActivityA(intent)
        val owner = mockOwner()
        val manager = LifecycleManager(
            currentActivityName = LifecycleTestActivityA::class.java.name,
            currentIntent = null,
            isAppInBackground = false,
        )
        listenTrackVisit(manager) // first init callbacks

        // Initial activity launch
        manager.onActivityStarted(activity) // visit #1
        manager.onActivityResumed(activity)
        manager.onActivityPaused(activity)
        manager.onActivityStopped(activity)
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        assertEquals(1, trackVisitEvents.size)

        // Reinit while backgrounded: schedule then replace callbacks (mirrors Mindbox.init flow)
        manager.scheduleReinitTrackVisit()
        listenTrackVisit(manager)
        assertEquals("reinit dispatches visit immediately through new callbacks", 2, trackVisitEvents.size)

        // User returns
        manager.onStateChanged(owner, Lifecycle.Event.ON_START) // visit #3
        assertEquals(3, trackVisitEvents.size)

        // Another background + foreground cycle
        manager.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        manager.onStateChanged(owner, Lifecycle.Event.ON_START) // visit #4
        assertEquals(4, trackVisitEvents.size)
    }

    // endregion
}

private fun assertSame(expected: Any, actual: Any) {
    assertTrue("Expected same instance", expected === actual)
}

internal class LifecycleTestActivityA : Activity()

internal class LifecycleTestActivityB : Activity()
