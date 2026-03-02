package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class WebViewLinkRouterTest {

    private lateinit var context: Context
    private lateinit var router: MindboxWebViewLinkRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        router = MindboxWebViewLinkRouter(context)
    }

    @Test
    fun `executeOpenLink opens web links from pdf cases`() {
        registerBrowsableHandler("https")
        val inputUrls: List<String> = listOf(
            "https://www.google.com",
            "https://habr.com/ru/articles/",
            "https://test-site.g.mindbox.ru",
            "https://test-site.g.mindbox.ru/some/path?param=1",
            "https://mindbox.ru",
            "https://mindbox.ru/products",
            "https://www.youtube.com/watch?v=abc",
            "https://t.me/durov",
        )
        inputUrls.forEach { inputUrl: String ->
            val actualResult: Result<String> = executeOpenLink(url = inputUrl)
            assertTrue(actualResult.isSuccess)
            assertEquals(inputUrl, actualResult.getOrNull())
        }
    }

    @Test
    fun `executeOpenLink opens deeplink schemes from pdf cases`() {
        registerBrowsableHandler("pushok")
        val inputUrls: List<String> = listOf(
            "pushok://",
            "pushok://product/123",
            "pushok://catalog?category=shoes&sort=price",
        )
        inputUrls.forEach { inputUrl: String ->
            val actualResult: Result<String> = executeOpenLink(url = inputUrl)
            assertTrue(actualResult.isSuccess)
            assertEquals(inputUrl, actualResult.getOrNull())
        }
    }

    @Test
    fun `executeOpenLink opens intent uri`() {
        registerBrowsableHandler("myapp")
        val intentUrl: String =
            "intent://catalog/item/1#Intent;scheme=myapp;S.browser_fallback_url=https%3A%2F%2Fmindbox.ru;end"
        val result: Result<String> = router.executeOpenLink("""{"url":"$intentUrl"}""")
        assertTrue(result.isSuccess)
        assertEquals(intentUrl, result.getOrNull())
    }

    @Test
    fun `executeOpenLink opens tg deeplink when handler exists`() {
        registerBrowsableHandler("tg")
        val inputUrl: String = "tg://resolve?domain=durov"
        val result: Result<String> = executeOpenLink(url = inputUrl)
        assertTrue(result.isSuccess)
        assertEquals(inputUrl, result.getOrNull())
    }

    @Test
    fun `executeOpenLink returns error for tg deeplink when handler missing`() {
        val activityNotFoundRouter: MindboxWebViewLinkRouter = createRouterWithActivityNotFoundError()
        val result: Result<String> = activityNotFoundRouter.executeOpenLink("""{"url":"tg://resolve?domain=durov"}""")
        assertFalse(result.isSuccess)
        assertErrorContains(result = result, expectedMessagePart = "ActivityNotFoundException")
    }

    @Test
    fun `executeOpenLink opens system schemes from pdf cases`() {
        registerActionHandler(action = Intent.ACTION_DIAL, scheme = "tel")
        registerActionHandler(action = Intent.ACTION_SENDTO, scheme = "mailto")
        registerActionHandler(action = Intent.ACTION_SENDTO, scheme = "sms")
        val inputUrls: List<String> = listOf(
            "tel:+1234567890",
            "mailto:test@example.com",
            "sms:+1234567890",
        )
        inputUrls.forEach { inputUrl: String ->
            val actualResult: Result<String> = executeOpenLink(url = inputUrl)
            assertTrue(actualResult.isSuccess)
            assertEquals(inputUrl, actualResult.getOrNull())
        }
    }

    @Test
    fun `executeOpenLink opens android only schemes when handler exists`() {
        registerBrowsableHandler("geo")
        registerBrowsableHandler("market")
        val geoResult: Result<String> = executeOpenLink(url = "geo:55.7558,37.6173?q=Moscow")
        assertTrue(geoResult.isSuccess)
        assertEquals("geo:55.7558,37.6173?q=Moscow", geoResult.getOrNull())
        val marketResult: Result<String> = executeOpenLink(url = "market://details?id=com.google.android.gm")
        assertTrue(marketResult.isSuccess)
        assertEquals("market://details?id=com.google.android.gm", marketResult.getOrNull())
    }

    @Test
    fun `executeOpenLink returns error for iOS only schemes without handler`() {
        val activityNotFoundRouter: MindboxWebViewLinkRouter = createRouterWithActivityNotFoundError()
        val mapsResult: Result<String> = activityNotFoundRouter.executeOpenLink("""{"url":"maps://?q=Moscow"}""")
        val appStoreResult: Result<String> =
            activityNotFoundRouter.executeOpenLink("""{"url":"itms-apps://apps.apple.com/app/id389801252"}""")
        assertFalse(mapsResult.isSuccess)
        assertFalse(appStoreResult.isSuccess)
        assertErrorContains(result = mapsResult, expectedMessagePart = "ActivityNotFoundException")
        assertErrorContains(result = appStoreResult, expectedMessagePart = "ActivityNotFoundException")
    }

    @Test
    fun `executeOpenLink returns error for blocked schemes from pdf cases`() {
        val blockedUrls: List<String> = listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,<h1>blocked</h1>",
            "blob:https://example.com/uuid",
        )
        blockedUrls.forEach { blockedUrl: String ->
            val actualResult: Result<String> = executeOpenLink(url = blockedUrl)
            assertFalse(actualResult.isSuccess)
            assertErrorContains(result = actualResult, expectedMessagePart = "Blocked URL scheme")
        }
    }

    @Test
    fun `executeOpenLink returns error for invalid or missing scheme urls`() {
        val invalidResult: Result<String> = executeOpenLink(url = "not a url at all")
        val missingSchemeResult: Result<String> = executeOpenLink(url = "://missing-scheme")
        assertFalse(invalidResult.isSuccess)
        assertFalse(missingSchemeResult.isSuccess)
        assertErrorContains(result = invalidResult, expectedMessagePart = "Invalid URL")
        assertErrorContains(result = missingSchemeResult, expectedMessagePart = "Invalid URL")
    }

    @Test
    fun `executeOpenLink returns error for unknown scheme without activity`() {
        val activityNotFoundRouter: MindboxWebViewLinkRouter = createRouterWithActivityNotFoundError()
        val result: Result<String> = activityNotFoundRouter.executeOpenLink("""{"url":"nonexistent-scheme://test"}""")
        assertFalse(result.isSuccess)
        assertErrorContains(result = result, expectedMessagePart = "ActivityNotFoundException")
    }

    @Test
    fun `executeOpenLink returns error for invalid payload cases from pdf`() {
        val nullPayloadResult: Result<String> = router.executeOpenLink(null)
        val emptyPayloadResult: Result<String> = router.executeOpenLink("")
        val blankPayloadResult: Result<String> = router.executeOpenLink("   ")
        val missingUrlResult: Result<String> = router.executeOpenLink("""{"foo":"bar"}""")
        val emptyUrlResult: Result<String> = router.executeOpenLink("""{"url":""}""")
        val invalidJsonResult: Result<String> = router.executeOpenLink("""{not-json}""")
        val notObjectJsonResult: Result<String> = router.executeOpenLink("""["https://mindbox.ru"]""")
        val payloadResults: List<Result<String>> = listOf(
            nullPayloadResult,
            emptyPayloadResult,
            blankPayloadResult,
            missingUrlResult,
            emptyUrlResult,
            invalidJsonResult,
            notObjectJsonResult,
        )
        payloadResults.forEach { actualResult: Result<String> ->
            assertFalse(actualResult.isSuccess)
            assertErrorContains(
                result = actualResult,
                expectedMessagePart = "Invalid payload: missing or empty 'url' field",
            )
        }
    }

    private fun executeOpenLink(url: String): Result<String> {
        return router.executeOpenLink("""{"url":"$url"}""")
    }

    private fun assertErrorContains(
        result: Result<String>,
        expectedMessagePart: String,
    ) {
        val actualError: Throwable? = result.exceptionOrNull()
        assertNotNull(actualError)
        val actualMessage: String = actualError?.message.orEmpty()
        assertTrue(actualMessage.contains(expectedMessagePart))
    }

    private fun createRouterWithActivityNotFoundError(): MindboxWebViewLinkRouter {
        val wrappedContext: Context = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("No activity found")
            }
        }
        return MindboxWebViewLinkRouter(wrappedContext)
    }

    private fun registerBrowsableHandler(scheme: String) {
        registerHandler(
            action = Intent.ACTION_VIEW,
            scheme = scheme,
            isBrowsable = true,
        )
    }

    private fun registerActionHandler(
        action: String,
        scheme: String,
    ) {
        registerHandler(
            action = action,
            scheme = scheme,
            isBrowsable = false,
        )
    }

    private fun registerHandler(
        action: String,
        scheme: String,
        isBrowsable: Boolean,
    ) {
        val componentName: ComponentName = ComponentName("com.example", "TestActivityFor_${action}_$scheme")
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        packageManager.addActivityIfNotPresent(componentName)
        packageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                if (isBrowsable) {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                addDataScheme(scheme)
            }
        )
    }
}
