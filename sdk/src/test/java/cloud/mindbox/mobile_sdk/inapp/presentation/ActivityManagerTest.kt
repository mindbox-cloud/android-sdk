package cloud.mindbox.mobile_sdk.inapp.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class ActivityManagerTest {

    private lateinit var context: Context

    private val callbackInteractor: CallbackInteractor = mockk()

    private lateinit var activityManager: ActivityManagerImpl

    @Test
    fun `tryOpenUrl should open valid URL and return true`() {
        context = ApplicationProvider.getApplicationContext()
        activityManager = ActivityManagerImpl(
            callbackInteractor = callbackInteractor,
            context = context
        )
        val url = "https://mindbox.ru"
        every {
            callbackInteractor.isValidUrl(url)
        } returns true
        assertTrue(activityManager.tryOpenUrl(url))
    }

    @Test
    fun `tryOpenUrl should not open invalid URL and return false`() {
        // Arrange
        val url = "https://example.com"
        context = mockk()
        activityManager = ActivityManagerImpl(
            callbackInteractor = callbackInteractor,
            context = context
        )
        every { callbackInteractor.isValidUrl(url) } returns false

        // Act
        val result = activityManager.tryOpenUrl(url)

        // Assert
        assert(!result)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `tryOpenUrl should return false on exception`() {
        // Arrange
        val url = "https://example.com"
        context = mockk()
        activityManager = ActivityManagerImpl(
            callbackInteractor = callbackInteractor,
            context = context
        )
        every { callbackInteractor.isValidUrl(url) } throws Exception()

        // Act
        val result = activityManager.tryOpenUrl(url)

        // Assert
        assert(!result)
    }

    @Test
    fun `try open url doesn't open deeplink`() {
        context = ApplicationProvider.getApplicationContext()
        activityManager = ActivityManagerImpl(callbackInteractor, context)
        val url = "https://pushok-mindbox.onelink.me/13Z2/a97bb56f"
        val componentName = "testComponentName"
        val packageName = "com.example"
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        packageManager.addActivityIfNotPresent(ComponentName(packageName, componentName))
        packageManager.addIntentFilterForActivity(
            ComponentName(
                packageName,
                componentName
            ),
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            }
        )
        every {
            callbackInteractor.isValidUrl(url)
        } returns true
        assertFalse(activityManager.tryOpenUrl(url))
    }

    @Test
    fun `test tryOpenDeepLink with valid deep link`() {
        // Arrange
        context = ApplicationProvider.getApplicationContext()
        activityManager = ActivityManagerImpl(
            callbackInteractor = callbackInteractor,
            context = context
        )
        val deepLink = "app://example.com"
        val primitiveComponentName = "testComponentName"
        val packageName = "com.example"
        context = ApplicationProvider.getApplicationContext()
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        val componentName = ComponentName(packageName, primitiveComponentName)
        packageManager.addActivityIfNotPresent(componentName)
        packageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("app")
            }
        )
        assertTrue(activityManager.tryOpenDeepLink(deepLink))
    }

    @Test
    fun `test tryOpenDeepLink with invalid deep link`() {
        // Arrange
        context = ApplicationProvider.getApplicationContext()
        activityManager = ActivityManagerImpl(
            callbackInteractor = callbackInteractor,
            context = context
        )
        val deepLink = "ap2p://example.c3om"
        val componentName = "testComponentName"
        val packageName = "com.example"
        context = ApplicationProvider.getApplicationContext()
        val packageManager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        packageManager.addActivityIfNotPresent(ComponentName(packageName, componentName))
        packageManager.addIntentFilterForActivity(
            ComponentName(
                packageName,
                componentName
            ),
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("app")
            }
        )
        assertFalse(activityManager.tryOpenDeepLink(deepLink))
    }
}
