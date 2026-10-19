package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import org.junit.Test

class MindboxInitOverloadsTest {

    // Never called: the test fails at compile time if an integrator's call stops resolving.
    @Suppress("DEPRECATION", "unused")
    private fun integratorCalls(
        application: Application,
        activity: Activity,
        context: Context,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        Mindbox.init(application, configuration)
        Mindbox.init(application, configuration, pushServices)
        Mindbox.init(application, configuration, listOf())
        Mindbox.init(application = application, configuration = configuration, pushServices = pushServices)
        Mindbox.init(activity, configuration)
        Mindbox.init(activity, configuration, pushServices)
        Mindbox.init(activity = activity, configuration, listOf())
        Mindbox.init(context, configuration, pushServices)
        Mindbox.init(context.applicationContext, configuration, listOf())
    }

    @Test
    fun `integrator calls of init resolve to one overload`() {
        // The assertion is that this file compiles; see integratorCalls.
    }
}
