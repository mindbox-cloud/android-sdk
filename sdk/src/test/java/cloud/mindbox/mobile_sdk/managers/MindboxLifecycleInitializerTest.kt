package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.getCurrentProcessName
import cloud.mindbox.mobile_sdk.isMainProcess
import cloud.mindbox.mobile_sdk.models.LINK
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class MindboxLifecycleInitializerTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic("cloud.mindbox.mobile_sdk.ExtensionsKt")
    }

    @After
    fun tearDown() {
        LifecycleManager.instance = null
        unmockkAll()
    }

    @Test
    fun `instance is null before create is called`() {
        assertNull(LifecycleManager.instance)
    }

    @Test
    fun `creates and stores LifecycleManager instance in main process`() {
        every { any<Context>().getCurrentProcessName() } returns context.packageName
        every { any<Context>().isMainProcess(context.packageName) } returns true

        MindboxLifecycleInitializer().create(context)

        assertNotNull(LifecycleManager.instance)
    }

    @Test
    fun `skips registration in non-main process`() {
        val nonMainProcessName = "${context.packageName}:push"
        every { any<Context>().getCurrentProcessName() } returns nonMainProcessName
        every { any<Context>().isMainProcess(nonMainProcessName) } returns false

        MindboxLifecycleInitializer().create(context)

        assertNull(
            "LifecycleManager must not be created outside the main process",
            LifecycleManager.instance,
        )
    }

    @Test
    fun `calling create twice creates a new instance each time`() {
        every { any<Context>().getCurrentProcessName() } returns context.packageName
        every { any<Context>().isMainProcess(any()) } returns true

        MindboxLifecycleInitializer().create(context)
        val firstInstance = LifecycleManager.instance

        MindboxLifecycleInitializer().create(context)

        assertNotNull(LifecycleManager.instance)
        assertNotSame(
            "second create must produce a new LifecycleManager instance",
            firstInstance,
            LifecycleManager.instance,
        )
    }

    /**
     * Robolectric never advances [ProcessLifecycleOwner] to STARTED, so the initializer must
     * create the manager with `isAppInBackground = true`.
     *
     * We verify the flag is honoured by supplying an https deep-link intent (source = LINK).
     * The LINK source bypasses the `!sameActivity && source == DIRECT` early-return guard inside
     * [LifecycleManager.sendTrackVisit], so the only thing that can suppress the visit is
     * the `isAppInBackground` flag itself — making the assertion unambiguous.
     */
    @Test
    fun `isAppInBackground true - suppresses track visit on first onActivityStarted`() {
        every { any<Context>().getCurrentProcessName() } returns context.packageName
        every { any<Context>().isMainProcess(any()) } returns true

        // Validate Robolectric environment assumption before trusting the assertion below.
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        assertFalse(
            "Test requires ProcessLifecycleOwner below STARTED; got $state",
            state.isAtLeast(Lifecycle.State.STARTED),
        )

        MindboxLifecycleInitializer().create(context)

        val manager = checkNotNull(LifecycleManager.instance)
        val events = mutableListOf<Pair<String?, String?>>()
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onTrackVisitReady(source: String?, requestUrl: String?) {
                events.add(source to requestUrl)
            }
        }

        // https intent → source = LINK, which bypasses the `!sameActivity && DIRECT` guard, so
        // a visit would be dispatched if isAppInBackground were false.
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/promo"))
        val activity = mockk<Activity>(relaxed = true) {
            every { this@mockk.intent } returns deepLinkIntent
        }

        manager.onActivityStarted(activity)

        assertTrue(
            "Track visit must NOT be dispatched on first onActivityStarted when the manager " +
                "was created while the app was in the background (isAppInBackground = true)",
            events.isEmpty(),
        )
    }

    /**
     * Counterpart to [isAppInBackground true - suppresses track visit on first onActivityStarted]:
     * when the manager starts with `isAppInBackground = false` (app already foregrounded),
     * [LifecycleManager.onActivityStarted] must dispatch a track-visit with source LINK and
     * the correct URL for an https deep-link intent.
     *
     * We construct the manager directly to avoid depending on Robolectric's lifecycle state.
     */
    @Test
    fun `isAppInBackground false - dispatches LINK track visit on onActivityStarted with https intent`() {
        val deepLinkUrl = "https://example.com/promo"
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))

        val manager = LifecycleManager(
            currentActivityName = null,
            currentIntent = null,
            isAppInBackground = false,
        )
        val events = mutableListOf<Pair<String?, String?>>()
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onTrackVisitReady(source: String?, requestUrl: String?) {
                events.add(source to requestUrl)
            }
        }
        val activity = mockk<Activity>(relaxed = true) {
            every { this@mockk.intent } returns deepLinkIntent
        }

        manager.onActivityStarted(activity)

        assertEquals("Exactly one track visit must be dispatched", 1, events.size)
        assertEquals("Source must be LINK for an https intent", LINK, events[0].first)
        assertEquals("URL must equal the deep-link URI", deepLinkUrl, events[0].second)
    }

    @Test
    fun `non-main process skips creation regardless of process name format`() {
        val processNames = listOf(
            "${context.packageName}:firebase",
            "${context.packageName}:push",
            "com.yandex.metrica",
            ":remote",
        )

        processNames.forEach { name ->
            LifecycleManager.instance = null
            every { any<Context>().getCurrentProcessName() } returns name
            every { any<Context>().isMainProcess(name) } returns false

            MindboxLifecycleInitializer().create(context)

            assertNull("Process '$name' must not create a LifecycleManager", LifecycleManager.instance)
        }
    }
}
