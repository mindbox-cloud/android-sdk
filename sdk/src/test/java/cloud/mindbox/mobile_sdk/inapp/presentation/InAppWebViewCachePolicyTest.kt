package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class InAppWebViewCachePolicyTest {

    private val serializationManager = MobileConfigSerializationManagerImpl(gson = Gson())

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
    }

    @After
    fun tearDown() {
        unmockkObject(MindboxPreferences)
    }

    @Test
    fun `isCacheEnabled is false when the cached config disables the toggle`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = false)

        val policy = InAppWebViewCachePolicy(serializationManager)

        assertEquals(false, policy.isCacheEnabled)
    }

    @Test
    fun `isCacheEnabled is true when the cached config explicitly enables the toggle`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = true)

        val policy = InAppWebViewCachePolicy(serializationManager)

        assertEquals(true, policy.isCacheEnabled)
    }

    @Test
    fun `isCacheEnabled defaults to true when the cached config has no toggle`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = null)

        val policy = InAppWebViewCachePolicy(serializationManager)

        assertEquals(true, policy.isCacheEnabled)
    }

    @Test
    fun `isCacheEnabled defaults to true when there is no cached config`() {
        every { MindboxPreferences.inAppConfig } returns ""

        val policy = InAppWebViewCachePolicy(serializationManager)

        assertEquals(true, policy.isCacheEnabled)
    }

    @Test
    fun `isCacheEnabled is latched for the process and ignores a later cached config change`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = false)
        val policy = InAppWebViewCachePolicy(serializationManager)
        assertEquals(false, policy.isCacheEnabled)

        // A fresh config lands and flips the cached toggle mid-session — the already
        // latched decision must not change, or the WebView cache would be split between
        // two behaviors within the same launch.
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = true)

        assertEquals(false, policy.isCacheEnabled)
    }

    @Test
    fun `prime latches the toggle from an already-parsed config blank without touching the cached config`() {
        val configBlank = serializationManager.deserializeToConfigDtoBlank(cachedConfigJson(cacheToggle = false))
        val policy = InAppWebViewCachePolicy(serializationManager)

        // MindboxPreferences.inAppConfig is deliberately left unstubbed: if isCacheEnabled
        // fell back to parsing the cache itself instead of using the primed value, this
        // would throw on the unstubbed mock.
        policy.prime(configBlank)

        assertEquals(false, policy.isCacheEnabled)
    }

    @Test
    fun `prime is a no-op once isCacheEnabled has already latched a value`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson(cacheToggle = true)
        val policy = InAppWebViewCachePolicy(serializationManager)
        assertEquals(true, policy.isCacheEnabled)

        val disabledBlank = serializationManager.deserializeToConfigDtoBlank(cachedConfigJson(cacheToggle = false))
        policy.prime(disabledBlank)

        assertEquals(true, policy.isCacheEnabled)
    }

    @Test
    fun `prime keeps the first primed value on a later prime call`() {
        val policy = InAppWebViewCachePolicy(serializationManager)
        val enabledBlank = serializationManager.deserializeToConfigDtoBlank(cachedConfigJson(cacheToggle = true))
        val disabledBlank = serializationManager.deserializeToConfigDtoBlank(cachedConfigJson(cacheToggle = false))

        policy.prime(enabledBlank)
        policy.prime(disabledBlank)

        assertEquals(true, policy.isCacheEnabled)
    }

    private fun cachedConfigJson(cacheToggle: Boolean?): String {
        val toggle = cacheToggle?.let { "\"MobileSdkShouldCacheInAppWebView\": $it" }.orEmpty()
        return """
        {
          "settings": {
            "featureToggles": { $toggle }
          }
        }
            """.trimIndent()
    }
}
