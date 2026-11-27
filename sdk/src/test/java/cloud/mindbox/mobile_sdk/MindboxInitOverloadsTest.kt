package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import org.junit.Test

class MindboxInitOverloadsTest {

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
        Mindbox.initPushServices(application)
        Mindbox.initPushServices(application, pushServices)
        Mindbox.initPushServices(context = context, pushServices = listOf())
        Mindbox.initPushServices(context, null)
    }

    @Test
    fun `integrator calls of init and initPushServices resolve to one overload at compile time`(): Unit = Unit
}
