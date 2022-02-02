package cloud.mindbox.mindbox_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk_core.models.isUuid
import cloud.mindbox.mobile_sdk_core.MindboxInternalCore
import cloud.mindbox.mobile_sdk_core.clearPreferences
import cloud.mindbox.mobile_sdk_core.initCoreComponents
import cloud.mindbox.mobile_sdk_core.setDatabaseTestMode
import org.junit.After
import org.junit.Assert
import org.junit.Test

class FmsTokenSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        MindboxInternalCore.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        setDatabaseTestMode(true)
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        val subscribeId = MindboxInternalCore.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        MindboxInternalCore.disposeFmsTokenSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint")
            .build()

        setDatabaseTestMode(true)
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        MindboxInternalCore.disposeFmsTokenSubscription("wrong_subscribe")
        MindboxInternalCore.disposeFmsTokenSubscription("")

        setDatabaseTestMode(true)
        initCoreComponents()
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = MindboxInternalCore.subscribeFmsToken { }

        MindboxInternalCore.disposeFmsTokenSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
