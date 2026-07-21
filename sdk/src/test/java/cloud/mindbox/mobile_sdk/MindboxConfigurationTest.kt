package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class MindboxConfigurationTest {

    companion object {
        private const val DOMAIN = "api.mindbox.ru"
        private const val ENDPOINT = "test-endpoint"
        private const val VERSION_NAME = "1.2.3"
        private const val VERSION_CODE = 123
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val packageInfo = shadowOf(context.packageManager)
            .getInternalMutablePackageInfo(context.packageName)
        packageInfo.versionName = VERSION_NAME
        @Suppress("DEPRECATION")
        packageInfo.versionCode = VERSION_CODE
    }

    @Test
    fun `versionCode is read from PackageInfo by default`() {
        val configuration = MindboxConfiguration.Builder(context, DOMAIN, ENDPOINT).build()

        assertEquals(VERSION_NAME, configuration.versionName)
        assertEquals(VERSION_CODE.toString(), configuration.versionCode)
        assertTrue(configuration.shouldIncludeVersionCode)
    }

    @Test
    fun `versionCode is read from PackageInfo when shouldIncludeVersionCode is true`() {
        val configuration = MindboxConfiguration.Builder(context, DOMAIN, ENDPOINT)
            .shouldIncludeVersionCode(true)
            .build()

        assertEquals(VERSION_CODE.toString(), configuration.versionCode)
    }

    @Test
    fun `versionCode is empty when shouldIncludeVersionCode is false`() {
        val configuration = MindboxConfiguration.Builder(context, DOMAIN, ENDPOINT)
            .shouldIncludeVersionCode(false)
            .build()

        assertEquals(VERSION_NAME, configuration.versionName)
        assertEquals("", configuration.versionCode)
        assertFalse(configuration.shouldIncludeVersionCode)
    }

    @Test
    fun `versionCode is placeholder when app info is unavailable`() {
        val brokenContext = mockk<Context> {
            every { packageManager } throws RuntimeException("no package manager")
        }

        val configuration = MindboxConfiguration.Builder(brokenContext, DOMAIN, ENDPOINT).build()

        assertEquals("?", configuration.versionCode)
    }

    @Test
    fun `versionCode is empty when shouldIncludeVersionCode is false and app info is unavailable`() {
        val brokenContext = mockk<Context> {
            every { packageManager } throws RuntimeException("no package manager")
        }

        val configuration = MindboxConfiguration.Builder(brokenContext, DOMAIN, ENDPOINT)
            .shouldIncludeVersionCode(false)
            .build()

        assertEquals("", configuration.versionCode)
    }
}
