package cloud.mindbox.mindbox_sdk_starter_core

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.Mindbox
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MindboxCoreStarterTest {

    class MainActivity : Activity()

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        mockkObject(Mindbox)
        every {
            Mindbox.handleRemoteMessage(
                context = any(),
                message = any(),
                channelId = any(),
                channelName = any(),
                pushSmallIcon = any(),
                defaultActivity = any(),
                channelDescription = any(),
                activities = any(),
            )
        } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onMessageReceived passes the launcher activity as defaultActivity`() {
        declareLauncher(name = MainActivity::class.java.name, targetActivity = null)

        MindboxCoreStarter.onMessageReceived(application, Any())

        verifyDefaultActivity(MainActivity::class.java)
    }

    @Test
    fun `onMessageReceived passes the alias target as defaultActivity when the launcher is an activity-alias`() {
        declareLauncher(
            name = "${application.packageName}.MainActivityDefault",
            targetActivity = MainActivity::class.java.name,
        )

        MindboxCoreStarter.onMessageReceived(application, Any())

        verifyDefaultActivity(MainActivity::class.java)
    }

    private fun declareLauncher(name: String, targetActivity: String?) {
        val info = ActivityInfo().apply {
            packageName = application.packageName
            this.name = name
            this.targetActivity = targetActivity
            applicationInfo = application.applicationInfo
            exported = true
        }
        shadowOf(application.packageManager).apply {
            addOrUpdateActivity(info)
            addIntentFilterForActivity(
                ComponentName(application.packageName, name),
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    private fun verifyDefaultActivity(expected: Class<out Activity>) {
        verify(exactly = 1) {
            Mindbox.handleRemoteMessage(
                context = any(),
                message = any(),
                channelId = any(),
                channelName = any(),
                pushSmallIcon = any(),
                defaultActivity = expected,
                channelDescription = any(),
                activities = any(),
            )
        }
    }
}
