package cloud.mindbox.mindbox_firebase

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseServiceHandlerTest {

    private val context = mockk<Context>()
    private val logger = mockk<MindboxLogger>(relaxed = true)

    // Real handler (not a mock): runCatching must execute the block so the
    // resource lookup actually runs.
    private val exceptionHandler = object : ExceptionHandler() {
        override fun handle(exception: Throwable) = Unit
    }
    private val handler = FirebaseServiceHandler(
        logger = logger,
        exceptionHandler = exceptionHandler,
    )

    @After
    fun tearDown() = unmockkAll()

    /**
     * Stubs the optional `mindbox_firebase_app_name` resource. The empty string is the
     * shipped default (host app didn't override it).
     */
    private fun stubAppName(value: String) {
        every { context.getString(R.string.mindbox_firebase_app_name) } returns value
    }

    private fun stubDefaultMessaging(token: String) {
        val defaultMessaging = mockk<FirebaseMessaging> {
            every { this@mockk.token } returns successTask(token)
        }
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns defaultMessaging
    }

    private fun successTask(token: String): Task<String> {
        val task = mockk<Task<String>>()
        every { task.addOnCanceledListener(any<OnCanceledListener>()) } returns task
        every { task.addOnSuccessListener(any<OnSuccessListener<String>>()) } answers {
            firstArg<OnSuccessListener<String>>().onSuccess(token)
            task
        }
        every { task.addOnFailureListener(any<OnFailureListener>()) } returns task
        return task
    }

    // --- Token resolution (resolveFirebaseMessaging via registerToken) ---

    @Test
    fun `resource blank (default) - token read from DEFAULT FirebaseApp (backward compatible)`() = runTest {
        stubAppName("")
        stubDefaultMessaging("default-token")

        val token = handler.registerToken(context, previousToken = null)

        assertEquals("default-token", token)
        verify(exactly = 1) { FirebaseMessaging.getInstance() }
    }

    @Test
    fun `resource whitespace-only - treated as blank, uses DEFAULT FirebaseApp`() = runTest {
        stubAppName("   ")
        stubDefaultMessaging("default-token")

        val token = handler.registerToken(context, previousToken = null)

        assertEquals("default-token", token)
        verify(exactly = 1) { FirebaseMessaging.getInstance() }
    }

    @Test
    fun `resource set and named app present - token read from named FirebaseApp`() = runTest {
        stubAppName("mindbox-named")
        val namedMessaging = mockk<FirebaseMessaging> {
            every { token } returns successTask("named-token")
        }
        val namedApp = mockk<FirebaseApp> {
            every { get(FirebaseMessaging::class.java) } returns namedMessaging
        }
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.getInstance("mindbox-named") } returns namedApp
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns mockk()

        val token = handler.registerToken(context, previousToken = null)

        assertEquals("named-token", token)
        verify(exactly = 1) { FirebaseApp.getInstance("mindbox-named") }
        // Isolation: the named path must never touch the [DEFAULT] instance.
        verify(exactly = 0) { FirebaseMessaging.getInstance() }
    }

    @Test
    fun `resource with surrounding whitespace - trimmed before use as named app`() = runTest {
        stubAppName("  mindbox-named  ")
        val namedMessaging = mockk<FirebaseMessaging> {
            every { token } returns successTask("named-token")
        }
        val namedApp = mockk<FirebaseApp> {
            every { get(FirebaseMessaging::class.java) } returns namedMessaging
        }
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.getInstance("mindbox-named") } returns namedApp
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns mockk()

        val token = handler.registerToken(context, previousToken = null)

        assertEquals("named-token", token)
        // Trimmed name is used, not the raw "  mindbox-named  ".
        verify(exactly = 1) { FirebaseApp.getInstance("mindbox-named") }
    }

    @Test
    fun `resource set but named app unresolvable - falls back to DEFAULT FirebaseApp`() = runTest {
        stubAppName("does-not-exist")
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.getInstance("does-not-exist") } throws
            IllegalStateException("FirebaseApp with name does-not-exist doesn't exist.")
        stubDefaultMessaging("default-token")

        val token = handler.registerToken(context, previousToken = null)

        assertEquals("default-token", token)
        verify(exactly = 1) { FirebaseMessaging.getInstance() }
        // A clear error naming the missing app is logged before the fallback.
        verify { logger.e(any(), match { it.contains("does-not-exist") }, any()) }
    }

    // --- initService: SDK creates the isolated named app ---

    @Test
    fun `initService creates named app from DEFAULT options when resource set and app absent`() = runTest {
        stubAppName("mindbox-named")
        val defaultOptions = mockk<FirebaseOptions>()
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(context) } returns mockk()
        every { FirebaseApp.getApps(context) } returns emptyList()
        every { FirebaseApp.getInstance() } returns mockk { every { options } returns defaultOptions }
        every { FirebaseApp.initializeApp(context, defaultOptions, "mindbox-named") } returns mockk()

        handler.initService(context)

        verify(exactly = 1) { FirebaseApp.initializeApp(context, defaultOptions, "mindbox-named") }
        // AC2: the configured app name is visible in the logs (logged once, at creation).
        verify { logger.i(any(), match { it.contains("mindbox-named") }) }
    }

    @Test
    fun `initService does not recreate named app when client already created it`() = runTest {
        stubAppName("mindbox-named")
        val existing = mockk<FirebaseApp> { every { name } returns "mindbox-named" }
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(context) } returns mockk()
        every { FirebaseApp.getApps(context) } returns listOf(existing)

        handler.initService(context)

        verify(exactly = 0) { FirebaseApp.initializeApp(context, any(), any<String>()) }
    }

    @Test
    fun `initService logs error and does not crash when named app creation fails`() = runTest {
        stubAppName("mindbox-named")
        val defaultOptions = mockk<FirebaseOptions>()
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(context) } returns mockk()
        every { FirebaseApp.getApps(context) } returns emptyList()
        every { FirebaseApp.getInstance() } returns mockk { every { options } returns defaultOptions }
        every { FirebaseApp.initializeApp(context, defaultOptions, "mindbox-named") } throws
            IllegalStateException("boom")

        handler.initService(context) // must not throw

        verify { logger.e(any(), match { it.contains("Failed to create") }, any()) }
    }

    @Test
    fun `initService does not create named app when resource is blank`() = runTest {
        stubAppName("")
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(context) } returns mockk()

        handler.initService(context)

        verify(exactly = 0) { FirebaseApp.initializeApp(context, any(), any<String>()) }
    }

    // --- End-to-end: initService creates the app, getToken then reads from it ---

    @Test
    fun `initService creates named app and a subsequent getToken reads its token`() = runTest {
        stubAppName("mindbox-named")
        val defaultOptions = mockk<FirebaseOptions>()
        val namedMessaging = mockk<FirebaseMessaging> {
            every { token } returns successTask("named-token")
        }
        val namedApp = mockk<FirebaseApp> {
            every { get(FirebaseMessaging::class.java) } returns namedMessaging
        }
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(context) } returns mockk()
        every { FirebaseApp.getApps(context) } returns emptyList()
        every { FirebaseApp.getInstance() } returns mockk { every { options } returns defaultOptions }
        every { FirebaseApp.initializeApp(context, defaultOptions, "mindbox-named") } returns namedApp
        every { FirebaseApp.getInstance("mindbox-named") } returns namedApp
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns mockk()

        handler.initService(context)
        val token = handler.registerToken(context, previousToken = null)

        assertEquals("named-token", token)
        verify(exactly = 1) { FirebaseApp.initializeApp(context, defaultOptions, "mindbox-named") }
        verify(exactly = 0) { FirebaseMessaging.getInstance() }
        // Resource read once across the whole init -> token sequence (memoization).
        verify(exactly = 1) { context.getString(R.string.mindbox_firebase_app_name) }
    }

    // --- Memoization: resource read once ---

    @Test
    fun `resource is read only once across multiple token requests - default path`() = runTest {
        stubAppName("")
        stubDefaultMessaging("default-token")

        handler.registerToken(context, previousToken = null)
        handler.registerToken(context, previousToken = null)

        verify(exactly = 1) { context.getString(R.string.mindbox_firebase_app_name) }
    }

    @Test
    fun `resource is read only once across multiple token requests - named path`() = runTest {
        stubAppName("mindbox-named")
        val namedApp = mockk<FirebaseApp> {
            every { get(FirebaseMessaging::class.java) } returns mockk<FirebaseMessaging> {
                every { token } returns successTask("named-token")
            }
        }
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.getInstance("mindbox-named") } returns namedApp
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns mockk()

        handler.registerToken(context, previousToken = null)
        handler.registerToken(context, previousToken = null)

        verify(exactly = 1) { context.getString(R.string.mindbox_firebase_app_name) }
    }
}
