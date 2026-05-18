package cloud.mindbox.mobile_sdk.managers

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.getCurrentProcessName
import cloud.mindbox.mobile_sdk.isMainProcess
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

    @Test
    fun `isAppInBackground is true when ProcessLifecycleOwner is below STARTED`() {
        every { any<Context>().getCurrentProcessName() } returns context.packageName
        every { any<Context>().isMainProcess(any()) } returns true

        // At test time ProcessLifecycleOwner has not been started by any Activity
        val stateBefore = ProcessLifecycleOwner.get().lifecycle.currentState
        val expectedBackground = !stateBefore.isAtLeast(Lifecycle.State.STARTED)

        MindboxLifecycleInitializer().create(context)

        // Verify by attempting a foreground track visit: if manager was created with
        // isAppInBackground=true, onActivityStarted will just clear the flag, not send a visit
        val trackVisitEvents = mutableListOf<Pair<String?, String?>>()
        val manager = LifecycleManager.instance!!
        manager.callbacks = object : LifecycleManager.Callbacks {
            override fun onTrackVisitReady(source: String?, requestUrl: String?) {
                trackVisitEvents.add(source to requestUrl)
            }
        }

        manager.onActivityStarted(mockk(relaxed = true))

        if (expectedBackground) {
            assertTrue(
                "Track visit must not be sent in first onActivityStarted when isAppInBackground was true",
                trackVisitEvents.isEmpty(),
            )
        }
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
